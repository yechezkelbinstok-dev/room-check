import Foundation
import RoomCheckCore

/// Fixed nights for the screenshots GitHub's Mac takes of every build, chosen by launching the
/// app with "-demo <name>". Each starts from an empty store in a scratch folder, on a pinned
/// evening, and never syncs - a screenshot run must not touch the yeshiva's real nights.
enum Demo {
    static let names = ["check-empty", "check-in-progress", "check-rooms-split", "check-hebrew", "check-thursday",
                        "check-past-night", "person-sheet", "night-picker", "review-everyone", "night-settings",
                        "settings", "names", "search", "whos-missing", "picture", "picture-hebrew"]

    static func model(_ name: String) -> AppModel {
        let home = FileManager.default.temporaryDirectory.appendingPathComponent("demo-\(UUID().uuidString)", isDirectory: true)
        let store = NightStore(home: home)
        let now = name == "check-thursday" ? date(2026, 10, 29, 23, 0) : date(2026, 10, 21, 22, 30)
        let session = NightSession(store: store, clock: { now })
        let sid = session.curSlot
        switch name {
        case "check-in-progress", "person-sheet":
            session.setMark("p2", sid, .inRoom)
            session.setMark("p1", sid, .out)
            session.toggleExcusedTonight("p3")
        case "check-rooms-split":
            let out: Set<String> = ["p1", "p5", "p6", "p9", "p10", "p11", "p13", "p14", "p15", "p16"]
            for i in 1...20 { session.setMark("p\(i)", sid, out.contains("p\(i)") ? .out : .inRoom) }
            for pid in ["p23", "p24", "p22"] { session.setMark(pid, sid, .inRoom) }
            session.setMark("p25", sid, .out)
            session.toggleAway("p31")
            session.jumpRoom(7)
        case "check-hebrew":
            session.updateSettings { $0.hebrewOnPlan = true }
            session.setMark("p9", sid, .inRoom)
            session.jumpRoom(2)
        case "check-thursday":
            session.setMark("p2", sid, .inRoom)
            session.setMark("p1", sid, .out)
        case "check-past-night", "review-everyone":
            session.markMissing(["p3", "p11"], sid)
            session.toggleAway("p31")
            session.shiftDay(1)
            session.shiftDay(-1)
            session.closeNight()
        case "night-settings":
            session.addRoundTonight("0130")
            session.toggleSheetSlot("1115")
        case "settings":
            session.updateSettings { $0.syncUrl = NightStore.SYNC_URL; $0.syncToken = NightStore.SYNC_TOKEN }
        case "names":
            session.toggleAway("p31")
            session.toggleAlwaysExcused("p11")
            session.setReason("p11", "Home sick")
        case "picture", "picture-hebrew":
            session.markMissing(["p3", "p11"], "1115")
            session.markMissing(["p11"], "1130")
            session.setMark("p7", "1200", .out)
            session.setMark("p8", "1200", .inRoom)
            session.toggleAway("p31")
            session.toggleExcusedTonight("p5")
            if name == "picture-hebrew" { session.updateSettings { $0.hebrewInExport = true } }
        default:
            break
        }
        let model = AppModel(session: session, client: nil)
        if name == "person-sheet" { model.demoPerson = "p1" }
        if name == "night-picker" { model.demoPicker = true }
        if name == "review-everyone" { model.demoEveryone = true }
        if name == "night-settings" { model.demoRoute = .night }
        if name == "settings" { model.demoRoute = .settings }
        if name == "names" { model.demoRoute = .names }
        if name == "search" { model.demoQuery = "Alt" }
        if name == "whos-missing" { model.demoRoute = .missing; model.demoPicked = ["p3", "p11"] }
        if name.hasPrefix("picture") { model.demoPicture = true }
        return model
    }

    private static func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
}
