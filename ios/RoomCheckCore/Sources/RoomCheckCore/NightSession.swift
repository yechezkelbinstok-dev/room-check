import Foundation

/// The night the app has open, and every edit to it - the iPhone's side of the Android app's
/// AppViewModel, with nothing in it that needs a screen, so all of it is tested here on any
/// machine Swift runs on. The app's screens read from it and call into it; [onChange] tells them
/// to draw again, and [onToast] passes on the short message the bottom of the screen flashes.
///
/// [clock] is the time taken as now - which night is tonight, and which round to open on. The
/// real one but in tests, which pin it, so a test of tonight's rounds does not turn into a test
/// of whether it happens to be Thursday.
public final class NightSession {
    public let store: NightStore
    private let clock: () -> Date

    public private(set) var dateKey: String
    public private(set) var night: Night
    public private(set) var curSlot: String
    /// The room on screen, as an index into Roster.PLAN.
    public private(set) var room = 0
    public private(set) var editing = false
    public private(set) var reviewing = false
    /// Who search just jumped to, ringed on the plan until it clears itself.
    public private(set) var highlight: String?
    private var undoStack: [Night] = []

    public var onChange: (() -> Void)?
    public var onToast: ((String) -> Void)?

    public init(store: NightStore, clock: @escaping () -> Date = Date.init) {
        self.store = store
        self.clock = clock
        let key = Dates.tonightKey(now: clock())
        dateKey = key
        night = Night()
        curSlot = ""
        night = loadAndMaybeClose(key)
        curSlot = defaultSlot()
    }

    // MARK: - reading

    /// Tonight's key by [clock]: the evening's date, still the same night until 6 in the morning.
    public func tonight() -> String { Dates.tonightKey(now: clock()) }

    /// The rounds of the night on screen - which depend on its date as well as on the night itself.
    public var rounds: [Slot] { Slots.all(night, dateKey: dateKey) }

    public var logic: NightLogic { NightLogic(night, store.extra, rounds) }

    /// A past night with marks in it opens for reading, not marking, until you say you are editing.
    public var isLocked: Bool {
        if editing { return false }
        if night.closed { return true }
        if dateKey == tonight() { return false }
        return rounds.contains { !(night.marks[$0.id] ?? [:]).isEmpty }
    }
    public var showingReview: Bool { isLocked || reviewing }

    // MARK: - marking

    public func setMark(_ pid: String, _ sid: String, _ value: Mark) {
        snap()
        var marks = night.marks[sid] ?? [:]
        if marks[pid] == value { marks.removeValue(forKey: pid) } else { marks[pid] = value }
        night.marks[sid] = marks
        night.touch(Merge.markKey(sid, pid))
        persist()
    }

    /// Every bed in the room still on the walk set to [value], or cleared when it is nil. With
    /// [only], that one person gets [onlyMark] and the rest of the room gets the other of in and
    /// out - the room with one boy in bed, or the room with one boy not in it.
    public func setRoom(_ room: Room, _ sid: String, _ value: Mark?, only: String? = nil, onlyMark: Mark = .inRoom) {
        snap()
        let rest: Mark = onlyMark == .out ? .inRoom : .out
        let logic = self.logic
        var marks = night.marks[sid] ?? [:]
        for pid in room.beds.flatMap(\.slots) {
            if logic.statusOf(pid, sid).offCheck { continue }
            let want: Mark? = only.map { pid == $0 ? onlyMark : rest } ?? value
            if let want { marks[pid] = want } else { marks.removeValue(forKey: pid) }
            night.touch(Merge.markKey(sid, pid))
        }
        night.marks[sid] = marks
        persist()
    }

    /// The round in one step: whoever is named is out, everyone else on the walk is in.
    public func markMissing(_ missing: Set<String>, _ sid: String) {
        snap()
        let logic = self.logic
        var marks = night.marks[sid] ?? [:]
        for p in Roster.PEOPLE where !logic.statusOf(p.id, sid).offCheck {
            let value: Mark = missing.contains(p.id) ? .out : .inRoom
            if marks[p.id] != value {
                marks[p.id] = value
                night.touch(Merge.markKey(sid, p.id))
            }
        }
        night.marks[sid] = marks
        persist()
    }

    public func toggleExcusedTonight(_ pid: String) {
        snap()
        if night.excusedTonight.contains(pid) {
            night.excusedTonight.remove(pid)
        } else {
            night.excusedTonight.insert(pid)
            for s in rounds {
                night.marks[s.id]?.removeValue(forKey: pid)
                night.touch(Merge.markKey(s.id, pid))
            }
        }
        night.touch(Merge.tonightKey(pid))
        persist()
    }

    public func setNote(_ pid: String, _ note: String) {
        if note.trimmingCharacters(in: .whitespaces).isEmpty { night.notes.removeValue(forKey: pid) } else { night.notes[pid] = note }
        night.touch(Merge.noteKey(pid))
        persist()
    }

    public func closeNight() {
        night.closed = true
        night.touch(Merge.CLOSED_KEY)
        editing = false
        reviewing = false
        persist()
    }

    public func undo() {
        guard let prev = undoStack.popLast() else { onToast?("Nothing to undo"); return }
        night = prev
        persist()
        onToast?("Undone")
    }

    // MARK: - moving about

    public func selectSlot(_ sid: String) { curSlot = sid; onChange?() }

    /// Jumps the plan to whoever was searched for and rings them - search is for the moment you
    /// hold a name and do not know the room, so it lands you ON the bed, not just in the room.
    public func findPerson(_ pid: String) {
        guard let i = Roster.PLAN.firstIndex(where: { r in r.beds.contains { $0.slots.contains(pid) } }) else { return }
        room = i
        highlight = pid
        reviewing = false
        onChange?()
    }

    public func clearHighlight() {
        guard highlight != nil else { return }
        highlight = nil
        onChange?()
    }
    public func goRoom(_ delta: Int) { jumpRoom(room + delta) }
    /// Another room drops the search ring. The room it is already in does not: the pages report
    /// the page they settle on, which after a search is the ringed one's own.
    public func jumpRoom(_ i: Int) {
        let to = max(0, min(Roster.PLAN.count - 1, i))
        if to != room { highlight = nil }
        room = to
        onChange?()
    }
    public func setEditing(_ on: Bool) { editing = on; reviewing = false; onChange?() }
    public func setReviewing(_ on: Bool) { reviewing = on; onChange?() }

    public func goToDate(_ key: String) {
        undoStack.removeAll()
        dateKey = key
        night = loadAndMaybeClose(key)
        editing = false
        reviewing = false
        // rounds belong to the night, so another night may not have the one you were on
        let slots = rounds
        if !slots.contains(where: { $0.id == curSlot }) { curSlot = slots.first?.id ?? curSlot }
        onChange?()
    }
    public func goToday() { goToDate(tonight()) }
    public func shiftDay(_ delta: Int) { goToDate(Dates.shiftKey(dateKey, delta)) }

    // MARK: - the night's own rounds

    /// Sets the rounds walked on THIS night, replacing whatever it had. Marks in a round being
    /// dropped go with it - they are marks from a walk that, as far as this night is now concerned,
    /// did not happen. Undo puts them back.
    public func setRoundsTonight(_ ids: [String]) {
        var seen = Set<String>()
        let want = ids.filter { Slots.isValidId($0) && seen.insert($0).inserted }.sorted { Slots.order($0) < Slots.order($1) }
        if want.isEmpty { onToast?("A night needs at least one round"); return }
        snap()
        let gone = rounds.map(\.id).filter { !want.contains($0) }
        for sid in gone {
            for pid in Array((night.marks[sid] ?? [:]).keys) { night.touch(Merge.markKey(sid, pid)) }
            night.marks.removeValue(forKey: sid)
            if night.sheetSlots.remove(sid) != nil { night.touch(Merge.sheetKey(sid)) }
        }
        // every round is its own cell, so two devices editing the night's rounds at the same time
        // merge the same way its marks do rather than one list flattening the other
        for sid in Set(night.rounds).union(want) {
            if want.contains(sid) { night.rounds.insert(sid) } else { night.rounds.remove(sid) }
            night.touch(Merge.slotKey(sid))
        }
        // standing on a round that is gone would leave the screen marking into nothing
        if !want.contains(curSlot) { curSlot = want[0] }
        persist()
    }

    /// Puts the night back on its usual rounds - Thursday's two on a Thursday, the standing three otherwise.
    public func resetRoundsTonight() {
        snap()
        for sid in night.rounds { night.touch(Merge.slotKey(sid)) }
        night.rounds.removeAll()
        for sid in night.sheetSlots { night.touch(Merge.sheetKey(sid)) }
        night.sheetSlots.removeAll()
        let slots = rounds
        if !slots.contains(where: { $0.id == curSlot }) { curSlot = slots[0].id }
        persist()
    }

    public func addRoundTonight(_ id: String) {
        guard Slots.isValidId(id) else { onToast?("Not a time"); return }
        let now = rounds.map(\.id)
        if now.contains(id) { onToast?("Already on this night"); return }
        setRoundsTonight(now + [id])
        curSlot = id
        onChange?()
    }

    /// The one-off in one step: this night had a single check, at this time.
    public func onlyRoundTonight(_ id: String) {
        guard Slots.isValidId(id) else { onToast?("Not a time"); return }
        setRoundsTonight([id])
    }

    public func removeRoundTonight(_ id: String) { setRoundsTonight(rounds.map(\.id).filter { $0 != id }) }

    /// The same walk, at a different time: everything already marked under [from] moves to [to] -
    /// the fix for having marked a round and only then seen the time was wrong.
    public func moveRoundTonight(_ from: String, _ to: String) {
        if from == to { return }
        guard Slots.isValidId(to) else { onToast?("Not a time"); return }
        let every = rounds.map(\.id)
        guard every.contains(from) else { return }
        if every.contains(to) { onToast?("Already on this night"); return }
        snap()
        if let marks = night.marks.removeValue(forKey: from) {
            night.marks[to] = marks
            for pid in marks.keys { night.touch(Merge.markKey(from, pid)); night.touch(Merge.markKey(to, pid)) }
        }
        // a night still on its usual rounds has to start naming them to hold the new time
        let want = (night.rounds.isEmpty ? every : Array(night.rounds)).map { $0 == from ? to : $0 }
        // the old time is stamped too, even on a night that never declared its rounds: a device
        // that did declare it has to be told it is gone, or the merge hands it back
        for sid in Set(night.rounds).union(want).union([from]) {
            if want.contains(sid) { night.rounds.insert(sid) } else { night.rounds.remove(sid) }
            night.touch(Merge.slotKey(sid))
        }
        if night.sheetSlots.remove(from) != nil {
            night.sheetSlots.insert(to)
            night.touch(Merge.sheetKey(from)); night.touch(Merge.sheetKey(to))
        }
        if curSlot == from { curSlot = to }
        persist()
    }

    /// Puts a round on this night's picture, or takes it off. All of them, unless you say otherwise.
    public func toggleSheetSlot(_ id: String) {
        let every = rounds.map(\.id)
        // "all of them" is stored as nothing at all, so the first tap has to write out the list it
        // is turning one off from
        if night.sheetSlots.isEmpty {
            for sid in every { night.sheetSlots.insert(sid); night.touch(Merge.sheetKey(sid)) }
        }
        if night.sheetSlots.contains(id) { night.sheetSlots.remove(id) } else { night.sheetSlots.insert(id) }
        night.touch(Merge.sheetKey(id))
        // back to everything ticked means back to no opinion, so a round added later joins in
        if night.sheetSlots == Set(every) {
            for sid in every { night.sheetSlots.remove(sid); night.touch(Merge.sheetKey(sid)) }
        }
        persist()
    }

    // MARK: - people

    public func toggleAlwaysExcused(_ pid: String) {
        let on = store.extra[pid]?.always != true
        if on { setPerson(pid, ("always", "1"), ("away", nil)) } else { setPerson(pid, ("always", nil)) }
        onToast?(on ? "Excused indefinitely" : "No longer excused")
    }

    public func toggleAway(_ pid: String) {
        let on = store.extra[pid]?.away != true
        if on { setPerson(pid, ("away", "1"), ("always", nil)) } else { setPerson(pid, ("away", nil)) }
        onToast?(on ? "Not in town until you turn it off" : "Back in town")
    }

    public func backInTown(_ pid: String) { setPerson(pid, ("away", nil)) }
    public func unexcuse(_ pid: String) { setPerson(pid, ("always", nil)) }

    public func setReason(_ pid: String, _ reason: String) {
        let r = reason.trimmingCharacters(in: .whitespaces)
        setPerson(pid, ("reason", r.isEmpty ? nil : reason))
    }

    public func renamePerson(_ pid: String, first: String, last: String) {
        func orNil(_ s: String) -> String? { s.trimmingCharacters(in: .whitespaces).isEmpty ? nil : s }
        setPerson(pid, ("first", orNil(first)), ("last", orNil(last)))
    }

    /// Turns the summer zman's stretch after the trip on or off, for every device.
    public func setSummerZman(_ on: Bool) {
        store.setPerson("yeshiva", "noSummer", on ? nil : "1")
        onChange?()
    }

    public func updateSettings(_ f: (inout Settings) -> Void) {
        f(&store.settings)
        store.saveState()
        onChange?()
    }

    /// A sync just finished: a night that changed under us is read again, or the screen keeps
    /// showing what it had before the other device's marks arrived.
    public func afterSync(changed: Int) {
        if changed > 0 { night = store.load(dateKey) }
        onChange?()
    }

    // MARK: - inside

    private func setPerson(_ pid: String, _ fields: (String, String?)...) {
        for (field, value) in fields { store.setPerson(pid, field, value) }
        onChange?()
    }

    private func snap() {
        undoStack.append(night.copy())
        if undoStack.count > 40 { undoStack.removeFirst() }
    }

    private func persist() {
        store.saveLocal(dateKey, night)
        onChange?()
    }

    private func loadAndMaybeClose(_ key: String) -> Night {
        let n = store.load(key)
        if key != tonight() && !n.closed {
            n.closed = true
            n.touch(Merge.CLOSED_KEY)
            store.save(key, n)
        }
        return n
    }

    /// Whichever of the night's rounds is nearest behind the clock right now, so opening lands on it.
    private func defaultSlot() -> String {
        let slots = rounds
        let c = Calendar.current.dateComponents([.hour, .minute], from: clock())
        let mins = Slots.order(Slots.idForClock(hour24: c.hour ?? 0, minute: c.minute ?? 0))
        return (slots.last { Slots.order($0.id) <= mins } ?? slots[0]).id
    }
}
