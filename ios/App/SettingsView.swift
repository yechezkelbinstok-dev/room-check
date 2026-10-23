import SwiftUI
import UIKit
import UniformTypeIdentifiers
import RoomCheckCore

/// Everything that is a preference rather than a night, as the phone's own Settings lays it out.
/// The two Hebrew switches are separate on purpose: marking in Hebrew and sending in Hebrew are
/// different decisions.
struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editingSync = false
    @State private var url = ""
    @State private var token = ""
    @State private var importing = false

    var body: some View {
        let s = model.session
        let settings = s.store.settings
        Form {
            Section("Names") {
                Toggle("Hebrew on the plan", isOn: setting(\.hebrewOnPlan))
                Toggle("Hebrew in the sent picture", isOn: setting(\.hebrewInExport))
            }
            Section("Plan") {
                Toggle("Bunk labels", isOn: setting(\.bunkLabels))
            }
            Section {
                // once it is set up the address and password are never touched again, so they fold
                // away to one row; a form left open makes the finished state look unfinished
                if editingSync || settings.syncUrl.trimmingCharacters(in: .whitespaces).isEmpty {
                    TextField("Server address", text: $url)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Sync password", text: $token)
                    Button("Save") {
                        let u = url.trimmingCharacters(in: .whitespaces), t = token.trimmingCharacters(in: .whitespaces)
                        s.updateSettings { $0.syncUrl = u; $0.syncToken = t }
                        editingSync = false
                        model.syncNow(loud: true)
                    }
                } else {
                    Button {
                        url = settings.syncUrl
                        token = settings.syncToken
                        editingSync = true
                    } label: {
                        HStack {
                            Text("Server").foregroundColor(.primary)
                            Spacer()
                            Text(host(settings.syncUrl)).foregroundColor(.secondary).lineLimit(1)
                        }
                    }
                }
                Button { model.syncNow(loud: true) } label: {
                    HStack {
                        Text("Sync now")
                        Spacer()
                        Text(model.syncLine).foregroundColor(.secondary).lineLimit(1)
                    }
                }
            } header: {
                Text("Sync")
            } footer: {
                Text("The same server the Android app and the website use, so every phone sees every mark.")
            }
            Section("Roster") {
                NavigationLink("Names & rooms", value: Route.names)
            }
            Section("Backup") {
                ShareLink(item: BackupFile(store: s.store), preview: SharePreview("Room Check backup")) {
                    Text("Save a backup file")
                }
                Button("Restore from a file") { importing = true }
                Button("Restore from copied text") { restore(UIPasteboard.general.string ?? "") }
            }
            Section("App") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text(model.version).foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText, .text]) { result in
            guard case .success(let file) = result else { return }
            let open = file.startAccessingSecurityScopedResource()
            defer { if open { file.stopAccessingSecurityScopedResource() } }
            restore((try? String(contentsOf: file, encoding: .utf8)) ?? "")
        }
    }

    private func setting(_ key: WritableKeyPath<Settings, Bool>) -> Binding<Bool> {
        let s = model.session
        return Binding(get: { s.store.settings[keyPath: key] }, set: { v in s.updateSettings { $0[keyPath: key] = v } })
    }

    private func host(_ url: String) -> String {
        var h = url
        for prefix in ["https://", "http://"] where h.hasPrefix(prefix) { h.removeFirst(prefix.count) }
        while h.hasSuffix("/") { h.removeLast() }
        return h
    }

    private func restore(_ text: String) {
        let ok = model.session.store.importBackup(text)
        if ok { model.session.goToDate(model.session.dateKey) }
        model.show(ok ? "Restored" : "Not a backup")
    }
}

/// The backup as a file for the share sheet - written only when it is actually shared, in the
/// same shape the Android app and the website save, so any of them can restore it.
struct BackupFile: Transferable {
    let store: NightStore

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .json) { backup in
            let f = DateFormatter()
            f.dateFormat = "yyyy-MM-dd"
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("room-check-backup-\(f.string(from: Date())).json")
            try Data(backup.store.exportBackup().utf8).write(to: url)
            return SentTransferredFile(url)
        }
    }
}

/// One person's place in a room: their bed, and which half of it.
struct BedPlace: Identifiable {
    let id: String
    let label: String

    static func all(in room: Room) -> [BedPlace] {
        room.beds.flatMap { bed in
            bed.slots.enumerated().map { i, pid in
                BedPlace(id: pid, label: bed.slots.count > 1 ? (i == 0 ? "top bunk" : "bottom bunk") : "single bed")
            }
        }
    }
}

/// Everyone, room by room - with whoever is not in town or excused until turned off at the top,
/// so they are not forgotten - and the way to change how a name is written.
struct NamesView: View {
    @EnvironmentObject private var model: AppModel
    @State private var renaming: String?
    @State private var first = ""
    @State private var last = ""

    var body: some View {
        let s = model.session
        let logic = s.logic
        let away = Roster.PEOPLE.filter { logic.isAway($0.id) }
        let excused = Roster.PEOPLE.filter { logic.isAlways($0.id) }
        List {
            if !away.isEmpty {
                Section("Not in town") {
                    ForEach(away, id: \.id) { p in
                        row(p.id, logic, detail: roomLabel(p.id)) {
                            Button("Back in town") { s.backInTown(p.id) }.buttonStyle(.borderless)
                        }
                    }
                }
            }
            if !excused.isEmpty {
                Section("Excused until turned off") {
                    ForEach(excused, id: \.id) { p in
                        let reason = s.store.extra[p.id]?.reason
                        row(p.id, logic, detail: roomLabel(p.id) + (reason.map { " · \($0)" } ?? "")) {
                            Button("Turn back on") { s.unexcuse(p.id) }.buttonStyle(.borderless)
                        }
                    }
                }
            }
            ForEach(Roster.PLAN, id: \.id) { room in
                Section(room.label) {
                    ForEach(BedPlace.all(in: room)) { place in
                        let pid = place.id
                        Button {
                            first = logic.first(pid)
                            last = logic.last(pid)
                            renaming = pid
                        } label: {
                            row(pid, logic, detail: place.label) {
                                if logic.isAlways(pid) { Text("excused").foregroundColor(.secondary) }
                                else if logic.isAway(pid) { Text("not in town").foregroundColor(.secondary) }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Names & rooms")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Rename", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("First", text: $first)
            TextField("Last", text: $last)
            Button("Save") { if let pid = renaming { s.renamePerson(pid, first: first, last: last) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("How the name is written everywhere - on the plan, the text and the picture, on every phone.")
        }
    }

    private func roomLabel(_ pid: String) -> String { Roster.roomOf[pid]?.label ?? "" }

    private func row<Trailing: View>(_ pid: String, _ logic: NightLogic, detail: String,
                                     @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(logic.nameOf(pid)).foregroundColor(.primary)
                Text(detail).font(.footnote).foregroundColor(.secondary)
            }
            Spacer()
            trailing()
        }
    }
}
