package com.roomcheck.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

enum class RoomMode { ONE, SCROLL }
enum class Tab { CHECK, NAMES, SETTINGS, TIMES }

data class UiState(
    val dateKey: String,
    val night: Night,
    val extra: Map<String, PersonOverride>,
    val settings: Settings,
    val curSlot: String,
    val room: Int = 0,
    val mode: RoomMode = RoomMode.ONE,
    val tab: Tab = Tab.CHECK,
    val reviewing: Boolean = false,
    val editing: Boolean = false,
    val onlyOut: Boolean = false,
    val rev: Int = 0,
    val sync: SyncState = SyncState.Off,
    val toast: String? = null
)

class AppViewModel(private val store: NightStore) : ViewModel() {
    companion object {
        /** How often to pull while the app is actually on screen. Never runs in the background. */
        const val FOREGROUND_SYNC_MS = 30_000L
    }

    private val undoStack = ArrayDeque<Night>()
    private val syncClient = SyncClient(store)
    private var syncing = false

    private fun deepCopy(n: Night): Night = Night.fromJson(n.toJson())

    private val initialKey = Dates.tonightKey()
    private val initialNight = loadAndMaybeClose(initialKey)
    private val _state = MutableStateFlow(
        UiState(
            dateKey = initialKey,
            night = initialNight,
            extra = store.extra,
            settings = store.settings,
            curSlot = defaultSlot(initialNight)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun logic(s: UiState = _state.value) = NightLogic(s.night, s.extra, Slots.all(s.night))

    /** Whichever of tonight's rounds is nearest behind the clock right now, so opening lands on it. */
    private fun defaultSlot(night: Night): String {
        val slots = Slots.all(night)
        val now = LocalDateTime.now()
        val mins = Slots.order(Slots.idForClock(now.hour, now.minute))
        return (slots.lastOrNull { Slots.order(it.id) <= mins } ?: slots.first()).id
    }

    private fun loadAndMaybeClose(key: String): Night {
        val n = store.load(key)
        if (key != Dates.tonightKey() && !n.closed) {
            n.closed = true
            n.touch(Merge.CLOSED_KEY)
            store.save(key, n)
        }
        return n
    }

    // Night/marks/notes/etc. are mutated in place for simplicity, so the resulting UiState can be
    // structurally `equal` (even reference-identical) to the previous one. MutableStateFlow drops
    // an assignment that's `equal` to its current value, which would silently swallow every mark/
    // undo/room-toggle. Bumping `rev` on every update guarantees StateFlow always sees a change.
    private fun update(f: (UiState) -> UiState) {
        val next = f(_state.value)
        _state.value = next.copy(rev = next.rev + 1)
    }

    private fun snap() { undoStack.addLast(deepCopy(_state.value.night)); if (undoStack.size > 40) undoStack.removeFirst() }

    private var pushJob: kotlinx.coroutines.Job? = null

    private fun persist(s: UiState) {
        store.saveLocal(s.dateKey, s.night)
        // Marking a room is a burst of taps. Wait for it to settle rather than firing a request
        // per tap; the marks are already safely on disk, this is only about sharing them.
        if (store.settings.syncUrl.isNotBlank()) {
            pushJob?.cancel()
            pushJob = viewModelScope.launch {
                kotlinx.coroutines.delay(4000)
                syncNow()
            }
        }
    }

    fun isLocked(s: UiState = _state.value): Boolean {
        if (s.editing) return false
        if (s.night.closed) return true
        if (s.dateKey == Dates.tonightKey()) return false
        return Slots.all(s.night).any { s.night.marks[it.id]?.isNotEmpty() == true }
    }
    fun showingReview(s: UiState = _state.value): Boolean = isLocked(s) || s.reviewing

    fun setMark(pid: String, sid: String, value: Mark) {
        snap()
        update { s ->
            val marks = s.night.marks[sid] ?: mutableMapOf()
            if (marks[pid] == value) marks.remove(pid) else marks[pid] = value
            s.night.marks[sid] = marks
            s.night.touch(Merge.markKey(sid, pid))
            persist(s); s
        }
    }

    fun markRoom(room: Room, sid: String) {
        snap()
        update { s ->
            val marks = s.night.marks[sid] ?: mutableMapOf()
            val all = logic(s).roomAllIn(room, sid)
            room.beds.flatMap { it.slots }.forEach { pid ->
                if (logic(s).statusOf(pid, sid) == Mark.EXC) return@forEach
                if (all) marks.remove(pid) else marks[pid] = Mark.IN
                s.night.touch(Merge.markKey(sid, pid))
            }
            s.night.marks[sid] = marks
            persist(s); s
        }
    }

    fun undo() {
        val prev = if (undoStack.isNotEmpty()) undoStack.removeLast() else null
        if (prev == null) { toast("Nothing to undo"); return }
        update { s -> persist(s.copy(night = prev)); s.copy(night = prev) }
        toast("Undone")
    }

    fun selectSlot(sid: String) = update { it.copy(curSlot = sid) }
    fun goRoom(delta: Int) = update { s -> val i = (s.room + delta).coerceIn(0, Roster.PLAN.size - 1); s.copy(room = i) }
    fun jumpRoom(i: Int) = update { it.copy(room = i.coerceIn(0, Roster.PLAN.size - 1)) }
    fun setMode(m: RoomMode) = update { it.copy(mode = m) }
    fun setTab(t: Tab) = update { it.copy(tab = t) }
    fun setReview(v: Boolean) = update { it.copy(reviewing = v) }
    fun setOnlyOut(v: Boolean) = update { it.copy(onlyOut = v) }
    fun setEditing(v: Boolean) = update { it.copy(editing = v, reviewing = false) }

    fun setSettings(f: (Settings) -> Settings) = update { s ->
        store.settings = f(store.settings)
        store.saveState()
        s.copy(settings = store.settings)
    }

    /**
     * Sets the rounds walked on THIS night, replacing whatever it had.
     *
     * The list lives on the night beside its marks, so it syncs to the other devices and is still
     * there when the night is opened again next week - and tomorrow starts from the standing three
     * with nothing to put back. Marks in a round that is being dropped go with it; they are marks
     * from a walk that, as far as this night is now concerned, did not happen. Undo puts them back.
     */
    fun setRoundsTonight(ids: List<String>) {
        val want = ids.filter { Slots.isValidId(it) }.distinct().sortedBy { Slots.order(it) }
        if (want.isEmpty()) { toast("A night needs at least one round"); return }
        snap()
        update { s ->
            val gone = Slots.all(s.night).map { it.id } - want.toSet()
            gone.forEach { sid ->
                s.night.marks[sid]?.keys?.toList()?.forEach { pid ->
                    s.night.marks[sid]?.remove(pid); s.night.touch(Merge.markKey(sid, pid))
                }
                s.night.marks.remove(sid)
                if (s.night.sheetSlots.remove(sid)) s.night.touch(Merge.sheetKey(sid))
            }
            // every round is its own cell, so two devices editing the night's rounds at the same
            // time merge the same way its marks do rather than one list flattening the other
            (s.night.rounds.toList() + want).distinct().forEach { sid ->
                if (sid in want) s.night.rounds.add(sid) else s.night.rounds.remove(sid)
                s.night.touch(Merge.slotKey(sid))
            }
            persist(s)
            // standing on a round that is gone would leave the screen marking into nothing
            s.copy(curSlot = if (s.curSlot in want) s.curSlot else want.first())
        }
    }

    /** Puts the night back on the usual three. */
    fun resetRoundsTonight() {
        snap()
        update { s ->
            s.night.rounds.toList().forEach { s.night.rounds.remove(it); s.night.touch(Merge.slotKey(it)) }
            s.night.sheetSlots.toList().forEach { s.night.sheetSlots.remove(it); s.night.touch(Merge.sheetKey(it)) }
            persist(s)
            val slots = Slots.all(s.night)
            s.copy(curSlot = if (slots.any { it.id == s.curSlot }) s.curSlot else slots.first().id)
        }
    }

    fun addRoundTonight(id: String) {
        if (!Slots.isValidId(id)) { toast("Not a time"); return }
        val now = Slots.all(_state.value.night).map { it.id }
        if (id in now) { toast("Already on this night"); return }
        setRoundsTonight(now + id)
        update { it.copy(curSlot = id) }
    }

    /** The one-off in one step: this night had a single check, at this time. */
    fun onlyRoundTonight(id: String) {
        if (!Slots.isValidId(id)) { toast("Not a time"); return }
        setRoundsTonight(listOf(id))
    }

    fun removeRoundTonight(id: String) =
        setRoundsTonight(Slots.all(_state.value.night).map { it.id } - id)

    /** Puts a round on this night's picture, or takes it off. All of them, unless you say otherwise. */
    fun toggleSheetSlot(id: String) {
        update { s ->
            val every = Slots.all(s.night).map { it.id }
            // "all of them" is stored as nothing at all, so the first tap has to write out the
            // list it is turning one off from
            if (s.night.sheetSlots.isEmpty()) {
                every.forEach { s.night.sheetSlots.add(it); s.night.touch(Merge.sheetKey(it)) }
            }
            if (s.night.sheetSlots.contains(id)) s.night.sheetSlots.remove(id) else s.night.sheetSlots.add(id)
            s.night.touch(Merge.sheetKey(id))
            // back to everything ticked means back to no opinion, so a round added later joins in
            if (s.night.sheetSlots.containsAll(every) && s.night.sheetSlots.size == every.size) {
                every.forEach { s.night.sheetSlots.remove(it); s.night.touch(Merge.sheetKey(it)) }
            }
            persist(s); s
        }
    }

    fun closeNight() = update { s ->
        s.night.closed = true
        s.night.touch(Merge.CLOSED_KEY)
        persist(s); s.copy(editing = false, reviewing = false)
    }

    fun goToDate(key: String) {
        undoStack.clear()
        update { s ->
            val night = loadAndMaybeClose(key)
            val slots = Slots.all(night)
            s.copy(
                dateKey = key, night = night, editing = false, reviewing = false,
                // rounds belong to the night, so another night may not have the one you were on
                curSlot = if (slots.any { it.id == s.curSlot }) s.curSlot else slots.first().id
            )
        }
    }
    fun goToday() = goToDate(Dates.tonightKey())
    fun shiftDay(delta: Long) = goToDate(Dates.shiftKey(_state.value.dateKey, delta))

    fun toggleExcusedTonight(pid: String) {
        snap()
        update { s ->
            if (s.night.excusedTonight.contains(pid)) s.night.excusedTonight.remove(pid)
            else {
                s.night.excusedTonight.add(pid)
                Slots.all(s.night).forEach { s.night.marks[it.id]?.remove(pid); s.night.touch(Merge.markKey(it.id, pid)) }
            }
            s.night.touch(Merge.tonightKey(pid))
            persist(s); s
        }
    }

    fun toggleAlwaysExcused(pid: String) {
        val ov = store.extra.getOrPut(pid) { PersonOverride() }
        ov.always = !ov.always
        store.saveState()
        update { it.copy(extra = store.extra.toMap()) }
        toast(if (ov.always) "Off the reports until you turn him back on" else "Back on the sheet")
    }

    fun unexcuse(pid: String) {
        store.extra[pid]?.always = false
        store.saveState()
        update { it.copy(extra = store.extra.toMap()) }
    }

    fun setNote(pid: String, note: String) {
        update { s ->
            if (note.isBlank()) s.night.notes.remove(pid) else s.night.notes[pid] = note
            s.night.touch(Merge.noteKey(pid))
            persist(s); s
        }
    }

    fun setReason(pid: String, reason: String) {
        val ov = store.extra.getOrPut(pid) { PersonOverride() }
        ov.reason = reason.ifBlank { null }
        store.saveState()
        update { it.copy(extra = store.extra.toMap()) }
    }

    fun renamePerson(pid: String, first: String, last: String) {
        val ov = store.extra.getOrPut(pid) { PersonOverride() }
        ov.first = first.ifBlank { null }
        ov.last = last.ifBlank { null }
        store.saveState()
        update { it.copy(extra = store.extra.toMap()) }
    }

    /**
     * Runs a sync if one is configured. Silent by default: it happens on opening the app and after
     * a change, and a failure there is not worth a popup - the marks are safe on this device
     * either way. [loud] is for the Sync now row, where you asked and want to be told.
     */
    fun syncNow(loud: Boolean = false) {
        if (store.settings.syncUrl.isBlank()) { if (loud) toast("No sync address set"); return }
        if (syncing) return
        syncing = true
        // The quiet poll runs every 30s; flashing "Syncing…" through the settings line each time
        // would be movement with nothing behind it. Only the row you pressed says it is working.
        if (loud) update { it.copy(sync = SyncState.Working) }
        viewModelScope.launch {
            val result = runCatching { syncClient.sync() }
            syncing = false
            result.onSuccess { changed ->
                update { s ->
                    s.copy(
                        sync = SyncState.Ok(System.currentTimeMillis(), changed),
                        // a night that changed under us has to be re-read, or the screen keeps
                        // showing what it had before the other device's marks arrived
                        night = if (changed > 0) store.load(s.dateKey) else s.night
                    )
                }
                if (loud) toast(if (changed > 0) "Synced · $changed night${if (changed == 1) "" else "s"} updated" else "Synced")
            }.onFailure { e ->
                update { it.copy(sync = SyncState.Failed(e.message ?: "Could not reach the server")) }
                if (loud) toast(e.message ?: "Could not sync")
            }
        }
    }

    fun exportBackup(): String = store.exportBackup()
    fun importBackup(text: String): Boolean {
        val ok = store.importBackup(text)
        if (ok) update { s -> s.copy(extra = store.extra.toMap(), night = loadAndMaybeClose(s.dateKey)) }
        return ok
    }

    fun toast(msg: String) { update { it.copy(toast = msg) } }
    fun clearToast() { update { it.copy(toast = null) } }

    fun calendarKeysWithData(): Set<String> = store.allDateKeys()
}
