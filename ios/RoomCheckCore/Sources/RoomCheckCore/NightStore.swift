import Foundation

/// Every night lives in a file under [home]. [home] is a plain directory rather than an app's
/// documents URL directly, so tests can point a real store at a scratch folder.
public final class NightStore {
    private let dir: URL
    private let stateFile: URL

    public var extra: [String: PersonOverride] = [:]
    public var settings: Settings = Settings()
    /// How far through the server's changes this device has read.
    public var lastPull: Int64 = 0

    // Nights edited here since the last successful push. Kept on disk because the edit may have
    // happened on a bus with no signal and the app may be killed before it ever gets one.
    private var dirty: Set<String> = []

    public init(home: URL) {
        dir = home.appendingPathComponent("nights", isDirectory: true)
        stateFile = home.appendingPathComponent("state.json")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        loadState()
    }

    public func dirtyDates() -> Set<String> { dirty }
    public func clearDirty(_ done: Set<String>) { dirty.subtract(done); saveState() }

    private func loadState() {
        extra = [:]
        guard let data = try? Data(contentsOf: stateFile),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        if let s = root["settings"] as? [String: Any] {
            settings = Settings(
                bunkLabels: (s["bunkLabels"] as? Bool) ?? false,
                hebrewOnPlan: (s["hebrewOnPlan"] as? Bool) ?? false,
                hebrewInExport: (s["hebrewInExport"] as? Bool) ?? false,
                syncUrl: (s["syncUrl"] as? String) ?? "",
                syncToken: (s["syncToken"] as? String) ?? ""
            )
        }
        if let n = root["lastPull"] as? NSNumber { lastPull = n.int64Value }
        if let d = root["dirty"] as? [String] { dirty = Set(d) }
        if let extraObj = root["extra"] as? [String: Any] {
            for (pid, raw) in extraObj {
                guard let po = raw as? [String: Any] else { continue }
                extra[pid] = PersonOverride(
                    first: po["first"] as? String, last: po["last"] as? String,
                    always: (po["always"] as? Bool) ?? false, reason: po["reason"] as? String
                )
            }
        }
    }

    private func extraJSONObject() -> [String: Any] {
        var out: [String: Any] = [:]
        for (pid, po) in extra {
            var o: [String: Any] = ["always": po.always]
            if let v = po.first { o["first"] = v }
            if let v = po.last { o["last"] = v }
            if let v = po.reason { o["reason"] = v }
            out[pid] = o
        }
        return out
    }

    public func saveState() {
        let root: [String: Any] = [
            "extra": extraJSONObject(),
            "settings": [
                "bunkLabels": settings.bunkLabels, "hebrewOnPlan": settings.hebrewOnPlan,
                "hebrewInExport": settings.hebrewInExport, "syncUrl": settings.syncUrl, "syncToken": settings.syncToken
            ],
            "lastPull": lastPull,
            "dirty": Array(dirty)
        ]
        if let data = try? JSONSerialization.data(withJSONObject: root) {
            try? data.write(to: stateFile)
        }
    }

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
        if dirty.insert(dateKey).inserted { saveState() }
    }

    public func hasData(_ dateKey: String) -> Bool { FileManager.default.fileExists(atPath: fileFor(dateKey).path) }

    public func allDateKeys() -> Set<String> {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        return Set(names.filter { $0.hasSuffix(".json") }.map { String($0.dropLast(5)) })
    }

    // MARK: - backup / restore, same schema as the website and the Android app

    public func exportBackup() -> String {
        let root: [String: Any] = [
            "v": 1,
            "saved": ISO8601DateFormatter().string(from: Date()),
            "extra": extraJSONObject(),
            "nights": Dictionary(uniqueKeysWithValues: allDateKeys().map { key in
                ("dn:\(key)", String(data: (try? load(key).toJSONData()) ?? Data(), encoding: .utf8) ?? "{}")
            })
        ]
        let data = (try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    public func importBackup(_ text: String) -> Bool {
        guard let data = text.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        let hasNights = root["nights"] != nil
        let hasExtra = root["extra"] != nil
        guard hasNights || hasExtra else { return false }

        extra = [:]
        if let extraObj = root["extra"] as? [String: Any] {
            for (pid, raw) in extraObj {
                guard let po = raw as? [String: Any] else { continue }
                extra[pid] = PersonOverride(
                    first: po["first"] as? String, last: po["last"] as? String,
                    always: (po["always"] as? Bool) ?? false, reason: po["reason"] as? String
                )
            }
        }
        saveState()

        if let nightsObj = root["nights"] as? [String: Any] {
            for (key, raw) in nightsObj {
                guard let jsonStr = raw as? String, let jsonData = jsonStr.data(using: .utf8) else { continue }
                let dateKey = key.hasPrefix("dn:") ? String(key.dropFirst(3)) : key
                save(dateKey, Night.fromJSONData(jsonData))
            }
        }
        return true
    }
}
