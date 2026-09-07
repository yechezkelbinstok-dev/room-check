import Foundation
#if canImport(FoundationNetworking)
// Linux splits URLSession/URLRequest/etc. into a separate module; Apple platforms keep it all in
// Foundation. This is the only place that split shows up, since it is the only file that touches
// the network.
import FoundationNetworking
#endif

/// What the last sync did, for the one line about it in Settings.
public enum SyncState: Equatable {
    case off
    case working
    case ok(at: Date, changed: Int)
    case failed(reason: String)
}

public enum SyncError: Error, LocalizedError {
    case noAddress
    case wrongPassword
    case serverStatus(Int)
    case unreachable(String)

    public var errorDescription: String? {
        switch self {
        case .noAddress: return "No sync address set"
        case .wrongPassword: return "Wrong sync password"
        case .serverStatus(let code): return "Server said \(code)"
        case .unreachable(let msg): return msg
        }
    }
}

/// Keeps this device's nights and the server's in step.
///
/// Offline-first on purpose: the phone's own files stay the truth and every mark is saved and
/// usable with no signal at all. Sync is a background reconciliation on top of that, never a gate
/// in front of it - a bed check happens at 11pm in a building with bad reception, and it cannot
/// wait on a network round trip.
///
/// Nothing is ever overwritten wholesale. Local edits are pushed, the server merges them into
/// whatever else arrived, and what comes back is merged in again cell by cell, so two people
/// marking different rooms at once keep both sets. Talks to the exact same Worker as the Android
/// app and the website - same `/push` and `/pull` shape, same Bearer token.
public final class SyncClient {
    private let store: NightStore
    private let session: URLSession

    public init(store: NightStore, session: URLSession = .shared) {
        self.store = store
        self.session = session
    }

    private func post(_ base: String, _ path: String, _ token: String, _ body: [String: Any]) async throws -> [String: Any] {
        guard let url = URL(string: base.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path) else {
            throw SyncError.unreachable("Bad address")
        }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp): (Data, URLResponse)
        do {
            (data, resp) = try await session.data(for: req)
        } catch {
            throw SyncError.unreachable(error.localizedDescription)
        }
        guard let http = resp as? HTTPURLResponse else { throw SyncError.unreachable("No response") }
        if http.statusCode == 401 { throw SyncError.wrongPassword }
        guard (200...299).contains(http.statusCode) else { throw SyncError.serverStatus(http.statusCode) }
        if data.isEmpty { return [:] }
        return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    /// One full exchange: push what changed here, then take everything that changed elsewhere.
    /// Returns how many nights ended up different locally.
    @discardableResult
    public func sync() async throws -> Int {
        let base = store.settings.syncUrl.trimmingCharacters(in: .whitespaces)
        guard !base.isEmpty else { throw SyncError.noAddress }
        let token = store.settings.syncToken

        var changed = 0

        // push first, so a night edited here is on the server before we ask what we are missing
        let dirty = store.dirtyDates()
        if !dirty.isEmpty {
            var nights: [String: Any] = [:]
            for date in dirty { nights[date] = store.load(date).toJSONObject() }
            let res = try await post(base, "/push", token, ["nights": nights])
            if let back = res["nights"] as? [String: Any] {
                for (date, raw) in back {
                    guard let obj = raw as? [String: Any] else { continue }
                    if adopt(date, Night.fromJSONObject(obj)) { changed += 1 }
                }
            }
            store.clearDirty(dirty)
        }

        let since = store.lastPull
        let res = try await post(base, "/pull", token, ["since": since])
        if let nights = res["nights"] as? [String: Any] {
            for (date, raw) in nights {
                guard let obj = raw as? [String: Any] else { continue }
                if adopt(date, Night.fromJSONObject(obj)) { changed += 1 }
            }
        }
        if let now = res["now"] as? NSNumber { store.lastPull = now.int64Value }
        store.saveState()
        return changed
    }

    /// Merge a night from the server into the local copy. True when the local copy actually moved.
    private func adopt(_ date: String, _ remote: Night) -> Bool {
        let local = store.load(date)
        let merged = Merge.merge(local, remote)
        guard let before = try? local.toJSONData(), let after = try? merged.toJSONData() else { return false }
        if before == after { return false }
        store.save(date, merged)
        return true
    }
}
