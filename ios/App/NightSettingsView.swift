import SwiftUI
import RoomCheckCore

/// A time being added to tonight, or one of tonight's being moved.
enum TimeEdit: Identifiable {
    case add
    case change(String)
    var id: String {
        switch self {
        case .add: return "add"
        case .change(let sid): return sid
        }
    }
}

/// The rounds walked on ONE night, and which of them the sent picture shows. A time changed here
/// belongs to this night alone - the zman moved, or you went round again at 1:30 - and the next
/// night opens on its usual times with nothing to put back.
struct NightSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editing: TimeEdit?

    var body: some View {
        let s = model.session
        let all = s.rounds
        let onSheet = Set(Slots.forSheet(s.night, dateKey: s.dateKey).map(\.id))
        List {
            Section {
                ForEach(all, id: \.id) { slot in
                    Button { editing = .change(slot.id) } label: {
                        HStack {
                            Text(slot.label).foregroundColor(.primary)
                            Spacer()
                            Text("Change").foregroundColor(.secondary)
                        }
                    }
                    .swipeActions(edge: .trailing) {
                        if all.count > 1 {
                            Button(role: .destructive) { s.removeRoundTonight(slot.id) } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                    }
                    .swipeActions(edge: .leading) {
                        if all.count > 1 {
                            Button { s.onlyRoundTonight(slot.id) } label: {
                                Label("Only this", systemImage: "1.circle")
                            }
                            .tint(.accentColor)
                        }
                    }
                }
                Button { editing = .add } label: { Label("Add a time", systemImage: "plus") }
                if !s.night.rounds.isEmpty {
                    Button { s.resetRoundsTonight() } label: {
                        Label("Back to the usual times", systemImage: "arrow.counterclockwise")
                    }
                }
            } header: {
                Text("Times")
            } footer: {
                Text("Swipe a time left to remove it, or right to make it the night's only one. Changes here are this night's alone.")
            }

            Section("On the sent picture") {
                ForEach(all, id: \.id) { slot in
                    Toggle(slot.label, isOn: Binding(get: { onSheet.contains(slot.id) },
                                                     set: { _ in s.toggleSheetSlot(slot.id) }))
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Night settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text("Night settings").font(.headline)
                    Text(Dates.hebrewDate(s.dateKey)).font(.caption).foregroundColor(.secondary)
                }
            }
        }
        .sheet(item: $editing) { e in
            TimeSheet(edit: e).environmentObject(model)
        }
        .onAppear {
            // the screenshot run's time wheel, once this page has finished sliding in
            if let e = model.demoTimeEdit {
                model.demoTimeEdit = nil
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { editing = e }
            }
        }
    }
}

/// The phone's own time wheel, opened on the round being changed - or 11:30 for a new one.
struct TimeSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let edit: TimeEdit
    @State private var time = Date()

    var body: some View {
        NavigationStack {
            DatePicker("Time", selection: $time, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .navigationTitle(isAdding ? "Add a time" : "Change the time")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(isAdding ? "Add" : "Change") { save(); dismiss() }.fontWeight(.semibold)
                    }
                }
        }
        .presentationDetents([.medium])
        .onAppear {
            let at: String
            switch edit {
            case .add: at = "1130"
            case .change(let sid): at = sid
            }
            time = Calendar.current.date(bySettingHour: Slots.clockHourOf(at), minute: Slots.clockMinuteOf(at),
                                         second: 0, of: Date()) ?? Date()
        }
    }

    private var isAdding: Bool {
        if case .add = edit { return true }
        return false
    }

    private func save() {
        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
        let id = Slots.idForClock(hour24: c.hour ?? 0, minute: c.minute ?? 0)
        switch edit {
        case .add: model.session.addRoundTonight(id)
        case .change(let from): model.session.moveRoundTonight(from, id)
        }
    }
}
