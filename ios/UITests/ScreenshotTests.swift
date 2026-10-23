import XCTest

/// The screenshots that need a finger on the screen: the menu held open, a time swiped, the share
/// sheet, the note after Copy. ci/screenshots.sh runs these after the plain demo launches, and
/// each saves its picture in the folder the script names in SHOTS_DIR.
final class ScreenshotTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testMenu() {
        let app = launch("check-in-progress")
        app.buttons["More"].firstMatch.tap()
        sleep(1)
        shoot("menu")
    }

    @MainActor
    func testCopied() {
        let app = launch("check-in-progress")
        app.buttons["Copy"].firstMatch.tap()
        usleep(300_000)
        shoot("copied")
    }

    @MainActor
    func testSend() {
        let app = launch("check-in-progress")
        app.buttons["Send"].firstMatch.tap()
        sleep(3)
        shoot("send")
    }

    @MainActor
    func testSwipeATimeToRemoveIt() {
        let app = launch("night-settings")
        drag(time(app, "12:00"), from: 0.9, to: 0.6)
        shoot("night-settings-swipe-remove")
    }

    @MainActor
    func testSwipeATimeToMakeItTheOnlyOne() {
        let app = launch("night-settings")
        drag(time(app, "11:30"), from: 0.1, to: 0.4)
        shoot("night-settings-swipe-only")
    }

    // MARK: -

    @MainActor
    private func launch(_ demo: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demo", demo]
        app.launch()
        sleep(2)
        return app
    }

    /// A row of tonight's times - the button with the time and "Change" on it.
    @MainActor
    private func time(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5), "no row for \(label)")
        return row
    }

    /// Part of the way across, and let go: far enough to open the row's buttons, not so far that
    /// a full swipe does what the button does.
    @MainActor
    private func drag(_ row: XCUIElement, from: CGFloat, to: CGFloat) {
        let start = row.coordinate(withNormalizedOffset: CGVector(dx: from, dy: 0.5))
        let end = row.coordinate(withNormalizedOffset: CGVector(dx: to, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
        sleep(1)
    }

    @MainActor
    private func shoot(_ name: String) {
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SHOTS_DIR"] {
            let file = URL(fileURLWithPath: dir).appendingPathComponent("\(name).png")
            XCTAssertNoThrow(try shot.pngRepresentation.write(to: file))
        }
    }
}
