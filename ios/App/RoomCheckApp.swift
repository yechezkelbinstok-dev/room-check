import SwiftUI

@main
struct RoomCheckApp: App {
    @StateObject private var model = AppModel.make()
    @Environment(\.scenePhase) private var phase
    // the same half-minute pull as the Android app, and only while the app is on screen
    private let tick = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some Scene {
        WindowGroup {
            CheckView()
                .environmentObject(model)
                .onAppear { model.syncNow() }
                .onChange(of: phase) { p in if p == .active { model.syncNow() } }
                .onReceive(tick) { _ in if phase == .active { model.syncNow() } }
        }
    }
}
