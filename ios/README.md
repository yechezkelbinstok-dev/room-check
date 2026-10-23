# Room Check for iPhone

Built on GitHub's Macs, so nobody here needs one. Every push that touches `ios/` runs
`.github/workflows/ios.yml`, which:

1. tests the shared code (`RoomCheckCore`, the same rules as the Android app, with the same cases),
2. builds the app,
3. opens it in the iPhone simulator on a few demo nights and takes screenshots,
4. puts the app file (`RoomCheck-<version>.ipa`) and the screenshots on the
   [ios-latest release](https://github.com/yechezkelbinstok-dev/room-check/releases/tag/ios-latest).

## Installing it

The `.ipa` is unsigned. An app that installs apps from outside the App Store signs it with your
own Apple ID on the way in:

- **AltStore** or **SideStore**, set up once from a computer. With a free Apple ID the app has to
  be refreshed every 7 days; AltStore does it over Wi-Fi while its computer program is running,
  SideStore on the phone itself.
- **Sideloadly** on a Windows PC or a Mac: plug the phone in and drop the `.ipa` on it.
- With a paid Apple developer account ($99 a year) it can go through **TestFlight** instead:
  installed from a link, no computer, a build lasting 90 days.

## What is where

- `RoomCheckCore/` - the shared code: nights, marks, merging, the Hebrew calendar, the roster,
  rounds, the people record, sync, and `NightSession`, which is the Android view model's editing
  rules. Builds and tests anywhere Swift runs, Linux included: `cd RoomCheckCore && swift test`.
- `App/` - the SwiftUI screens on top of it.
- `project.yml` - the Xcode project, turned into `RoomCheck.xcodeproj` by XcodeGen on the build.
- `ci/screenshots.sh` - the simulator run. The demo nights it opens are in `App/Demo.swift`; the
  shots that need a finger on the screen (a menu open, a row swiped, the share sheet) are
  `UITests/`.

## So far

Everything the Android app does but the yeshiva calendar: the marking screen (the night, the
rounds, the room buttons, the floor plan with here and not here, a whole room at once, only one in
or only one missing), a person's sheet (each round's marks, not in town, excused, a reason, a note),
search, Who's missing, the review (who was missing, or everyone's marks room by room), the night's
picture and text to send, night settings (tonight's times and which go on the picture), settings,
names and rooms, backups, and sync with the same server as the Android app and the website.
