import XCTest
@testable import RoomCheckCore

/// Same cases as the Kotlin SlotsTest - a night's own rounds replace the standing three rather
/// than adding to them, and ids read like a clock face rather than sorting as plain numbers.
final class SlotsTests: XCTestCase {

    private func night(_ only: String...) -> Night {
        let n = Night(); n.rounds = Set(only); return n
    }

    func testTheUsualNightIsTheStandingThree() {
        XCTAssertEqual(Slots.all(Night()).map(\.label), ["11:15", "11:30", "12:00"])
    }

    func testANightCanBeOneRoundAtItsOwnTime() {
        XCTAssertEqual(Slots.all(night("1230")).map(\.label), ["12:30"])
    }

    func testANightCanBeTwoRounds() {
        XCTAssertEqual(Slots.all(night("1230", "0130")).map(\.label), ["12:30", "1:30"])
    }

    func testMidnightSitsBetweenElevenAndOne() {
        XCTAssertEqual(
            Slots.all(night("0130", "1045", "1115", "1200")).map(\.label),
            ["10:45", "11:15", "12:00", "1:30"]
        )
    }

    func testRoundsBelongToTheirOwnNightOnly() {
        XCTAssertEqual(Slots.all(night("1230")).map(\.label), ["12:30"])
        XCTAssertEqual(Slots.all(Night()).map(\.label), ["11:15", "11:30", "12:00"])
    }

    func testANightsRoundsSurviveASaveAndLoad() {
        let n = night("1230")
        n.marks["1230"] = ["p1": .out]
        let back = Night.fromJSONObject(n.toJSONObject())
        XCTAssertEqual(Slots.all(back).map(\.label), ["12:30"])
        XCTAssertEqual(back.marks["1230"]?["p1"], .out)
    }

    func testARoundWithMarksInItCountsWithoutBeingDeclared() {
        let n = Night()
        n.marks["0130"] = ["p1": .out]
        XCTAssertEqual(Slots.all(n).map(\.label), ["11:15", "11:30", "12:00", "1:30"])
    }

    func testAnEmptySlotMapIsNotARound() {
        let n = Night()
        n.marks["0130"] = [:]
        XCTAssertEqual(Slots.all(n).map(\.label), ["11:15", "11:30", "12:00"])
    }

    func testTheClockMapsOntoTheRightRound() {
        XCTAssertEqual(Slots.idForClock(hour24: 23, minute: 20), "1120")
        XCTAssertEqual(Slots.idForClock(hour24: 0, minute: 30), "1230")
        XCTAssertEqual(Slots.idForClock(hour24: 1, minute: 40), "0140")
        XCTAssertTrue(Slots.order(Slots.idForClock(hour24: 23, minute: 20)) > Slots.order("1115"))
        XCTAssertTrue(Slots.order(Slots.idForClock(hour24: 0, minute: 30)) > Slots.order("1200"))
        XCTAssertTrue(Slots.order(Slots.idForClock(hour24: 1, minute: 40)) > Slots.order("0130"))
    }

    func testLabelsReadAsAClockDoes() {
        XCTAssertEqual(Slots.labelFor("0130"), "1:30")
        XCTAssertEqual(Slots.labelFor("1200"), "12:00")
        XCTAssertEqual(Slots.labelFor("1045"), "10:45")
    }

    func testRubbishTimesAreRefused() {
        for id in ["2460", "99", "abcd", "1300", "1160", "0060"] {
            XCTAssertFalse(Slots.isValidId(id), id)
        }
        XCTAssertTrue(Slots.isValidId("0130"))
    }

    func testTheSheetCanBeNarrowedToOneTime() {
        let n = Night(); n.sheetSlots = ["1200"]
        XCTAssertEqual(Slots.forSheet(n).map(\.label), ["12:00"])
    }

    func testTheSheetCanBeNarrowedToTwo() {
        let n = night("1115", "1200", "0130"); n.sheetSlots = ["1200", "0130"]
        XCTAssertEqual(Slots.forSheet(n).map(\.label), ["12:00", "1:30"])
    }

    func testNarrowingTheSheetIsAlsoJustThatNight() {
        let n = night("0130"); n.sheetSlots = ["0130"]
        _ = n
        XCTAssertEqual(Slots.forSheet(Night()).map(\.label), ["11:15", "11:30", "12:00"])
    }

    func testNarrowingToATimeThatNoLongerExistsFallsBackToAll() {
        let n = Night(); n.sheetSlots = ["0130"]
        XCTAssertEqual(Slots.forSheet(n).map(\.label), ["11:15", "11:30", "12:00"])
    }

    func testARoundWithMarksInItKeepsItsColumn() {
        let n = night("1230")
        n.marks["0130"] = ["p1": .out]
        XCTAssertEqual(Slots.all(n).map(\.label), ["12:30", "1:30"])
    }
}
