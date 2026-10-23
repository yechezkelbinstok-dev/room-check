import Foundation
import RoomCheckCore

/// What the screens watch: the open night (a NightSession, from the shared code) and the sync
/// with the yeshiva's server. Every change to the night bumps [rev], which is what redraws.
///
/// Everything here runs on the main thread, as SwiftUI calls it - including every read and write
/// of the store a sync makes. Only the waits on the network leave it.
final class AppModel: ObservableObject {
    let session: NightSession
    private let client: SyncClient?

    @Published private(set) var rev = 0
    @Published private(set) var toast: String?
    @Published private(set) var syncState: SyncState = .off

    private var syncing = false
    private var pushLater: DispatchWorkItem?
    private var toastLater: DispatchWorkItem?

    init(session: NightSession, client: SyncClient?) {
        self.session = session
        self.client = client
        session.onChange = { [weak self] in self?.changed() }
        session.onToast = { [weak self] in self?.show($0) }
    }

    /// The real app: the phone's own storage, the yeshiva's server built in - or, when the
    /// screenshot run asks for one, a demo night that never goes near the server.
    static func make() -> AppModel {
        if let name = UserDefaults.standard.string(forKey: "demo") { return Demo.model(name) }
        let home = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("RoomCheck", isDirectory: true)
        let store = NightStore(home: home, builtInSync: true)
        return AppModel(session: NightSession(store: store), client: SyncClient(store: store))
    }

    private func changed() {
        rev += 1
        // marking a room is a burst of taps: wait for it to settle rather than syncing per tap -
        // the marks are already safe on the phone, this is only about sharing them
        if client != nil && !session.store.dirtyDates().isEmpty {
            pushLater?.cancel()
            let later = DispatchWorkItem { [weak self] in self?.syncNow() }
            pushLater = later
            DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: later)
        }
    }

    func show(_ message: String) {
        toast = message
        toastLater?.cancel()
        let later = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastLater = later
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.7, execute: later)
    }

    /// One exchange with the server: push what changed here, pull what changed elsewhere. Quiet
    /// unless [loud] - it runs on opening and every half minute, and a failure there is not worth
    /// a popup, since the marks are safe on the phone either way.
    func syncNow(loud: Bool = false) {
        guard let client else { return }
        guard !syncing else { return }
        let base: String, token: String
        do { (base, token) = try client.endpoint() } catch {
            if loud { show(error.localizedDescription) }
            return
        }
        syncing = true
        if loud { syncState = .working }
        let push = client.pushRequest()
        let pullBody = client.pullRequest()
        Task.detached { [weak self] in
            do {
                var pushed: [String: Any]? = nil
                if let push { pushed = try await client.post(base, "/push", token, push.body) }
                let pulled = try await client.post(base, "/pull", token, pullBody)
                await MainActor.run { self?.finished(push: push, pushed: pushed, pulled: pulled, loud: loud) }
            } catch {
                await MainActor.run { self?.failed(error, loud: loud) }
            }
        }
    }

    private func finished(push: (sent: [String: Int], body: [String: Any])?, pushed: [String: Any]?,
                          pulled: [String: Any], loud: Bool) {
        guard let client else { return }
        var changed = 0
        if let push, let pushed { changed += client.adoptPush(pushed, sent: push.sent) }
        changed += client.adoptPull(pulled)
        syncing = false
        syncState = .ok(at: Date(), changed: changed)
        session.afterSync(changed: changed)
        if loud { show(changed > 0 ? "Synced · \(changed) night\(changed == 1 ? "" : "s") updated" : "Synced") }
    }

    private func failed(_ error: Error, loud: Bool) {
        syncing = false
        syncState = .failed(reason: error.localizedDescription)
        if loud { show(error.localizedDescription) }
    }
}
