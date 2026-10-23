import SwiftUI
import RoomCheckCore

/// Everything about one person, as a sheet of the phone's own rows and switches: their mark at
/// each of tonight's rounds, the room shortcuts, not in town and excused, and a note for tonight.
struct PersonSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let pid: String
    @State private var note = ""
    @State private var reason = ""

    var body: some View {
        let s = model.session
        let logic = s.logic
        let sid = s.curSlot
        let round = s.rounds.first { $0.id == sid }?.label ?? sid
        let room = Roster.roomOf[pid]
        let hebrew = s.store.settings.hebrewOnPlan
        let shared = room.map { r in r.beds.reduce(0) { $0 + $1.slots.count } > 1 } ?? false
        let locked = s.isLocked
        NavigationStack {
            List {
                Section {
                    ForEach(logic.slots, id: \.id) { slot in
                        let st = logic.statusOf(pid, slot.id)
                        HStack {
                            Text(slot.label)
                            Spacer()
                            switch st {
                            case .away: Text("Not in town").foregroundColor(.secondary)
                            case .exc: Text("Excused").foregroundColor(.secondary)
                            default:
                                MarkPair(status: st, size: 32) { s.setMark(pid, slot.id, $0) }
                                    .disabled(locked)
                            }
                        }
                    }
                } header: {
                    Text(room.map { hebrew ? $0.hebLabel : $0.label } ?? "")
                }

                if let room, shared, !locked, !logic.statusOf(pid, sid).offCheck {
                    Section {
                        Button {
                            s.setRoom(room, sid, nil, only: pid); dismiss()
                        } label: {
                            Label("Only him in the room", systemImage: "person.fill.checkmark")
                        }
                        Button {
                            s.setRoom(room, sid, nil, only: pid, onlyMark: .out); dismiss()
                        } label: {
                            Label("Only him missing", systemImage: "person.fill.xmark")
                        }
                    } header: {
                        Text("The \(round) round")
                    }
                }

                Section {
                    Toggle("Not in town", isOn: Binding(get: { logic.isAway(pid) }, set: { _ in s.toggleAway(pid) }))
                    Toggle("Excused tonight", isOn: Binding(get: { s.night.excusedTonight.contains(pid) },
                                                             set: { _ in s.toggleExcusedTonight(pid) }))
                    Toggle("Excused until turned off", isOn: Binding(get: { logic.isAlways(pid) },
                                                                      set: { _ in s.toggleAlwaysExcused(pid) }))
                    if logic.isAlways(pid) {
                        TextField("Reason (optional)", text: $reason)
                    }
                } footer: {
                    Text("Not in town and excused until turned off stay on every night, on every phone, until you switch them off.")
                }

                Section("Note for tonight") {
                    TextField("Note", text: $note, axis: .vertical)
                }
            }
            .navigationTitle(logic.nameOf(pid, hebrew: hebrew))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear {
            note = s.night.notes[pid] ?? ""
            reason = s.store.extra[pid]?.reason ?? ""
        }
        // saved as they are typed, like the Android app - but only a real change, so opening the
        // sheet does not stamp the note as freshly edited and send it round every phone again
        .onChange(of: note) { v in if v != (s.night.notes[pid] ?? "") { s.setNote(pid, v) } }
        .onChange(of: reason) { v in if v != (s.store.extra[pid]?.reason ?? "") { s.setReason(pid, v) } }
    }
}
