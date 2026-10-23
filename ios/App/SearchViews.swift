import SwiftUI
import UIKit
import RoomCheckCore

/// Whether a person answers to what was typed - in English or in Hebrew, first or last name.
private func matches(_ logic: NightLogic, _ pid: String, _ query: String) -> Bool {
    let q = query.trimmingCharacters(in: .whitespaces)
    if q.isEmpty { return true }
    return [logic.first(pid, hebrew: true), logic.last(pid, hebrew: true), logic.first(pid), logic.last(pid)]
        .contains { $0.localizedCaseInsensitiveContains(q) }
}

/// Type a name, tap it, and the plan turns to that bed with the person ringed - for when you are
/// holding a name and do not know the room.
struct SearchSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        let s = model.session
        let logic = s.logic
        let hebrew = s.store.settings.hebrewOnPlan
        let found = Roster.PEOPLE.filter { matches(logic, $0.id, query) }
        NavigationStack {
            List(found, id: \.id) { p in
                Button {
                    s.findPerson(p.id)
                    dismiss()
                } label: {
                    HStack {
                        Text(logic.nameOf(p.id, hebrew: hebrew)).foregroundColor(.primary)
                        Spacer()
                        Text(Roster.roomOf[p.id].map { hebrew ? $0.hebLabel : $0.label } ?? "")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .listStyle(.plain)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Name")
            .navigationTitle("Find someone")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .onAppear { if let q = model.demoQuery { query = q } }
    }
}

/// The whole round in one go: pick whoever is missing, everyone else is marked in. For the night
/// you already know - you were told three names - where thirty taps is the slow way round.
struct WhosMissingView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var picked: Set<String> = []

    var body: some View {
        let s = model.session
        let logic = s.logic
        let sid = s.curSlot
        let round = s.rounds.first { $0.id == sid }?.label ?? sid
        let hebrew = s.store.settings.hebrewOnPlan
        // whoever is excused or not in town is not on the list at all: their status already
        // overrides any mark, so picking one would be a tap that did nothing
        let roster = Roster.PEOPLE.filter { !logic.statusOf($0.id, sid).offCheck && matches(logic, $0.id, query) }
        List(roster, id: \.id) { p in
            let on = picked.contains(p.id)
            Button {
                if on { picked.remove(p.id) } else { picked.insert(p.id) }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: on ? "xmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundColor(on ? RC.red : Color(UIColor.tertiaryLabel))
                    Text(logic.nameOf(p.id, hebrew: hebrew))
                        .foregroundColor(on ? RC.red : .primary)
                        .fontWeight(on ? .semibold : .regular)
                    Spacer()
                    Text(Roster.roomOf[p.id].map { hebrew ? $0.hebLabel : $0.label } ?? "")
                        .foregroundColor(.secondary)
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, prompt: "Name")
        .navigationTitle("Who's missing · \(round)")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Button {
                s.markMissing(picked, sid)
                dismiss()
            } label: {
                Text(picked.isEmpty ? "Everyone in" : "\(picked.count) out, the rest in")
                    .fontWeight(.semibold).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(EdgeInsets(top: 10, leading: 16, bottom: 6, trailing: 16))
            .background(.bar)
            .overlay(alignment: .top) { Divider() }
        }
        .onAppear { if let demo = model.demoPicked { picked = demo } }
    }
}
