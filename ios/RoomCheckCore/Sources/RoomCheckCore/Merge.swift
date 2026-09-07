import Foundation

/// How two copies of the same night are reconciled.
///
/// Whole-night last-write-wins would be wrong here: two people walking different rooms at the same
/// time would each save a full night, and whoever saved second would erase the other's rooms. So a
/// night carries a timestamp per CELL - one mark, one note, one excusal, one round, one sheet
/// choice - and merging is decided cell by cell. Marking Room 1 on the phone and Room 5 on the
/// website at the same moment leaves both, because they are different cells and neither is newer.
///
/// This is the third implementation of the rule - Merge.kt for the app, merge.js for the website
/// and the sync server - and all three are pinned to the same cases in fixtures.json, so they
/// cannot quietly drift into disagreeing about who wins.
public enum Merge {
    public static func markKey(_ sid: String, _ pid: String) -> String { "m:\(sid):\(pid)" }
    public static func tonightKey(_ pid: String) -> String { "t:\(pid)" }
    public static func noteKey(_ pid: String) -> String { "n:\(pid)" }
    /// An extra round walked on this night only.
    public static func slotKey(_ sid: String) -> String { "s:\(sid)" }
    /// This round goes on this night's picture.
    public static func sheetKey(_ sid: String) -> String { "h:\(sid)" }
    public static let CLOSED_KEY = "c"

    /// Every cell either side knows anything about.
    private static func keysOf(_ n: Night) -> Set<String> {
        var out = Set<String>()
        for (sid, slot) in n.marks { for pid in slot.keys { out.insert(markKey(sid, pid)) } }
        for pid in n.excusedTonight { out.insert(tonightKey(pid)) }
        for pid in n.notes.keys { out.insert(noteKey(pid)) }
        for sid in n.rounds { out.insert(slotKey(sid)) }
        for sid in n.sheetSlots { out.insert(sheetKey(sid)) }
        out.insert(CLOSED_KEY)
        out.formUnion(n.stamps.keys)
        return out
    }

    /// The cell's value as a string, or nil when the cell is empty - unmarking is a value too.
    public static func valueOf(_ n: Night, _ key: String) -> String? {
        if key == CLOSED_KEY { return n.closed ? "1" : nil }
        if key.hasPrefix("m:") {
            let rest = key.dropFirst(2)
            guard let colon = rest.firstIndex(of: ":") else { return nil }
            let sid = String(rest[rest.startIndex..<colon])
            let pid = String(rest[rest.index(after: colon)...])
            return n.marks[sid]?[pid]?.rawValue
        }
        if key.hasPrefix("t:") { return n.excusedTonight.contains(String(key.dropFirst(2))) ? "1" : nil }
        if key.hasPrefix("n:") { return n.notes[String(key.dropFirst(2))] }
        if key.hasPrefix("s:") { return n.rounds.contains(String(key.dropFirst(2))) ? "1" : nil }
        if key.hasPrefix("h:") { return n.sheetSlots.contains(String(key.dropFirst(2))) ? "1" : nil }
        return nil
    }

    public static func setValue(_ n: Night, _ key: String, _ value: String?) {
        if key == CLOSED_KEY { n.closed = value != nil; return }
        if key.hasPrefix("m:") {
            let rest = key.dropFirst(2)
            guard let colon = rest.firstIndex(of: ":") else { return }
            let sid = String(rest[rest.startIndex..<colon])
            let pid = String(rest[rest.index(after: colon)...])
            var slot = n.marks[sid] ?? [:]
            if let value, let mark = Mark(rawValue: value) { slot[pid] = mark } else { slot.removeValue(forKey: pid) }
            n.marks[sid] = slot
            return
        }
        if key.hasPrefix("t:") {
            let pid = String(key.dropFirst(2))
            if value != nil { n.excusedTonight.insert(pid) } else { n.excusedTonight.remove(pid) }
            return
        }
        if key.hasPrefix("n:") {
            let pid = String(key.dropFirst(2))
            if let value, !value.isEmpty { n.notes[pid] = value } else { n.notes.removeValue(forKey: pid) }
            return
        }
        if key.hasPrefix("s:") {
            let sid = String(key.dropFirst(2))
            if value != nil { n.rounds.insert(sid) } else { n.rounds.remove(sid) }
            return
        }
        if key.hasPrefix("h:") {
            let sid = String(key.dropFirst(2))
            if value != nil { n.sheetSlots.insert(sid) } else { n.sheetSlots.remove(sid) }
            return
        }
    }

    /// The later edit of each cell wins. Ties fall back to comparing the values themselves, so two
    /// devices merging the same pair in either order always land on the same answer rather than on
    /// whichever one happened to run second.
    public static func merge(_ local: Night, _ remote: Night) -> Night {
        let out = Night()
        let keys = keysOf(local).union(keysOf(remote)).sorted()
        for key in keys {
            let lt = local.stamps[key] ?? 0
            let rt = remote.stamps[key] ?? 0
            let lv = valueOf(local, key)
            let rv = valueOf(remote, key)
            let takeRemote = lt != rt ? (rt > lt) : ((rv ?? "") > (lv ?? ""))
            let value = takeRemote ? rv : lv
            setValue(out, key, value)
            let stamp = max(lt, rt)
            if stamp > 0 || value != nil { out.stamps[key] = stamp }
        }
        // a slot whose last mark was cleared leaves an empty map behind; drop it so an empty
        // night is empty in every implementation, not just some of them
        for sid in out.marks.keys where out.marks[sid]?.isEmpty == true { out.marks.removeValue(forKey: sid) }
        return out
    }
}
