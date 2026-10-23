import XCTest
@testable import RoomCheckCore

/// The same cases as the Android app's RoundsTest, MoveRoundTest, AwayAndRoomTest, MarkMissingTest
/// and PeopleSyncTest - the editing rules both apps have to agree on - run against NightSession.
final class NightSessionTests: XCTestCase {

    private var home: URL!
    private var now = "2026-10-21T22:30"   // a Wednesday evening: tonight is a standing-three night

    private func date(_ s: String) -> Date {
        let p = s.split(whereSeparator: { "-T:".contains($0) }).compactMap { Int($0) }
        return Calendar.current.date(from: DateComponents(year: p[0], month: p[1], day: p[2], hour: p[3], minute: p[4]))!
    }

    private func session(at: String = "2026-10-21T22:30") -> NightSession {
        home = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        now = at
        return reopen()
    }
    /// The same storage opened again, as a second launch of the app sees it.
    private func reopen() -> NightSession {
        let t = date(now)
        return NightSession(store: NightStore(home: home), clock: { t })
    }

    private func labels(_ s: NightSession) -> [String] { s.rounds.map(\.label) }
    private let room8 = Roster.PLAN.first { $0.id == "r8" }!   // Fehler / Smith bunk, Levitansky

    // MARK: rounds

    func testOneCheckTonightAtItsOwnTime() {
        let s = session()
        s.onlyRoundTonight("1230")
        XCTAssertEqual(labels(s), ["12:30"])
        XCTAssertEqual(s.curSlot, "1230")
    }

    func testARoundCanBeDroppedFromTheUsualThree() {
        let s = session()
        s.removeRoundTonight("1115")
        XCTAssertEqual(labels(s), ["11:30", "12:00"])
    }

    func testDroppingARoundTakesItsMarksAndUndoBringsThemBack() {
        let s = session()
        s.setMark("p1", "1115", .out)
        s.removeRoundTonight("1115")
        XCTAssertNil(s.night.marks["1115"])
        s.undo()
        XCTAssertEqual(s.night.marks["1115"]?["p1"], .out)
    }

    func testANightsRoundsAreOnDisk() {
        let s = session()
        s.onlyRoundTonight("1230")
        s.setMark("p1", "1230", .out)
        let again = reopen()
        XCTAssertEqual(labels(again), ["12:30"])
        XCTAssertEqual(again.night.marks["1230"]?["p1"], .out)
    }

    func testAnotherNightIsUntouched() {
        let s = session()
        s.onlyRoundTonight("1230")
        s.shiftDay(-1)
        XCTAssertEqual(labels(s), ["11:15", "11:30", "12:00"])
        s.goToday()
        XCTAssertEqual(labels(s), ["12:30"])
    }

    func testThursdayNightIsTwoLateRounds() {
        let s = session(at: "2026-10-29T23:00")
        XCTAssertEqual(labels(s), ["12:45", "1:00"])
        XCTAssertEqual(s.curSlot, "1245")
        s.shiftDay(-1)
        XCTAssertEqual(labels(s), ["11:15", "11:30", "12:00"])
        // coming back to Thursday from a standing-three night lands on one of Thursday's
        s.goToday()
        XCTAssertEqual(s.curSlot, "1245")
        s.onlyRoundTonight("1230")
        s.resetRoundsTonight()
        XCTAssertEqual(labels(s), ["12:45", "1:00"])
    }

    func testMovingARoundTakesItsMarksAlong() {
        let s = session()
        s.setMark("p3", "1200", .out)
        s.selectSlot("1200")
        s.moveRoundTonight("1200", "1230")
        XCTAssertEqual(s.rounds.map(\.id), ["1115", "1130", "1230"])
        XCTAssertEqual(s.night.marks["1230"]?["p3"], .out)
        XCTAssertEqual(s.curSlot, "1230")
        s.moveRoundTonight("1230", "1130")   // already there: refused, nothing swallowed
        XCTAssertEqual(s.night.marks["1230"]?["p3"], .out)
    }

    func testThePictureCanBeNarrowedAndWidenedAgain() {
        let s = session()
        s.toggleSheetSlot("1115")
        s.toggleSheetSlot("1130")
        XCTAssertEqual(Slots.forSheet(s.night, dateKey: s.dateKey).map(\.label), ["12:00"])
        s.toggleSheetSlot("1115")
        s.toggleSheetSlot("1130")
        XCTAssertTrue(s.night.sheetSlots.isEmpty)
    }

    // MARK: marking

    func testTappingTheSameMarkTwiceClearsIt() {
        let s = session()
        s.setMark("p1", "1115", .inRoom)
        s.setMark("p1", "1115", .inRoom)
        XCTAssertNil(s.logic.statusOf("p1", "1115"))
    }

    func testAPastNightOpensForReadingUntilEdited() {
        let s = session()
        s.setMark("p1", "1115", .inRoom)
        s.shiftDay(1)
        s.shiftDay(-1)                   // tonight again: still for marking
        XCTAssertFalse(s.isLocked)
        s.shiftDay(-1)
        s.setEditing(true)
        s.setMark("p2", "1115", .out)
        s.closeNight()
        XCTAssertTrue(s.isLocked)
    }

    func testNamedPeopleAreOutAndEveryoneElseIsIn() {
        let s = session()
        s.toggleExcusedTonight("p5")
        s.markMissing(["p3", "p11"], "1115")
        let logic = s.logic
        XCTAssertEqual(logic.statusOf("p3", "1115"), .out)
        XCTAssertEqual(logic.statusOf("p5", "1115"), .exc)
        XCTAssertEqual(logic.statusOf("p1", "1115"), .inRoom)
        XCTAssertNil(logic.statusOf("p1", "1130"))
    }

    // MARK: not in town, and the whole room at once

    func testNotInTownCarriesToEveryRoundAndEveryNightUntilTurnedOff() {
        let s = session()
        s.toggleAway("p31")
        for sid in s.rounds.map(\.id) { XCTAssertEqual(s.logic.statusOf("p31", sid), .away) }
        s.shiftDay(1)
        XCTAssertEqual(s.logic.statusOf("p31", s.curSlot), .away)
        XCTAssertTrue(reopen().logic.isAway("p31"))
        s.backInTown("p31")
        XCTAssertNil(s.logic.statusOf("p31", s.curSlot))
    }

    func testNotInTownAndExcusedIndefinitelyAreOneOrTheOther() {
        let s = session()
        s.toggleAway("p31")
        s.toggleAlwaysExcused("p31")
        XCTAssertFalse(s.logic.isAway("p31"))
        XCTAssertEqual(s.logic.statusOf("p31", s.curSlot), .exc)
        s.toggleAway("p31")
        XCTAssertFalse(s.logic.isAlways("p31"))
    }

    func testRoomButtonsLeaveWhoeverIsNotInTownAlone() {
        let s = session()
        let sid = s.curSlot
        s.toggleAway("p31")
        s.setRoom(room8, sid, .inRoom)
        XCTAssertEqual(s.logic.statusOf("p29", sid), .inRoom)
        XCTAssertEqual(s.logic.statusOf("p31", sid), .away)
        XCTAssertTrue(s.logic.roomAllIn(room8, sid))
        s.setRoom(room8, sid, .out)
        XCTAssertEqual(s.logic.outIn(room8, sid), ["p29", "p28"])
        s.setRoom(room8, sid, nil)
        XCTAssertFalse(s.logic.roomChecked(room8, sid))
    }

    func testTheShareMissingCountsOnlyWhoIsOnTheWalk() {
        let s = session()
        let sid = s.curSlot
        s.setMark("p29", sid, .out)
        XCTAssertEqual(s.logic.missingShare(room8, sid), 1.0 / 3, accuracy: 0.001)
        s.toggleAway("p31")
        XCTAssertEqual(s.logic.missingShare(room8, sid), 0.5, accuracy: 0.001)
    }

    func testOnlyOneInTheRoomAndOnlyOneMissing() {
        let s = session()
        let sid = s.curSlot
        s.setRoom(room8, sid, nil, only: "p28")
        XCTAssertEqual(s.logic.statusOf("p28", sid), .inRoom)
        XCTAssertEqual(s.logic.statusOf("p29", sid), .out)
        s.setRoom(room8, sid, nil, only: "p28", onlyMark: .out)
        XCTAssertEqual(s.logic.statusOf("p28", sid), .out)
        XCTAssertEqual(s.logic.statusOf("p31", sid), .inRoom)
        // whichever way round, a bed off the walk gets no mark at all
        s.toggleAway("p31")
        s.setRoom(room8, sid, nil, only: "p28")
        XCTAssertEqual(s.logic.statusOf("p29", sid), .out)
        XCTAssertEqual(s.night.marks[sid]?["p31"], .inRoom, "the mark from before he went away is left as it was")
    }

    func testReportNamesWhoIsNotInTownApartFromWhoIsMissing() {
        let s = session()
        s.toggleAway("p31")
        XCTAssertTrue(s.logic.report(s.dateKey).hasSuffix("Not in town\nMordechai Shlomo Smith"))
    }

    // MARK: the shared people record

    func testSettingsAreWrittenIntoThePeopleRecordAndOweAPush() {
        let s = session()
        s.toggleAway("p31")
        s.toggleAlwaysExcused("p11")
        s.setReason("p11", "Home sick")
        s.renamePerson("p2", first: "Yossi", last: "")
        let store = NightStore(home: home)
        XCTAssertEqual(store.load(NightStore.PEOPLE_KEY).notes,
                       ["p31.away": "1", "p11.always": "1", "p11.reason": "Home sick", "p2.first": "Yossi"])
        XCTAssertTrue(store.dirtyDates().contains(NightStore.PEOPLE_KEY))
        XCTAssertEqual(store.extra["p2"]?.first, "Yossi")
        XCTAssertNil(store.extra["p2"]?.last)
        XCTAssertFalse(store.allDateKeys().contains(NightStore.PEOPLE_KEY))
    }

    func testARecordFromTheSiteIsReadTheSameWay() {
        let site = Night.fromJSONData(Data("""
            {"marks":{},"tonight":[],"notes":{"p31.away":"1","p9.first":"Avi","yeshiva.noSummer":"1"},
             "closed":false,"slots":[],"sheet":[],"ts":{"n:p31.away":5000,"n:p9.first":5000,"n:yeshiva.noSummer":5000}}
            """.utf8))
        let store = NightStore(home: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
        store.save(NightStore.PEOPLE_KEY, Merge.merge(store.load(NightStore.PEOPLE_KEY), site))
        store.rebuildExtra()
        XCTAssertEqual(store.extra["p31"]?.away, true)
        XCTAssertEqual(store.extra["p9"]?.first, "Avi")
        XCTAssertNil(store.extra["yeshiva"], "the yeshiva's own settings are nobody's")
        XCTAssertFalse(store.summerZman)
    }

    func testTurningOffElsewhereWinsOverAnOlderOnHere() {
        let store = NightStore(home: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
        store.setPerson("p31", "away", "1")
        let later = Night()
        later.touch(Merge.noteKey("p31.away"), at: Int64(Date().timeIntervalSince1970 * 1000) + 60_000)
        store.save(NightStore.PEOPLE_KEY, Merge.merge(store.load(NightStore.PEOPLE_KEY), later))
        store.rebuildExtra()
        XCTAssertNotEqual(store.extra["p31"]?.away, true)
    }

    func testADeviceFromBeforeStartsTheRecordFromItsOwnList() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data(#"{"extra":{"p11":{"always":true,"reason":"Home sick"}}}"#.utf8).write(to: dir.appendingPathComponent("state.json"))
        let store = NightStore(home: dir)
        let people = store.load(NightStore.PEOPLE_KEY)
        XCTAssertEqual(people.notes["p11.always"], "1")
        // as old as a stamp can be, so anything set anywhere since wins over it
        XCTAssertEqual(people.stamps[Merge.noteKey("p11.always")], 1)
        XCTAssertTrue(store.dirtyDates().contains(NightStore.PEOPLE_KEY))
        XCTAssertEqual(store.extra["p11"]?.always, true)
    }

    func testTheSummerSwitchIsSharedLikeEverythingElse() {
        let s = session()
        XCTAssertTrue(s.store.summerZman)
        s.setSummerZman(false)
        let store = NightStore(home: home)
        XCTAssertFalse(store.summerZman)
        XCTAssertEqual(store.load(NightStore.PEOPLE_KEY).notes[NightStore.NO_SUMMER], "1")
        XCTAssertNil(store.extra["yeshiva"])
        s.setSummerZman(true)
        XCTAssertTrue(NightStore(home: home).summerZman)
    }

    func testTheBuiltInServerIsFilledInOnlyWhereNoneWasEntered() {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let fresh = NightStore(home: dir, builtInSync: true)
        XCTAssertEqual(fresh.settings.syncUrl, NightStore.SYNC_URL)
        XCTAssertEqual(fresh.settings.syncToken, NightStore.SYNC_TOKEN)
        fresh.settings.syncUrl = "https://example.com"
        fresh.saveState()
        XCTAssertEqual(NightStore(home: dir, builtInSync: true).settings.syncUrl, "https://example.com")
        XCTAssertEqual(NightStore(home: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)).settings.syncUrl, "")
    }

    // MARK: search, and the time picker

    func testSearchLandsOnTheBedAndRingsIt() {
        let s = session()
        s.findPerson("p28")
        XCTAssertEqual(Roster.PLAN[s.room].id, "r8")
        XCTAssertEqual(s.highlight, "p28")
        s.jumpRoom(0)
        XCTAssertNil(s.highlight)
    }

    func testAClockPickerOpensOnTheRoundItIsChanging() {
        XCTAssertEqual(Slots.clockHourOf("1115"), 23)
        XCTAssertEqual(Slots.clockHourOf("1200"), 0)
        XCTAssertEqual(Slots.clockHourOf("0130"), 1)
        XCTAssertEqual(Slots.clockMinuteOf("0130"), 30)
        XCTAssertEqual(Slots.idForClock(hour24: Slots.clockHourOf("1245"), minute: 45), "1245")
    }
}
