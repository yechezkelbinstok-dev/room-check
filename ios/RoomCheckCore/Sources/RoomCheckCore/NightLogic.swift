import Foundation

public struct SlotStats: Equatable {
    public let out: Int, inCount: Int, exc: Int, left: Int, started: Bool, away: Int
    public init(out: Int, inCount: Int, exc: Int, left: Int, started: Bool, away: Int = 0) {
        self.out = out; self.inCount = inCount; self.exc = exc; self.left = left; self.started = started; self.away = away
    }
}

/// [slots] is the times this night is walked at - its usual rounds, or its own, plus anything
/// marked in.
public final class NightLogic {
    private let night: Night
    private let extra: [String: PersonOverride]
    public let slots: [Slot]

    public init(_ night: Night, _ extra: [String: PersonOverride], _ slots: [Slot] = Slots.DEFAULT) {
        self.night = night; self.extra = extra; self.slots = slots
    }

    public var sids: [String] { slots.map(\.id) }

    public func isAlways(_ pid: String) -> Bool { extra[pid]?.always == true }
    public func isAway(_ pid: String) -> Bool { extra[pid]?.away == true }
    public func first(_ pid: String) -> String { extra[pid]?.first ?? Roster.byId[pid]?.first ?? "" }
    public func last(_ pid: String) -> String { extra[pid]?.last ?? Roster.byId[pid]?.last ?? "" }
    public func nameOf(_ pid: String) -> String { "\(first(pid)) \(last(pid))" }

    // Hebrew is only used where the whole person has it, so nobody ends up half-transliterated
    // with a Hebrew given name against an English surname.
    private func hasHebrew(_ pid: String) -> Bool {
        guard let p = Roster.byId[pid] else { return false }
        return !p.hebFirst.isEmpty && !p.hebLast.isEmpty
    }

    public func first(_ pid: String, hebrew: Bool) -> String {
        hebrew && hasHebrew(pid) ? (Roster.byId[pid]?.hebFirst ?? "") : first(pid)
    }
    public func last(_ pid: String, hebrew: Bool) -> String {
        hebrew && hasHebrew(pid) ? (Roster.byId[pid]?.hebLast ?? "") : last(pid)
    }
    public func nameOf(_ pid: String, hebrew: Bool) -> String { "\(first(pid, hebrew: hebrew)) \(last(pid, hebrew: hebrew))" }

    /// True once at least one person actually has a Hebrew name to switch to.
    public func anyHebrewNames() -> Bool { Roster.PEOPLE.contains { !$0.hebFirst.isEmpty } }

    public func statusOf(_ pid: String, _ sid: String) -> Mark? {
        if isAlways(pid) { return .exc }
        if isAway(pid) { return .away }
        if night.excusedTonight.contains(pid) { return .exc }
        return night.marks[sid]?[pid]
    }

    private func livePids(_ room: Room, _ sid: String) -> [String] {
        room.beds.flatMap(\.slots).filter { !statusOf($0, sid).offCheck }
    }

    public func roomChecked(_ room: Room, _ sid: String) -> Bool {
        let live = livePids(room, sid)
        if live.isEmpty { return true }
        return live.contains { night.marks[sid]?[$0] != nil }
    }

    public func roomAllIn(_ room: Room, _ sid: String) -> Bool {
        var any = false
        for bed in room.beds {
            for pid in bed.slots {
                let st = statusOf(pid, sid)
                if st.offCheck { continue }
                if st != .inRoom { return false }
                any = true
            }
        }
        return any || roomChecked(room, sid)
    }

    /// Share of the room still on the walk that is marked out at [sid] - 0 when nobody is.
    public func missingShare(_ room: Room, _ sid: String) -> Double {
        let live = livePids(room, sid)
        if live.isEmpty { return 0 }
        return Double(live.filter { statusOf($0, sid) == .out }.count) / Double(live.count)
    }

    /// Share of the room still on the walk that is marked in at [sid].
    public func inShare(_ room: Room, _ sid: String) -> Double {
        let live = livePids(room, sid)
        if live.isEmpty { return 0 }
        return Double(live.filter { statusOf($0, sid) == .inRoom }.count) / Double(live.count)
    }

    public func outIn(_ room: Room, _ sid: String) -> [String] {
        room.beds.flatMap(\.slots).filter { statusOf($0, sid) == .out }
    }

    public func stats(_ sid: String) -> SlotStats {
        var out = 0, inC = 0, exc = 0, away = 0
        for p in Roster.PEOPLE {
            switch statusOf(p.id, sid) {
            case .out: out += 1
            case .inRoom: inC += 1
            case .exc: exc += 1
            case .away: away += 1
            case nil: break
            }
        }
        let left = Roster.PLAN.filter { !roomChecked($0, sid) }.count
        return SlotStats(out: out, inCount: inC, exc: exc, left: left, started: left < Roster.PLAN.count, away: away)
    }

    /// Everyone marked out at [sid], in room order then bed order - the order you walked them.
    public func missingAt(_ sid: String) -> [String] {
        Roster.PLAN.flatMap { room in room.beds.flatMap(\.slots) }.filter { statusOf($0, sid) == .out }
    }

    /// Everyone switched to not in town, in walking order.
    public func awayList() -> [String] {
        Roster.PLAN.flatMap { room in room.beds.flatMap(\.slots) }.filter { isAway($0) }
    }

    public func uncheckedAt(_ sid: String) -> [Room] { Roster.PLAN.filter { !roomChecked($0, sid) } }

    /// The night as plain text to paste into a message: the Hebrew date, then each time with just
    /// the names of whoever wasn't there. Nothing else - no room labels, no counts, no totals.
    public func report(_ dateKey: String) -> String {
        var lines = [Dates.hebrewDayMonth(dateKey)]
        for (i, sid) in sids.enumerated() {
            lines.append("")
            lines.append(slots[i].label)
            let missing = missingAt(sid)
            if !missing.isEmpty {
                lines.append(missing.map { nameOf($0) }.joined(separator: ", "))
            } else if !stats(sid).started {
                lines.append("Not marked")
            } else {
                lines.append("Everybody there")
            }
            let unchecked = uncheckedAt(sid)
            if stats(sid).started && !unchecked.isEmpty {
                lines.append("(still to mark: \(unchecked.map(\.label).joined(separator: ", ")))")
            }
        }
        let away = awayList()
        if !away.isEmpty {
            lines.append("")
            lines.append("Not in town")
            lines.append(away.map { nameOf($0) }.joined(separator: ", "))
        }
        return lines.joined(separator: "\n")
    }
}
