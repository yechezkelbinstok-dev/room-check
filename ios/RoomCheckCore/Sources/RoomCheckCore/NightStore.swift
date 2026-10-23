import Foundation

/// Every night lives in a file under [home]. [home] is a plain directory rather than an app's
/// documents URL directly, so tests can point a real store at a scratch folder.
///
/// [builtInSync] fills in this yeshiva's own server and password wherever none has been entered,
/// so a new install syncs from the first open. The app turns it on; tests leave it off, so they
/// never talk to the real server.
public final class NightStore {
    /// Everyone's standing settings - not in town, excused indefinitely and why, a changed name -
    /// ride to the server as one more night, under a date no real night can have. Each is a note
    /// cell named "pid.field", so the server and the merge carry them exactly like a night's notes,
    /// the later edit of each one winning, with nothing new on the server to deploy.
    public static let PEOPLE_KEY = "0000-00-00"

    /// Set to "1" in the shared record when the summer zman's stretch after the trip is switched
    /// off; absent, it is on.
    public static let NO_SUMMER = "yeshiva.noSummer"

    /// This yeshiva's sync server - the same one written down in server/README.md.
    public static let SYNC_URL = "https://room-check-sync.yechezkelbinstok.workers.dev"
    public static let SYNC_TOKEN = "6iciRn6C1NAU8xOGfHS99ZrCO2wtqjyfNQ-_Lr-jJDg"

    private let dir: URL
    private let stateFile: URL

    /// Only ever a reading of the shared people record, so it follows what arrives - change a
    /// person with `setPerson`, never by writing here.
    public internal(set) var extra: [String: PersonOverride] = [:]

    /// Whether the year has the summer zman's stretch after the trip. It belongs to the whole
    /// yeshiva, so it rides in the shared record and reads the same on every device.
    public private(set) var summerZman = true

    public var settings: Settings = Settings()
    /// How far through the server's changes this device has read.
    public var lastPull: Int64 = 0

    // Nights edited here since the last successful push. Kept on disk because the edit may have
    // happened on a bus with no signal and the app may be killed before it ever gets one.
    private var dirty: Set<String> = []
    // How many times each night has been edited since this store opened. A push reads it on the
    // way out, and only a night not edited again while the push was in the air counts as sent.
    private var edits: [String: Int] = [:]

    public init(home: URL, builtInSync: Bool = false) {
        dir = home.appendingPathComponent("nights", isDirectory: true)
        stateFile = home.appendingPathComponent("state.json")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        loadState()
        if builtInSync && settings.syncUrl.trimmingCharacters(in: .whitespaces).isEmpty {
            settings.syncUrl = NightStore.SYNC_URL
            if settings.syncToken.isEmpty { settings.syncToken = NightStore.SYNC_TOKEN }
        }
        // A device from before people synced has them only in state.json: start the shared
        // record from that, dated as old as can be so anything set anywhere since wins over it.
        if !hasData(NightStore.PEOPLE_KEY) && !extra.isEmpty {
            saveLocal(NightStore.PEOPLE_KEY, Merge.merge(Night(), NightStore.peopleNight(extra, stamp: 1)))
        }
        if hasData(NightStore.PEOPLE_KEY) { rebuildExtra() }
    }

    public func dirtyDates() -> Set<String> { dirty }
    public func clearDirty(_ done: Set<String>) { dirty.subtract(done); saveState() }

    /// The nights owed to the server, each with how many edits it has had - what a push sends.
    public func dirtySnapshot() -> [String: Int] {
        Dictionary(uniqueKeysWithValues: dirty.map { ($0, edits[$0] ?? 0) })
    }

    /// The push of [sent] went through: each night is clear unless it was edited again while the
    /// push was in the air - then it still owes the server that newer edit.
    public func clearDirty(sent: [String: Int]) {
        for (date, n) in sent where (edits[date] ?? 0) == n { dirty.remove(date) }
        saveState()
    }

    // MARK: - the shared people record

    /// [extra] written out as the people record's note cells, every one stamped [stamp].
    public static func peopleNight(_ extra: [String: PersonOverride], stamp: Int64) -> Night {
        let n = Night()
        for (pid, po) in extra {
            let fields: [(String, String?)] = [
                ("first", po.first), ("last", po.last), ("reason", po.reason),
                ("always", po.always ? "1" : nil), ("away", po.away ? "1" : nil)
            ]
            for (field, v) in fields {
                guard let v, !v.isEmpty else { continue }
                n.notes["\(pid).\(field)"] = v
                n.touch(Merge.noteKey("\(pid).\(field)"), at: stamp)
            }
        }
        return n
    }

    /// The people record read back into one override per person.
    public static func extraFrom(_ people: Night) -> [String: PersonOverride] {
        var out: [String: PersonOverride] = [:]
        for (key, v) in people.notes {
            guard let dot = key.firstIndex(of: ".") else { continue }
            let pid = String(key[..<dot]), field = String(key[key.index(after: dot)...])
            // the record also carries what belongs to the yeshiva as a whole, under "yeshiva."
            guard Roster.byId[pid] != nil else { continue }
            var po = out[pid] ?? PersonOverride()
            switch field {
            case "first": po.first = v
            case "last": po.last = v
            case "reason": po.reason = v
            case "always": po.always = v == "1"
            case "away": po.away = v == "1"
            default: break
            }
            out[pid] = po
        }
        return out
    }

    /// One person's one setting, changed here: saved, owed to the server, and read back.
    public func setPerson(_ pid: String, _ field: String, _ value: String?) {
        let n = load(NightStore.PEOPLE_KEY)
        let key = "\(pid).\(field)"
        if let value, !value.isEmpty { n.notes[key] = value } else { n.notes.removeValue(forKey: key) }
        n.touch(Merge.noteKey(key))
        saveLocal(NightStore.PEOPLE_KEY, n)
        rebuildExtra()
    }

    /// Reads [extra] and the summer switch afresh from the people record - after a sync, or an edit.
    public func rebuildExtra() {
        let people = load(NightStore.PEOPLE_KEY)
        extra = NightStore.extraFrom(people)
        summerZman = people.notes[NightStore.NO_SUMMER] != "1"
        saveState()
    }

    // MARK: - this device's own state

    private func loadState() {
        extra = [:]
        guard let data = try? Data(contentsOf: stateFile),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        if let s = root["settings"] as? [String: Any] {
            settings = Settings(
                bunkLabels: (s["bunkLabels"] as? Bool) ?? false,
                hebrewOnPlan: (s["hebrewOnPlan"] as? Bool) ?? false,
                hebrewInExport: (s["hebrewInExport"] as? Bool) ?? false,
                luachEnglish: (s["luachEnglish"] as? Bool) ?? false,
                luachList: (s["luachList"] as? Bool) ?? false,
                luachMonths: (s["luachMonths"] as? String) ?? "",
                syncUrl: (s["syncUrl"] as? String) ?? "",
                syncToken: (s["syncToken"] as? String) ?? ""
            )
        }
        if let n = root["lastPull"] as? NSNumber { lastPull = n.int64Value }
        if let d = root["dirty"] as? [String] { dirty = Set(d) }
        if let extraObj = root["extra"] as? [String: Any] {
            for (pid, raw) in extraObj {
                if let po = raw as? [String: Any] { extra[pid] = PersonOverride.from(po) }
            }
        }
    }

    private func extraJSONObject() -> [String: Any] {
        extra.mapValues { $0.jsonObject() }
    }

    public func saveState() {
        let root: [String: Any] = [
            "extra": extraJSONObject(),
            "settings": [
                "bunkLabels": settings.bunkLabels, "hebrewOnPlan": settings.hebrewOnPlan,
                "hebrewInExport": settings.hebrewInExport, "luachEnglish": settings.luachEnglish,
                "luachList": settings.luachList, "luachMonths": settings.luachMonths,
                "syncUrl": settings.syncUrl, "syncToken": settings.syncToken
            ] as [String: Any],
            "lastPull": lastPull,
            "dirty": Array(dirty)
        ]
        if let data = try? JSONSerialization.data(withJSONObject: root) {
            try? data.write(to: stateFile)
        }
    }

    // MARK: - nights

    private func fileFor(_ dateKey: String) -> URL { dir.appendingPathComponent("\(dateKey).json") }

    public func load(_ dateKey: String) -> Night {
        let f = fileFor(dateKey)
        guard let data = try? Data(contentsOf: f) else { return Night() }
        // a night written before timestamps existed is dated by its file, not by zero
        let mtime = (try? FileManager.default.attributesOfItem(atPath: f.path)[.modificationDate] as? Date) ?? nil
        let fallback = Int64(((mtime ?? Date()) as Date).timeIntervalSince1970 * 1000)
        return Night.fromJSONData(data, fallbackStamp: fallback)
    }

    public func save(_ dateKey: String, _ night: Night) {
        if let data = try? night.toJSONData() { try? data.write(to: fileFor(dateKey)) }
    }

    /// Saves a local edit and remembers the night still owes the server a push.
    public func saveLocal(_ dateKey: String, _ night: Night) {
        save(dateKey, night)
        edits[dateKey, default: 0] += 1
        if dirty.insert(dateKey).inserted { saveState() }
    }

    public func hasData(_ dateKey: String) -> Bool { FileManager.default.fileExists(atPath: fileFor(dateKey).path) }

    /// Every night kept here - never the people record, which only looks like one.
    public func allDateKeys() -> Set<String> {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        return Set(names.filter { $0.hasSuffix(".json") }.map { String($0.dropLast(5)) }
            .filter { $0 != NightStore.PEOPLE_KEY })
    }

    // MARK: - backup / restore, same schema as the website and the Android app

    public func exportBackup() -> String {
        // the shared record goes too, so a restore brings back the summer switch with the nights
        let keys = allDateKeys().union(hasData(NightStore.PEOPLE_KEY) ? [NightStore.PEOPLE_KEY] : [])
        let root: [String: Any] = [
            "v": 1,
            "saved": ISO8601DateFormatter().string(from: Date()),
            "extra": extraJSONObject(),
            "nights": Dictionary(uniqueKeysWithValues: keys.map { key in
                ("dn:\(key)", String(data: (try? load(key).toJSONData()) ?? Data(), encoding: .utf8) ?? "{}")
            })
        ]
        let data = (try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    public func importBackup(_ text: String) -> Bool {
        guard let data = text.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        guard root["nights"] != nil || root["extra"] != nil else { return false }

        extra = [:]
        if let extraObj = root["extra"] as? [String: Any] {
            for (pid, raw) in extraObj {
                if let po = raw as? [String: Any] { extra[pid] = PersonOverride.from(po) }
            }
        }
        saveState()

        if let nightsObj = root["nights"] as? [String: Any] {
            for (key, raw) in nightsObj {
                guard let jsonStr = raw as? String, let jsonData = jsonStr.data(using: .utf8) else { continue }
                let dateKey = key.hasPrefix("dn:") ? String(key.dropFirst(3)) : key
                let night = Night.fromJSONData(jsonData)
                // a backup from the site carries the people record too: fold it in, never
                // overwrite, or settings changed since the backup would be thrown away
                if dateKey == NightStore.PEOPLE_KEY { saveLocal(dateKey, Merge.merge(load(dateKey), night)) }
                else { save(dateKey, night) }
            }
        }
        // the backup's own list, as old as can be, so anything set since it was taken wins
        saveLocal(NightStore.PEOPLE_KEY, Merge.merge(load(NightStore.PEOPLE_KEY), NightStore.peopleNight(extra, stamp: 1)))
        rebuildExtra()
        return true
    }
}
