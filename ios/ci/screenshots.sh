#!/bin/bash
# Builds the app for the iPhone simulator, opens it on each demo night (App/Demo.swift) and saves
# a screenshot of each - how the app gets looked at without a Mac of our own. Run from ios/.
set -euo pipefail

# the newest plain "iPhone NN" the runner has, on the newest iOS it has
UDID=$(xcrun simctl list devices available --json | python3 -c '
import json, re, sys
best = None
for runtime, devices in json.load(sys.stdin)["devices"].items():
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not m:
        continue
    for d in devices:
        n = re.fullmatch(r"iPhone (\d+)", d["name"])
        if n:
            key = (int(m.group(1)), int(m.group(2)), int(n.group(1)))
            if best is None or key > best[0]:
                best = (key, d["udid"], d["name"], runtime)
if best is None:
    sys.exit("no iPhone simulator found")
print(best[2], "on", best[3], file=sys.stderr)
print(best[1])
')

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b
# a fresh simulator covers the keyboard, the first time one opens, with a card about sliding to
# type; a phone that has been used never shows it
xcrun simctl spawn "$UDID" defaults write com.apple.keyboard.ContinuousPath KeyboardContinuousPathEnabled -bool false || true

xcodebuild -project RoomCheck.xcodeproj -scheme RoomCheck -configuration Debug \
  -destination "id=$UDID" -derivedDataPath build/sim "$@" build -quiet
xcrun simctl install "$UDID" build/sim/Build/Products/Debug-iphonesimulator/RoomCheck.app
xcrun simctl status_bar "$UDID" override --time "22:30" --batteryState charged --batteryLevel 100 \
  --wifiBars 3 --cellularMode active --cellularBars 4 || true

shoot() {   # demo name, file name
  xcrun simctl terminate "$UDID" com.roomcheck.app 2>/dev/null || true
  xcrun simctl launch "$UDID" com.roomcheck.app -demo "$1"
  sleep 5
  xcrun simctl io "$UDID" screenshot "out/shots/$2.png"
  echo "shot $2"
}

mkdir -p out/shots
xcrun simctl ui "$UDID" appearance light
# the first launch after installing can take longer than a shot waits; get it over with first
xcrun simctl launch "$UDID" com.roomcheck.app -demo check-empty
sleep 10
for demo in check-empty check-in-progress check-rooms-split check-hebrew check-thursday check-past-night \
            check-past-night-editing search-ring person-sheet night-picker review-everyone night-settings add-time \
            change-time settings sync-edit names rename search whos-missing picture picture-hebrew \
            room-{1..8} room-he-{1..8}; do
  shoot "$demo" "$demo"
done
# the largest ordinary text size in the phone's Settings, which the app's text follows
xcrun simctl ui "$UDID" content_size extra-extra-extra-large || true
for demo in check-in-progress person-sheet night-settings settings; do
  shoot "$demo" "$demo-large-text"
done
xcrun simctl ui "$UDID" content_size large || true
# the same app in dark mode, which it follows because every colour in it is the system's own
xcrun simctl ui "$UDID" appearance dark
for demo in check-in-progress check-past-night person-sheet add-time settings names; do
  shoot "$demo" "$demo-dark"
done
xcrun simctl ui "$UDID" appearance light
xcrun simctl terminate "$UDID" com.roomcheck.app 2>/dev/null || true

# the ones that need a finger: the menu open, a time swiped, the share sheet, the note after Copy
# (UITests/ScreenshotTests.swift). A failure there is reported but does not lose the shots above.
TEST_RUNNER_SHOTS_DIR="$PWD/out/shots" xcodebuild test -project RoomCheck.xcodeproj -scheme RoomCheck \
  -destination "id=$UDID" -derivedDataPath build/sim -only-testing:RoomCheckUITests "$@" -quiet \
  || echo "::warning::the screenshot UI tests failed"
# what was on the screen when a test did not find what it looked for
for tree in out/*-tree.txt; do
  if [ -f "$tree" ]; then echo "== $tree"; cat "$tree"; fi
done
ls out/shots
