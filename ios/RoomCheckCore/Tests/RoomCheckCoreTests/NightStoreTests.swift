import XCTest
@testable import RoomCheckCore

final class NightStoreTests: XCTestCase {
    private func tempHome() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    func testANightSurvivesASaveAndLoad() {
        let store = NightStore(home: tempHome())
        let n = Night()
        n.marks["1115"] = ["p1": .out]
        n.touch(Merge.markKey("1115", "p1"))
        store.save("2026-09-02", n)

        let back = store.load("2026-09-02")
        XCTAssertEqual(back.marks["1115"]?["p1"], .out)
    }

    func testAnUnwrittenNightIsBlankNotACrash() {
        let store = NightStore(home: tempHome())
        let n = store.load("2099-01-01")
        XCTAssertTrue(n.marks.isEmpty)
        XCTAssertFalse(n.closed)
    }

    func testSettingsAndExtraSurviveAcrossAFreshInstance() {
        let home = tempHome()
        let a = NightStore(home: home)
        a.settings.syncUrl = "https://example.com"
        a.settings.hebrewOnPlan = true
        a.extra["p1"] = PersonOverride(first: "Shloimy", always: true)
        // extra is exposed read-only from outside the type, so this exercises it the way the
        // view model actually does: mutate a copy via the same dictionary literal shape it stores
        a.saveState()

        let b = NightStore(home: home)
        XCTAssertEqual(b.settings.syncUrl, "https://example.com")
        XCTAssertTrue(b.settings.hebrewOnPlan)
        XCTAssertEqual(b.extra["p1"]?.first, "Shloimy")
        XCTAssertEqual(b.extra["p1"]?.always, true)
    }

    func testSaveLocalMarksTheNightDirtyExactlyOnce() {
        let store = NightStore(home: tempHome())
        XCTAssertTrue(store.dirtyDates().isEmpty)
        store.saveLocal("2026-09-02", Night())
        store.saveLocal("2026-09-02", Night())
        XCTAssertEqual(store.dirtyDates(), ["2026-09-02"])
    }

    func testClearDirtyOnlyClearsWhatWasPassed() {
        let store = NightStore(home: tempHome())
        store.saveLocal("2026-09-01", Night())
        store.saveLocal("2026-09-02", Night())
        store.clearDirty(["2026-09-01"])
        XCTAssertEqual(store.dirtyDates(), ["2026-09-02"])
    }

    func testBackupRoundTripsMarksAndOverrides() {
        let homeA = tempHome()
        let a = NightStore(home: homeA)
        let n = Night(); n.marks["1115"] = ["p3": .out]
        a.save("2026-09-02", n)
        a.extra["p3"] = PersonOverride(always: true, reason: "family emergency")
        a.saveState()

        let backup = a.exportBackup()
        XCTAssertTrue(backup.contains("dn:2026-09-02"))

        let b = NightStore(home: tempHome())
        XCTAssertTrue(b.importBackup(backup))
        XCTAssertEqual(b.load("2026-09-02").marks["1115"]?["p3"], .out)
        XCTAssertEqual(b.extra["p3"]?.reason, "family emergency")
        XCTAssertEqual(b.extra["p3"]?.always, true)
    }

    func testGarbageIsRejectedNotCrashed() {
        let store = NightStore(home: tempHome())
        XCTAssertFalse(store.importBackup("not json at all"))
        XCTAssertFalse(store.importBackup("{\"nothing\":\"relevant\"}"))
    }

    func testAllDateKeysListsOnlyWrittenNights() {
        let store = NightStore(home: tempHome())
        store.save("2026-09-01", Night())
        store.save("2026-09-02", Night())
        XCTAssertEqual(store.allDateKeys(), ["2026-09-01", "2026-09-02"])
    }
}
