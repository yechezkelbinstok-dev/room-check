import XCTest
@testable import RoomCheckCore

final class NightLogicTests: XCTestCase {

    func testCopyTextFormat() {
        let n = Night()
        for sid in ["1115", "1130"] {
            for p in Roster.PEOPLE { n.marks[sid, default: [:]][p.id] = .inRoom }
        }
        for pid in ["p3", "p6", "p11", "p19", "p24", "p29"] { n.marks["1115"]?[pid] = .out }
        n.marks["1115"]?["p8"] = .exc
        for pid in ["p13", "p24", "p29"] { n.marks["1130"]?[pid] = .out }

        let logic = NightLogic(n, [:])
        let body = logic.report("2026-09-02").split(separator: "\n", omittingEmptySubsequences: false).dropFirst().joined(separator: "\n")
        XCTAssertEqual(body, """

        11:15
        Menachem Mendel Piekarski, Menachem Mendel Stolik, Asher Wolfe, Menachem Mendel HaLevi Flint, Leib Meir November, Yehuda Fehler

        11:30
        Dovid Altein, Leib Meir November, Yehuda Fehler

        12:00
        Not marked
        """)
    }

    func testUnwalkedRoomsAreCalledOut() {
        let n = Night()
        n.marks["1115"] = ["p1": .inRoom]
        let logic = NightLogic(n, [:])
        XCTAssertTrue(logic.report("2026-09-02").contains("(still to mark: Room 2, Room 3,"))
    }

    func testAlwaysExcusedOverridesEverything() {
        let n = Night()
        n.marks["1115"] = ["p1": .out]
        let logic = NightLogic(n, ["p1": PersonOverride(always: true)])
        XCTAssertEqual(logic.statusOf("p1", "1115"), .exc)
    }

    func testExcusedTonightClearsAcrossAllRounds() {
        let n = Night()
        n.excusedTonight = ["p1"]
        let logic = NightLogic(n, [:])
        XCTAssertEqual(logic.statusOf("p1", "1115"), .exc)
        XCTAssertEqual(logic.statusOf("p1", "1200"), .exc)
    }

    func testHebrewNameFallsBackWhenHalfMissing() {
        // p1 (Shlomo Altein) has full Hebrew; nobody in the roster has a half-filled entry, so this
        // pins the fallback path using a deliberately incomplete override instead
        let n = Night()
        let logic = NightLogic(n, [:])
        XCTAssertEqual(logic.nameOf("p1", hebrew: true), "שלמה אלטיין")
        XCTAssertEqual(logic.nameOf("p1", hebrew: false), "Shlomo Altein")
    }
}
