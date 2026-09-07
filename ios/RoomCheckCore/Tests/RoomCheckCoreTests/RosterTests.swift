import XCTest
@testable import RoomCheckCore

/// The room plan is hand-transcribed geometry - the one place a typo (a duplicated bed slot, a
/// person left out, a door on a wall with no wall) would be silent until someone actually opened
/// the app and looked.
final class RosterTests: XCTestCase {
    func testThirtyPeopleThirtyIds() {
        XCTAssertEqual(Roster.PEOPLE.count, 30)
        XCTAssertEqual(Set(Roster.PEOPLE.map(\.id)).count, 30)
    }

    func testEightRooms() {
        XCTAssertEqual(Roster.PLAN.count, 8)
    }

    func testEveryPersonSleepsInExactlyOneBed() {
        let allSlots = Roster.PLAN.flatMap { $0.beds.flatMap(\.slots) }
        XCTAssertEqual(allSlots.count, 30, "expected 30 bed slots total, got \(allSlots.count)")
        XCTAssertEqual(Set(allSlots).count, 30, "a person appears in more than one bed")
        XCTAssertEqual(Set(allSlots), Set(Roster.PEOPLE.map(\.id)), "roster and room plan disagree on who exists")
    }

    func testRoomOfFindsEveryone() {
        for p in Roster.PEOPLE {
            XCTAssertNotNil(Roster.roomOf[p.id], "\(p.id) is not placed in any room")
        }
    }

    func testHebrewNamesAreEitherWholeOrAbsent() {
        // half-transliterated (Hebrew given name, blank surname or vice versa) is exactly the bug
        // NightLogic.hasHebrew guards against - if the data itself never does it, that guard is
        // dead code, so this pins the assumption it relies on.
        for p in Roster.PEOPLE {
            XCTAssertEqual(p.hebFirst.isEmpty, p.hebLast.isEmpty, "\(p.id) has only one Hebrew half")
        }
    }

    func testBedFootprintIsAlwaysTheSameSizeJustRotated() {
        for room in Roster.PLAN {
            for bed in room.beds {
                if bed.row {
                    XCTAssertEqual(bed.w, 60); XCTAssertEqual(bed.h, 32)
                } else {
                    XCTAssertEqual(bed.w, 32); XCTAssertEqual(bed.h, 60)
                }
            }
        }
    }

    /// Every bed has to actually fit inside its own room's stated width/height, or the plan draws
    /// a bed hanging out through a wall.
    func testEveryBedFitsInsideItsRoom() {
        for room in Roster.PLAN {
            for bed in room.beds {
                XCTAssertLessThanOrEqual(bed.x + bed.w, room.w, "\(room.id) bed runs past the right wall")
                XCTAssertLessThanOrEqual(bed.y + bed.h, room.h, "\(room.id) bed runs past the bottom wall")
                XCTAssertGreaterThanOrEqual(bed.x, 0); XCTAssertGreaterThanOrEqual(bed.y, 0)
            }
        }
    }
}
