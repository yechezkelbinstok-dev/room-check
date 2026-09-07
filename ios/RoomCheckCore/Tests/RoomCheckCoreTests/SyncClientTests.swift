import XCTest
@testable import RoomCheckCore

final class SyncClientTests: XCTestCase {
    private func tempStore() -> NightStore {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        return NightStore(home: dir)
    }

    func testNoAddressFailsImmediately() async {
        let store = tempStore()
        let client = SyncClient(store: store, session: makeMockedSession(MockSyncServer()))
        do {
            _ = try await client.sync()
            XCTFail("expected an error")
        } catch SyncError.noAddress {
            // expected
        } catch { XCTFail("wrong error: \(error)") }
    }

    func testWrongPasswordIsReportedNotSwallowed() async {
        let store = tempStore()
        store.settings.syncUrl = "http://mock"
        store.settings.syncToken = "wrong"
        let server = MockSyncServer(); server.token = "secret"
        let client = SyncClient(store: store, session: makeMockedSession(server))
        do {
            _ = try await client.sync()
            XCTFail("expected an error")
        } catch SyncError.wrongPassword {
            // expected
        } catch { XCTFail("wrong error: \(error)") }
    }

    func testServerErrorIsReported() async {
        let store = tempStore()
        store.settings.syncUrl = "http://mock"
        let server = MockSyncServer(); server.forceStatus = 500
        let client = SyncClient(store: store, session: makeMockedSession(server))
        do {
            _ = try await client.sync()
            XCTFail("expected an error")
        } catch SyncError.serverStatus(500) {
            // expected
        } catch { XCTFail("wrong error: \(error)") }
    }

    /// The whole point of sync: two devices marking different rooms on the same night both keep
    /// their marks after a round trip through the server.
    func testTwoDevicesMarkingDifferentRoomsBothKeepTheirMarks() async throws {
        let server = MockSyncServer()

        let a = tempStore(); a.settings.syncUrl = "http://mock"; a.settings.syncToken = "secret"
        let n1 = Night(); n1.marks["1115"] = ["p1": .inRoom]; n1.touch(Merge.markKey("1115", "p1"))
        a.saveLocal("2026-09-02", n1)
        _ = try await SyncClient(store: a, session: makeMockedSession(server)).sync()

        let b = tempStore(); b.settings.syncUrl = "http://mock"; b.settings.syncToken = "secret"
        let n2 = Night(); n2.marks["1115"] = ["p20": .out]; n2.touch(Merge.markKey("1115", "p20"))
        b.saveLocal("2026-09-02", n2)
        _ = try await SyncClient(store: b, session: makeMockedSession(server)).sync()

        // A pulls again and should now see B's mark alongside its own
        _ = try await SyncClient(store: a, session: makeMockedSession(server)).sync()
        let merged = a.load("2026-09-02")
        XCTAssertEqual(merged.marks["1115"]?["p1"], .inRoom, "A lost its own mark")
        XCTAssertEqual(merged.marks["1115"]?["p20"], .out, "A never got B's mark")
    }

    func testARoundAddedOnOneDeviceReachesTheOther() async throws {
        let server = MockSyncServer()

        let a = tempStore(); a.settings.syncUrl = "http://mock"; a.settings.syncToken = "secret"
        let n = Night(); n.rounds = ["1230"]; n.touch(Merge.slotKey("1230"))
        n.marks["1230"] = ["p5": .out]; n.touch(Merge.markKey("1230", "p5"))
        a.saveLocal("2026-09-02", n)
        _ = try await SyncClient(store: a, session: makeMockedSession(server)).sync()

        let b = tempStore(); b.settings.syncUrl = "http://mock"; b.settings.syncToken = "secret"
        _ = try await SyncClient(store: b, session: makeMockedSession(server)).sync()

        let seen = b.load("2026-09-02")
        XCTAssertEqual(Slots.all(seen).map(\.label), ["12:30"])
        XCTAssertEqual(seen.marks["1230"]?["p5"], .out)
    }

    func testChangedCountReflectsWhatActuallyMoved() async throws {
        let server = MockSyncServer()
        let a = tempStore(); a.settings.syncUrl = "http://mock"; a.settings.syncToken = "secret"
        // nothing dirty, nothing on the server: a sync with nothing to do reports zero
        let changed = try await SyncClient(store: a, session: makeMockedSession(server)).sync()
        XCTAssertEqual(changed, 0)
    }

    func testDirtyNightsAreClearedAfterASuccessfulPush() async throws {
        let server = MockSyncServer()
        let a = tempStore(); a.settings.syncUrl = "http://mock"; a.settings.syncToken = "secret"
        a.saveLocal("2026-09-02", Night())
        XCTAssertEqual(a.dirtyDates(), ["2026-09-02"])
        _ = try await SyncClient(store: a, session: makeMockedSession(server)).sync()
        XCTAssertTrue(a.dirtyDates().isEmpty)
    }
}
