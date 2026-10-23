import SwiftUI
import UIKit
import RoomCheckCore

/// The marking screen, laid out like the Android app's: the night at the top, the rounds and the
/// rooms under it, one room's plan, and the count with Save and the report at the bottom. A past
/// night that was marked opens as the night's summary instead, until you say you are editing it.
struct CheckView: View {
    @EnvironmentObject private var model: AppModel
    @State private var person: String?

    private var s: NightSession { model.session }

    var body: some View {
        let logic = s.logic
        let review = s.showingReview
        VStack(spacing: 0) {
            topBar
            if review {
                ReviewView(logic: logic)
            } else {
                VStack(spacing: 8) {
                    roundTabs(logic)
                    roomButtons(logic)
                }
                .padding(EdgeInsets(top: 10, leading: 12, bottom: 4, trailing: 12))
                roomArea(logic)
            }
            bottomBar(logic, review: review)
        }
        .background(RC.bg.ignoresSafeArea())
        .overlay(alignment: .bottom) {
            if let t = model.toast {
                Text(t).font(.system(size: 13.5)).foregroundColor(.white)
                    .padding(.horizontal, 17).padding(.vertical, 9)
                    .background(Color(hex: 0x121218).opacity(0.94))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.bottom, 110)
            }
        }
        .confirmationDialog(
            person.map { logic.nameOf($0, hebrew: s.store.settings.hebrewOnPlan) } ?? "",
            isPresented: Binding(get: { person != nil }, set: { if !$0 { person = nil } }),
            titleVisibility: .visible,
            presenting: person
        ) { pid in personActions(pid, logic) }
    }

    // MARK: - the night

    private var topBar: some View {
        HStack(spacing: 6) {
            iconButton("chevron.left") { s.shiftDay(-1) }
            VStack(spacing: 1) {
                Text(Dates.hebrewDate(s.dateKey)).font(.system(size: 19, weight: .bold))
                    .lineLimit(1).minimumScaleFactor(0.7)
                Text(Dates.longDate(s.dateKey)).font(.system(size: 11.5)).foregroundColor(RC.sub).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            iconButton("chevron.right") { s.shiftDay(1) }
            if s.dateKey != s.tonight() { iconButton("location", small: true) { s.goToday() } }
            if !s.isLocked { iconButton("arrow.uturn.backward") { s.undo() } }
            Menu {
                Button("Sync now") { model.syncNow(loud: true) }
                Toggle("Hebrew names on the plan", isOn: Binding(
                    get: { s.store.settings.hebrewOnPlan },
                    set: { on in s.updateSettings { $0.hebrewOnPlan = on } }
                ))
                Text(syncLine)
                Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
            } label: {
                iconLabel("ellipsis", small: false)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var syncLine: String {
        switch model.syncState {
        case .off: return "Sync: ready"
        case .working: return "Syncing…"
        case .ok(let at, _):
            let f = DateFormatter(); f.dateFormat = "h:mm a"
            return "Last synced " + f.string(from: at).lowercased()
        case .failed(let reason): return reason
        }
    }

    private func iconButton(_ symbol: String, small: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) { iconLabel(symbol, small: small) }.buttonStyle(.plain)
    }

    private func iconLabel(_ symbol: String, small: Bool) -> some View {
        Image(systemName: symbol)
            .font(.system(size: small ? 14 : 16, weight: .semibold))
            .foregroundColor(RC.blue)
            .frame(width: 36, height: 36)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(RC.sep, lineWidth: 1))
    }

    // MARK: - rounds and rooms

    private func roundTabs(_ logic: NightLogic) -> some View {
        HStack(spacing: 3) {
            ForEach(logic.slots, id: \.id) { slot in
                let on = slot.id == s.curSlot
                let line = Self.statLine(logic.stats(slot.id))
                Button { s.selectSlot(slot.id) } label: {
                    VStack(spacing: 2) {
                        Text(slot.label).font(.system(size: 16, weight: .semibold)).foregroundColor(on ? RC.text : RC.sub2)
                        Text(line.text).font(.system(size: 11, weight: line.bold ? .bold : .regular)).foregroundColor(line.color)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(on ? Color.white : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(RC.segment)
        .clipShape(RoundedRectangle(cornerRadius: 11))
    }

    /// Under each round's time: how many out, all in, how many rooms left, or a dash before it starts.
    private static func statLine(_ st: SlotStats) -> (text: String, color: Color, bold: Bool) {
        if st.out > 0 { return ("\(st.out) out", RC.red, true) }
        if !st.started { return ("—", RC.sub, false) }
        if st.left == 0 { return ("all in", RC.green, true) }
        return ("\(st.left) left", RC.sub, false)
    }

    private static let roomActions: [(String, Mark?)] = [("All in", .inRoom), ("All out", .out), ("Clear", nil)]

    /// One row per floor, rooms 1 to 3 on top. Every row is cut into as many columns as the
    /// fullest floor has rooms, so a number sits in the same place on both.
    private func roomButtons(_ logic: NightLogic) -> some View {
        let cols = Roster.FLOORS.map(\.count).max() ?? 5
        return HStack(spacing: 6) {
            VStack(spacing: 5) {
                ForEach(0..<Roster.FLOORS.count, id: \.self) { f in
                    HStack(spacing: 5) {
                        ForEach(Roster.FLOORS[f], id: \.self) { i in roomButton(i, logic) }
                        ForEach(0..<(cols - Roster.FLOORS[f].count), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity).frame(height: 34)
                        }
                    }
                }
            }
            iconButton("eye") { s.setReviewing(true) }
        }
    }

    private func roomButton(_ i: Int, _ logic: NightLogic) -> some View {
        let room = Roster.PLAN[i]
        let sid = s.curSlot
        let selected = i == s.room
        let out = logic.outIn(room, sid).count
        let checked = logic.roomChecked(room, sid)
        var bg = Color.white, fg = RC.sub2, plain = true
        if selected { bg = RC.text; fg = .white; plain = false }
        else if out > 0 { fg = Color(hex: 0x7A1A14) }
        else if checked { bg = RC.greenL; fg = RC.green; plain = false }
        // split by its marks: red across the share missing, green across the share in, white
        // across whoever is not marked yet; nothing marked or everyone in stays one colour
        let red = logic.missingShare(room, sid), green = logic.inShare(room, sid)
        let split = !selected && (red > 0 || green > 0) && green < 1
        var border = Color.clear
        if split { border = red > 0 ? Color(hex: 0xE8B9B4) : Color(hex: 0xBCE0D2) }
        else if plain { border = RC.sep }
        return Button { s.jumpRoom(i) } label: {
            Text("\(i + 1)").font(.system(size: 14, weight: .semibold)).foregroundColor(fg)
                .frame(maxWidth: .infinity).frame(height: 34)
                .background {
                    if split {
                        GeometryReader { g in
                            HStack(spacing: 0) {
                                Color(hex: 0xF4C7C3).frame(width: g.size.width * red)
                                Color(hex: 0xD3ECE1).frame(width: g.size.width * green)
                                Color.white
                            }
                        }
                    } else {
                        bg
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: - the room

    private func roomName(_ i: Int) -> String {
        let r = Roster.PLAN[i]
        return s.store.settings.hebrewOnPlan ? r.hebLabel : r.label
    }

    private func roomArea(_ logic: NightLogic) -> some View {
        let room = Roster.PLAN[s.room]
        let sid = s.curSlot
        let hebrew = s.store.settings.hebrewOnPlan
        let out = logic.outIn(room, sid).count
        let beds = room.beds.reduce(0) { $0 + $1.slots.count }
        return VStack(spacing: 0) {
            HStack(alignment: .lastTextBaseline) {
                Text(roomName(s.room)).font(.system(size: 19, weight: .bold))
                Text(out > 0 ? "\(out) out" : "\(beds) beds")
                    .font(.system(size: 13, weight: out > 0 ? .bold : .medium))
                    .foregroundColor(out > 0 ? RC.red : RC.sub)
                Spacer()
                HStack(spacing: 14) {
                    ForEach(Self.roomActions.indices, id: \.self) { i in
                        Button(Self.roomActions[i].0) { s.setRoom(room, sid, Self.roomActions[i].1) }
                            .font(.system(size: 14, weight: .semibold)).foregroundColor(RC.blue)
                    }
                }
            }
            .padding(EdgeInsets(top: 12, leading: 16, bottom: 8, trailing: 16))

            FloorPlanView(room: room, allIn: logic.roomAllIn(room, sid)) { pid, bunk, wide, size in
                PersonSlot(
                    first: logic.first(pid, hebrew: hebrew), last: logic.last(pid, hebrew: hebrew),
                    bunk: s.store.settings.bunkLabels ? bunk : nil,
                    status: logic.statusOf(pid, sid), wide: wide, size: size,
                    onName: { person = pid },
                    onMark: { s.setMark(pid, sid, $0) }
                )
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            HStack(spacing: 8) {
                navButton(s.room > 0 ? roomName(s.room - 1) : nil, back: true) { s.goRoom(-1) }
                navButton(s.room < Roster.PLAN.count - 1 ? roomName(s.room + 1) : nil, back: false) { s.goRoom(1) }
            }
            .padding(EdgeInsets(top: 14, leading: 12, bottom: 6, trailing: 12))
        }
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 30).onEnded { v in
            if v.translation.width > 55 { s.goRoom(-1) } else if v.translation.width < -55 { s.goRoom(1) }
        })
    }

    /// At the first and last room the button has nowhere to go, so it holds its place as blank
    /// space rather than an empty white box - an unlabelled button reads as something broken.
    @ViewBuilder
    private func navButton(_ label: String?, back: Bool, action: @escaping () -> Void) -> some View {
        if let label {
            Button(action: action) {
                HStack(spacing: 5) {
                    if back { Text("‹") }
                    Text(label)
                    if !back { Text("›") }
                }
                .font(.system(size: 14.5, weight: .semibold))
                .foregroundColor(RC.blue)
                .frame(maxWidth: .infinity).frame(height: 48)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 11))
                .overlay(RoundedRectangle(cornerRadius: 11).stroke(RC.sep, lineWidth: 1))
            }
            .buttonStyle(.plain)
        } else {
            Color.clear.frame(maxWidth: .infinity).frame(height: 48)
        }
    }

    // MARK: - a person

    @ViewBuilder
    private func personActions(_ pid: String, _ logic: NightLogic) -> some View {
        let sid = s.curSlot
        if let room = Roster.roomOf[pid], room.beds.reduce(0, { $0 + $1.slots.count }) > 1 {
            Button("Only him in the room") { s.setRoom(room, sid, nil, only: pid) }
            Button("Only him missing") { s.setRoom(room, sid, nil, only: pid, onlyMark: .out) }
        }
        Button(logic.isAway(pid) ? "Back in town" : "Not in town") { s.toggleAway(pid) }
        Button(s.night.excusedTonight.contains(pid) ? "No longer excused tonight" : "Excused tonight") {
            s.toggleExcusedTonight(pid)
        }
        Button(logic.isAlways(pid) ? "No longer excused" : "Excused until turned off") { s.toggleAlwaysExcused(pid) }
        Button("Cancel", role: .cancel) {}
    }

    // MARK: - the count, and what to do with it

    private func bottomBar(_ logic: NightLogic, review: Bool) -> some View {
        let sid = s.curSlot
        let st = logic.stats(sid)
        let label = logic.slots.first { $0.id == sid }?.label ?? sid
        var bits = [st.out > 0 ? "\(st.out) out" : "Nobody out"]
        if st.left > 0 { bits.append("\(st.left) left") }
        if st.away > 0 { bits.append("\(st.away) not in town") }
        if st.exc > 0 { bits.append("\(st.exc) excused") }
        let report = logic.report(s.dateKey)
        return VStack(alignment: .leading, spacing: 7) {
            if !review {
                Text("\(label) · \(bits.joined(separator: " · "))")
                    .font(.system(size: 13)).foregroundColor(RC.sub2).lineLimit(1)
                    .padding(.leading, 2)
            }
            HStack(spacing: 8) {
                if !review { barButton("Save", RC.blue) { s.closeNight() } }
                barButton("Copy text", RC.text) {
                    UIPasteboard.general.string = report
                    model.show("Copied")
                }
                ShareLink(item: report) { barLabel("Send text", RC.text) }
            }
        }
        .padding(EdgeInsets(top: 8, leading: 12, bottom: 10, trailing: 12))
        .background(RC.bg.opacity(0.94))
    }

    private func barButton(_ text: String, _ color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) { barLabel(text, color) }.buttonStyle(.plain)
    }

    private func barLabel(_ text: String, _ color: Color) -> some View {
        Text(text).font(.system(size: 14, weight: .bold)).foregroundColor(.white).lineLimit(1)
            .frame(maxWidth: .infinity).frame(height: 44)
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

/// A night already walked: each round with who was missing, the way the report reads, and the
/// way back into marking it.
struct ReviewView: View {
    @EnvironmentObject private var model: AppModel
    let logic: NightLogic

    var body: some View {
        let s = model.session
        let hebrew = s.store.settings.hebrewOnPlan
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(logic.slots, id: \.id) { slot in
                    let missing = logic.missingAt(slot.id)
                    let st = logic.stats(slot.id)
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(slot.label).font(.system(size: 17, weight: .bold))
                            Spacer()
                            Text(missing.isEmpty ? (st.started ? "everybody there" : "not marked") : "\(missing.count) out")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(missing.isEmpty ? (st.started ? RC.green : RC.sub) : RC.red)
                        }
                        ForEach(missing, id: \.self) { pid in
                            HStack {
                                Text(logic.nameOf(pid, hebrew: hebrew)).font(.system(size: 15))
                                Spacer()
                                Text(Roster.roomOf[pid].map { hebrew ? $0.hebLabel : $0.label } ?? "")
                                    .font(.system(size: 12.5)).foregroundColor(RC.sub)
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                let away = logic.awayList()
                if !away.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Not in town").font(.system(size: 17, weight: .bold))
                        ForEach(away, id: \.self) { pid in Text(logic.nameOf(pid, hebrew: hebrew)).font(.system(size: 15)) }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                Button(s.isLocked ? "Edit this night" : "Back to marking") {
                    if s.isLocked { s.setEditing(true) } else { s.setReviewing(false) }
                }
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(RC.blue)
                .frame(maxWidth: .infinity).frame(height: 46)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(12)
        }
    }
}
