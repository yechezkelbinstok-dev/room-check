package com.roomcheck.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

enum class RoomMode { ONE, SCROLL }
enum class Tab { CHECK, NAMES, SETTINGS }

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
    private val undoStack = ArrayDeque<Night>()
    private val syncClient = SyncClient(store)
    private var syncing = false

    private fun deepCopy(n: Night): Night = Night.fromJson(n.toJson())

    private val initialKey = Dates.tonightKey()
    private val _state = MutableStateFlow(
        UiState(
            dateKey = initialKey,
            night = loadAndMaybeClose(initialKey),
            extra = store.extra,
            settings = store.settings,
            curSlot = defaultSlot()
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun logic(s: UiState = _state.value) = NightLogic(s.night, s.extra)

    private fun defaultSlot(): String {
        val now = LocalDateTime.now()
        val mins = now.hour * 60 + now.minute
        return when {
            mins >= 23 * 60 + 55 || mins < 6 * 60 -> "1200"
            mins >= 23 * 60 + 25 -> "1130"
            else -> "1115"
        }
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
        return Roster.SIDS.any { s.night.marks[it]?.isNotEmpty() == true }
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

    fun closeNight() = update { s ->
        s.night.closed = true
        s.night.touch(Merge.CLOSED_KEY)
        persist(s); s.copy(editing = false, reviewing = false)
    }

    fun goToDate(key: String) {
        undoStack.clear()
        update { s -> s.copy(dateKey = key, night = loadAndMaybeClose(key), editing = false, reviewing = false) }
    }
    fun goToday() = goToDate(Dates.tonightKey())
    fun shiftDay(delta: Long) = goToDate(Dates.shiftKey(_state.value.dateKey, delta))

    fun toggleExcusedTonight(pid: String) {
        snap()
        update { s ->
            if (s.night.excusedTonight.contains(pid)) s.night.excusedTonight.remove(pid)
            else {
                s.night.excusedTonight.add(pid)
                Roster.SIDS.forEach { s.night.marks[it]?.remove(pid); s.night.touch(Merge.markKey(it, pid)) }
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
        update { it.copy(sync = SyncState.Working) }
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
