import SwiftUI
import UIKit
import RoomCheckCore

/// A night already walked, the way Settings lists things - either who was missing at each round,
/// or everyone room by room with their mark at every round, like the Android app's review.
struct ReviewList: View {
    @EnvironmentObject private var model: AppModel
    let logic: NightLogic
    @State private var everyone = false

    var body: some View {
        List {
            Section {
                Picker("Show", selection: $everyone) {
                    Text("Who was missing").tag(false)
                    Text("Everyone").tag(true)
                }
                .pickerStyle(.segmented)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
            if everyone { grid } else { summary }
        }
        .listStyle(.insetGrouped)
        .onAppear { if model.demoEveryone { everyone = true } }
    }

    private var hebrew: Bool { model.session.store.settings.hebrewOnPlan }

    private func roomName(_ pid: String) -> String {
        Roster.roomOf[pid].map { hebrew ? $0.hebLabel : $0.label } ?? ""
    }

    // MARK: who was missing

    @ViewBuilder private var summary: some View {
        ForEach(logic.slots, id: \.id) { slot in
            let missing = logic.missingAt(slot.id)
            let started = logic.stats(slot.id).started
            Section {
                if missing.isEmpty {
                    Label(started ? "Everybody there" : "Not marked",
                          systemImage: started ? "checkmark.circle.fill" : "circle.dashed")
                        .foregroundColor(started ? RC.green : .secondary)
                }
                ForEach(missing, id: \.self) { pid in
                    HStack {
                        Text(logic.nameOf(pid, hebrew: hebrew))
                        Spacer()
                        Text(roomName(pid)).foregroundColor(.secondary)
                    }
                }
            } header: {
                HStack {
                    Text(slot.label)
                    Spacer()
                    if !missing.isEmpty { Text("\(missing.count) out").foregroundColor(RC.red) }
                }
            }
        }
        let away = logic.awayList()
        if !away.isEmpty {
            Section("Not in town") {
                ForEach(away, id: \.self) { pid in
                    HStack {
                        Text(logic.nameOf(pid, hebrew: hebrew))
                        Spacer()
                        Text(roomName(pid)).foregroundColor(.secondary)
                    }
                }
            }
        }
    }

    // MARK: everyone

    @ViewBuilder private var grid: some View {
        ForEach(Roster.PLAN, id: \.id) { room in
            Section {
                ForEach(room.beds.flatMap(\.slots), id: \.self) { pid in
                    HStack(spacing: 0) {
                        Text(logic.nameOf(pid, hebrew: hebrew)).lineLimit(1).minimumScaleFactor(0.8)
                        Spacer(minLength: 8)
                        ForEach(logic.slots, id: \.id) { slot in
                            glyph(logic.statusOf(pid, slot.id)).frame(width: 44)
                        }
                    }
                }
            } header: {
                HStack(spacing: 0) {
                    Text(hebrew ? room.hebLabel : room.label)
                    Spacer()
                    ForEach(logic.slots, id: \.id) { slot in
                        Text(slot.label).frame(width: 44)
                    }
                }
            }
        }
        Section {
        } footer: {
            Text("✓ here · ✕ not here · \(hebrew ? "פ׳" : "E") excused · ✈︎ not in town · – not marked")
        }
    }

    @ViewBuilder private func glyph(_ mark: Mark?) -> some View {
        switch mark {
        case .inRoom: Image(systemName: "checkmark").font(.body.weight(.bold)).foregroundColor(RC.green)
        case .out: Image(systemName: "xmark").font(.body.weight(.bold)).foregroundColor(RC.red)
        case .exc: Text(hebrew ? "פ׳" : "E").font(.body.weight(.bold)).foregroundColor(.secondary)
        case .away: Image(systemName: "airplane").foregroundColor(.accentColor)
        case nil: Text("–").foregroundColor(Color(UIColor.tertiaryLabel))
        }
    }
}
