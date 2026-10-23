import SwiftUI
import UIKit
import RoomCheckCore

/// Someone tapped on the plan, for the sheet of what can be done about him.
struct PersonRef: Identifiable { let id: String }

/// The screens the marking screen opens onto, the way Settings opens onto its pages.
enum Route: Hashable { case night, settings, names, missing }

/// The marking screen, built from Apple's own pieces: the night as the navigation bar's title,
/// with ‹ › either side and a calendar behind it; the rounds as a segmented control; the rooms as
/// buttons over pages you swipe between; and a bottom bar like the system's with Copy, Send and
/// Save. A past night that was marked opens as its summary, until you say you are editing it.
struct CheckView: View {
    @EnvironmentObject private var model: AppModel
    @State private var person: PersonRef?
    @State private var pickingNight = false
    @State private var searching = false
    @State private var showingPicture = false
    @State private var path = NavigationPath()

    private var s: NightSession { model.session }

    var body: some View {
        let logic = s.logic
        let review = s.showingReview
        NavigationStack(path: $path) {
            Group {
                if review {
                    ReviewList(logic: logic)
                } else {
                    marking(logic)
                }
            }
            .background(RC.page.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar(review: review) }
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomBar(logic, review: review) }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .night: NightSettingsView()
                case .settings: SettingsView()
                case .names: NamesView()
                case .missing: WhosMissingView()
                }
            }
        }
        .overlay(alignment: .bottom) { toast }
        .sheet(item: $person) { p in
            PersonSheet(pid: p.id).environmentObject(model)
        }
        .sheet(isPresented: $pickingNight) {
            NightPicker().environmentObject(model)
        }
        .sheet(isPresented: $searching) {
            SearchSheet().environmentObject(model)
        }
        .fullScreenCover(isPresented: $showingPicture) {
            PicturePreview(image: NightPictureFile(session: s).render())
        }
        // whoever search landed on is ringed for a moment, then the ring clears itself
        .onChange(of: s.highlight) { pid in
            guard pid != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.5) { s.clearHighlight() }
        }
        .onAppear {
            if let pid = model.demoPerson { person = PersonRef(id: pid) }
            if model.demoPicker { pickingNight = true }
            if let route = model.demoRoute { path.append(route) }
            if model.demoQuery != nil { searching = true }
            if model.demoPicture { showingPicture = true }
        }
    }

    // MARK: - the navigation bar

    @ToolbarContentBuilder
    private func toolbar(review: Bool) -> some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            if s.dateKey != s.tonight() { Button("Tonight") { s.goToday() } }
        }
        ToolbarItem(placement: .principal) {
            HStack(spacing: 14) {
                Button { s.shiftDay(-1) } label: { Image(systemName: "chevron.left").font(.body.weight(.semibold)) }
                    .accessibilityLabel("Night before")
                Button { pickingNight = true } label: {
                    VStack(spacing: 0) {
                        Text(Dates.hebrewDate(s.dateKey)).font(.headline).foregroundColor(.primary)
                        Text(Dates.longDate(s.dateKey)).font(.caption).foregroundColor(.secondary)
                    }
                    .lineLimit(1)
                }
                .buttonStyle(.plain)
                Button { s.shiftDay(1) } label: { Image(systemName: "chevron.right").font(.body.weight(.semibold)) }
                    .accessibilityLabel("Night after")
            }
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            if review {
                if s.isLocked { Button("Edit") { s.setEditing(true) } }
                else { Button("Done") { s.setReviewing(false) }.fontWeight(.semibold) }
            } else {
                Button { s.undo() } label: { Image(systemName: "arrow.uturn.backward") }
                    .accessibilityLabel("Undo")
            }
            Menu {
                Section {
                    if !review {
                        // most nights are their usual rounds and never need these, but the night
                        // that does is this night
                        Button { path.append(Route.missing) } label: { Label("Who's missing", systemImage: "person.fill.questionmark") }
                        Button { s.setReviewing(true) } label: { Label("This night's summary", systemImage: "list.bullet.rectangle") }
                    }
                    Button { path.append(Route.night) } label: { Label("Night settings", systemImage: "clock") }
                }
                Section {
                    Button { model.syncNow(loud: true) } label: { Label("Sync now", systemImage: "arrow.triangle.2.circlepath") }
                    Toggle(isOn: Binding(get: { s.store.settings.hebrewOnPlan },
                                         set: { on in s.updateSettings { $0.hebrewOnPlan = on } })) {
                        Label("Hebrew names", systemImage: "character.book.closed")
                    }
                    Button { path.append(Route.settings) } label: { Label("Settings", systemImage: "gear") }
                }
                Section {
                    Text(model.syncLine)
                    Text("Version \(model.version)")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
    }

    // MARK: - marking

    private func marking(_ logic: NightLogic) -> some View {
        VStack(spacing: 12) {
            rounds(logic)
            roomButtons(logic)
            // one page per room, swiped between like any page on the phone; the buttons above
            // turn the same pages
            TabView(selection: Binding(get: { s.room }, set: { s.jumpRoom($0) })) {
                ForEach(Roster.PLAN.indices, id: \.self) { i in
                    RoomCard(index: i, logic: logic) { person = PersonRef(id: $0) }
                        .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
        .padding(.top, 8)
    }

    /// The night's rounds as Apple's segmented control, with each one's count lined up under it.
    private func rounds(_ logic: NightLogic) -> some View {
        VStack(spacing: 5) {
            Picker("Round", selection: Binding(get: { s.curSlot }, set: { s.selectSlot($0) })) {
                ForEach(logic.slots, id: \.id) { Text($0.label).tag($0.id) }
            }
            .pickerStyle(.segmented)
            HStack(spacing: 0) {
                ForEach(logic.slots, id: \.id) { slot in
                    let line = Self.statLine(logic.stats(slot.id))
                    Text(line.text)
                        .font(.caption2.weight(line.bold ? .semibold : .regular))
                        .foregroundColor(line.color)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    /// Under each round: how many out, all in, how many rooms left, or a dash before it starts.
    private static func statLine(_ st: SlotStats) -> (text: String, color: Color, bold: Bool) {
        if st.out > 0 { return ("\(st.out) out", RC.red, true) }
        if !st.started { return ("—", .secondary, false) }
        if st.left == 0 { return ("all in", RC.green, true) }
        return ("\(st.left) left", .secondary, false)
    }

    /// One row per floor, rooms 1 to 3 on top. Every row is cut into as many columns as the
    /// fullest floor has rooms, so a number sits in the same place on both.
    private func roomButtons(_ logic: NightLogic) -> some View {
        let cols = Roster.FLOORS.map(\.count).max() ?? 5
        return HStack(spacing: 8) {
            VStack(spacing: 6) {
                ForEach(0..<Roster.FLOORS.count, id: \.self) { f in
                    HStack(spacing: 6) {
                        ForEach(Roster.FLOORS[f], id: \.self) { i in roomButton(i, logic) }
                        ForEach(0..<(cols - Roster.FLOORS[f].count), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity).frame(height: 34)
                        }
                    }
                }
            }
            // for when you are holding a name and do not know the room
            Button { searching = true } label: {
                Image(systemName: "magnifyingglass")
                    .font(.body.weight(.semibold))
                    .frame(width: 40, height: 74)
                    .background(RC.card, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
            .accessibilityLabel("Find someone")
        }
        .padding(.horizontal, 16)
    }

    private func roomButton(_ i: Int, _ logic: NightLogic) -> some View {
        let room = Roster.PLAN[i]
        let sid = s.curSlot
        let selected = i == s.room
        let out = logic.outIn(room, sid).count
        let checked = logic.roomChecked(room, sid)
        var fg = Color.primary
        if selected { fg = .white } else if out > 0 { fg = RC.red } else if checked { fg = RC.green }
        // split by its marks: red across the share missing, green across the share in, the
        // card's own colour across whoever is not marked yet
        let red = logic.missingShare(room, sid), green = logic.inShare(room, sid)
        let split = !selected && (red > 0 || green > 0)
        let shape = RoundedRectangle(cornerRadius: 9, style: .continuous)
        return Button {
            withAnimation(.easeInOut(duration: 0.25)) { s.jumpRoom(i) }
        } label: {
            Text("\(i + 1)").font(.subheadline.weight(.semibold)).foregroundColor(fg)
                .frame(maxWidth: .infinity).frame(height: 34)
                .background {
                    if selected {
                        shape.fill(Color.accentColor)
                    } else {
                        GeometryReader { g in
                            HStack(spacing: 0) {
                                if split {
                                    RC.red.opacity(0.22).frame(width: g.size.width * red)
                                    RC.green.opacity(0.22).frame(width: g.size.width * green)
                                }
                                RC.card
                            }
                        }
                        .clipShape(shape)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(room.label)
    }

    // MARK: - the bottom bar

    private func bottomBar(_ logic: NightLogic, review: Bool) -> some View {
        let sid = s.curSlot
        let st = logic.stats(sid)
        let label = logic.slots.first { $0.id == sid }?.label ?? sid
        var bits = [st.out > 0 ? "\(st.out) out" : "Nobody out"]
        if st.left > 0 { bits.append("\(st.left) left") }
        if st.away > 0 { bits.append("\(st.away) not in town") }
        if st.exc > 0 { bits.append("\(st.exc) excused") }
        let report = logic.report(s.dateKey)
        return VStack(spacing: 8) {
            if !review {
                Text("\(label) · \(bits.joined(separator: " · "))")
                    .font(.footnote).foregroundColor(.secondary).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 10) {
                Button {
                    UIPasteboard.general.string = report
                    model.show("Copied")
                } label: {
                    Label("Copy", systemImage: "doc.on.doc").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                // the night's picture, drawn only when it is actually sent; Copy is for the text
                ShareLink(item: NightPictureFile(session: s),
                          preview: SharePreview(Dates.hebrewDayMonth(s.dateKey), image: Image(systemName: "photo"))) {
                    Label("Send", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                if !review {
                    Button { s.closeNight() } label: {
                        Text("Save").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .controlSize(.large)
        }
        .padding(EdgeInsets(top: 10, leading: 16, bottom: 6, trailing: 16))
        .background(.bar)
        .overlay(alignment: .top) { Divider() }
    }

    @ViewBuilder private var toast: some View {
        if let t = model.toast {
            Text(t).font(.subheadline.weight(.medium))
                .padding(.horizontal, 18).padding(.vertical, 10)
                .background(.regularMaterial, in: Capsule())
                .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
                .padding(.bottom, 130)
                .transition(.opacity)
        }
    }
}

/// One room as a card: its name and count, buttons for the whole room, and its floor plan.
struct RoomCard: View {
    @EnvironmentObject private var model: AppModel
    let index: Int
    let logic: NightLogic
    let onPerson: (String) -> Void

    var body: some View {
        let s = model.session
        let room = Roster.PLAN[index]
        let sid = s.curSlot
        let hebrew = s.store.settings.hebrewOnPlan
        let out = logic.outIn(room, sid).count
        let beds = room.beds.reduce(0) { $0 + $1.slots.count }
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(hebrew ? room.hebLabel : room.label).font(.title3.weight(.semibold))
                Text(out > 0 ? "\(out) out" : "\(beds) beds")
                    .font(.subheadline.weight(out > 0 ? .semibold : .regular))
                    .foregroundColor(out > 0 ? RC.red : .secondary)
                Spacer(minLength: 4)
                HStack(spacing: 6) {
                    Button("All in") { s.setRoom(room, sid, .inRoom) }.tint(RC.green)
                    Button("All out") { s.setRoom(room, sid, .out) }.tint(RC.red)
                    Button("Clear") { s.setRoom(room, sid, nil) }.tint(.gray)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            FloorPlanView(room: room, allIn: logic.roomAllIn(room, sid)) { pid, bunk, wide, size in
                PersonSlot(
                    first: logic.first(pid, hebrew: hebrew), last: logic.last(pid, hebrew: hebrew),
                    bunk: s.store.settings.bunkLabels ? bunk : nil,
                    status: logic.statusOf(pid, sid), wide: wide, size: size,
                    highlighted: pid == s.highlight,
                    onName: { onPerson(pid) },
                    onMark: { s.setMark(pid, sid, $0) }
                )
            }
            // right under the room's name, not floating in the middle of the card
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .padding(14)
        .background(RC.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }
}

/// Any night, picked off the phone's own calendar.
struct NightPicker: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var day = Date()

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker("Night", selection: $day, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(.horizontal)
                Spacer(minLength: 0)
            }
            .navigationTitle("Go to a night")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Go") {
                        let c = Calendar.current.dateComponents([.year, .month, .day], from: day)
                        model.session.goToDate(Dates.keyOf(y: c.year!, m: c.month!, d: c.day!))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.large])
        .onAppear {
            let p = model.session.dateKey.split(separator: "-").compactMap { Int($0) }
            if p.count == 3, let d = Calendar.current.date(from: DateComponents(year: p[0], month: p[1], day: p[2], hour: 12)) {
                day = d
            }
        }
    }
}

/// The night's picture full screen - how the screenshot run gets to look at it.
struct PicturePreview: View {
    let image: UIImage

    var body: some View {
        ScrollView {
            Image(uiImage: image).resizable().scaledToFit()
        }
        .background(Color.white.ignoresSafeArea())
    }
}
