import Foundation

public enum Mark: String, Codable {
    case inRoom = "in"
    case out = "out"
    case exc = "exc"
}

/// One night's marks and everything about it that a device can edit. Mirrors the exact JSON shape
/// used by the Android app, the website, and the sync server - all four have to read and write the
/// same wire format or a night saved on one device would come out empty on another.
///
/// Rounds are on the night itself, not the app: a night that names its own `rounds` gets THOSE
/// instead of the standing three, and the marks in a round dropped for the night go with it.
public final class Night {
    public var marks: [String: [String: Mark]]
    public var excusedTonight: Set<String>
    public var notes: [String: String]
    public var closed: Bool
    public var rounds: Set<String>
    public var sheetSlots: Set<String>
    /// When each cell was last set, so two devices' copies can be merged cell by cell.
    public var stamps: [String: Int64]

    public init(
        marks: [String: [String: Mark]] = [:],
        excusedTonight: Set<String> = [],
        notes: [String: String] = [:],
        closed: Bool = false,
        rounds: Set<String> = [],
        sheetSlots: Set<String> = [],
        stamps: [String: Int64] = [:]
    ) {
        self.marks = marks
        self.excusedTonight = excusedTonight
        self.notes = notes
        self.closed = closed
        self.rounds = rounds
        self.sheetSlots = sheetSlots
        self.stamps = stamps
    }

    /// Records that a cell just changed. Every edit goes through here or it will not sync.
    public func touch(_ key: String, at: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        stamps[key] = at
    }

    /// A deep copy - used for the undo stack, so the copy on the stack cannot be mutated by later
    /// edits to the live night.
    public func copy() -> Night {
        Night(marks: marks, excusedTonight: excusedTonight, notes: notes, closed: closed,
              rounds: rounds, sheetSlots: sheetSlots, stamps: stamps)
    }
}

// MARK: - JSON

/// Hand-written rather than synthesized `Codable`: the wire format is a JSON object whose "marks"
/// value is itself an object of objects, and Swift's dictionary encoding order is not stable the
/// way the other three implementations need for a byte-identical "did this change?" comparison.
extension Night {
    private enum Key: String { case marks, tonight, notes, closed, slots, sheet, ts }

    public func toJSONObject() -> [String: Any] {
        var marksOut: [String: Any] = [:]
        for (sid, slot) in marks {
            var slotOut: [String: String] = [:]
            for (pid, mark) in slot { slotOut[pid] = mark.rawValue }
            marksOut[sid] = slotOut
        }
        return [
            Key.marks.rawValue: marksOut,
            Key.tonight.rawValue: Array(excusedTonight).sorted(),
            Key.notes.rawValue: notes,
            Key.closed.rawValue: closed,
            Key.slots.rawValue: Array(rounds).sorted(),
            Key.sheet.rawValue: Array(sheetSlots).sorted(),
            Key.ts.rawValue: stamps
        ]
    }

    public func toJSONData() throws -> Data {
        try JSONSerialization.data(withJSONObject: toJSONObject(), options: [.sortedKeys])
    }

    /// [fallbackStamp] dates cells in a night saved before timestamps existed - the file's own
    /// mtime, which is roughly when those marks were made. Without it they would all read as
    /// "never set" and the first sync from any other device would wipe them.
    public static func fromJSONObject(_ obj: [String: Any]?, fallbackStamp: Int64 = 0) -> Night {
        let n = Night()
        guard let obj else { return n }

        if let marksObj = obj[Key.marks.rawValue] as? [String: Any] {
            for (sid, raw) in marksObj {
                guard let slotObj = raw as? [String: Any] else { continue }
                var slot: [String: Mark] = [:]
                for (pid, v) in slotObj {
                    guard let s = v as? String, let m = Mark(rawValue: s) else { continue }
                    slot[pid] = m
                }
                n.marks[sid] = slot
            }
        }
        if let tonight = obj[Key.tonight.rawValue] as? [String] {
            n.excusedTonight = Set(tonight)
        }
        if let notesObj = obj[Key.notes.rawValue] as? [String: Any] {
            for (pid, v) in notesObj { if let s = v as? String { n.notes[pid] = s } }
        }
        n.closed = (obj[Key.closed.rawValue] as? Bool) ?? false
        if let slots = obj[Key.slots.rawValue] as? [String] { n.rounds = Set(slots) }
        if let sheet = obj[Key.sheet.rawValue] as? [String] { n.sheetSlots = Set(sheet) }

        if let ts = obj[Key.ts.rawValue] as? [String: Any] {
            for (k, v) in ts {
                if let num = v as? NSNumber { n.stamps[k] = num.int64Value }
            }
        } else if fallbackStamp > 0 {
            for (sid, slot) in n.marks {
                for pid in slot.keys { n.stamps[Merge.markKey(sid, pid)] = fallbackStamp }
            }
            for pid in n.excusedTonight { n.stamps[Merge.tonightKey(pid)] = fallbackStamp }
            for pid in n.notes.keys { n.stamps[Merge.noteKey(pid)] = fallbackStamp }
            for sid in n.rounds { n.stamps[Merge.slotKey(sid)] = fallbackStamp }
            for sid in n.sheetSlots { n.stamps[Merge.sheetKey(sid)] = fallbackStamp }
            if n.closed { n.stamps[Merge.CLOSED_KEY] = fallbackStamp }
        }
        return n
    }

    public static func fromJSONData(_ data: Data, fallbackStamp: Int64 = 0) -> Night {
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        return fromJSONObject(obj, fallbackStamp: fallbackStamp)
    }
}

public struct PersonOverride: Codable {
    public var first: String?
    public var last: String?
    public var always: Bool = false
    public var reason: String?
    public init(first: String? = nil, last: String? = nil, always: Bool = false, reason: String? = nil) {
        self.first = first; self.last = last; self.always = always; self.reason = reason
    }
}

/// The things you can turn on and off, kept off by default: the plan and the sheet read cleaner
/// without them, and anyone who wants them back can say so in Names.
public struct Settings: Codable {
    /// "top"/"bottom" written on each half of a bunk.
    public var bunkLabels: Bool = false
    /// Hebrew names on the floor plan you tap through.
    public var hebrewOnPlan: Bool = false
    /// Hebrew names in the sent picture. Separate on purpose: you may want one and not the other.
    public var hebrewInExport: Bool = false
    /// Where the shared copy lives. Blank means this device keeps to itself, as it always did.
    public var syncUrl: String = ""
    public var syncToken: String = ""

    public init(bunkLabels: Bool = false, hebrewOnPlan: Bool = false, hebrewInExport: Bool = false,
                syncUrl: String = "", syncToken: String = "") {
        self.bunkLabels = bunkLabels
        self.hebrewOnPlan = hebrewOnPlan
        self.hebrewInExport = hebrewInExport
        self.syncUrl = syncUrl
        self.syncToken = syncToken
    }
}
