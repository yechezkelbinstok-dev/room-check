import SwiftUI
import RoomCheckCore

private let MARGIN = 5.0   // space outside the walls, so a door box can sit outside them
private let GAP = 7.0      // half the doorway's width, in room units
private let DOOR_D = 4.0   // how far the three-sided door box juts out past the wall

/// One room's floor plan, the way the Android app draws it: walls with a doorway - a gap with a
/// small three-sided box outside it - and every bed where the sketch puts it. Room and bed
/// coordinates are room units; one unit across is one unit down, so a bed turned 90 degrees
/// measures as long as it did upright.
struct FloorPlanView<Slot: View>: View {
    let room: Room
    let allIn: Bool
    /// One person's half of a bed: who, the bunk's "top"/"bottom", whether the slot is wide
    /// enough for the name beside the buttons, and its size on screen.
    @ViewBuilder let slot: (_ pid: String, _ bunk: String?, _ wide: Bool, _ size: CGSize) -> Slot

    var body: some View {
        let totalW = room.w + 2 * MARGIN, totalH = room.h + 2 * MARGIN
        GeometryReader { g in
            let u = g.size.width / totalW
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in walls(ctx, u) }
                ForEach(Array(room.beds.enumerated()), id: \.offset) { _, bed in
                    bedView(bed, u)
                        .frame(width: bed.w * u, height: bed.h * u)
                        .offset(x: (MARGIN + bed.x) * u, y: (MARGIN + bed.y) * u)
                }
            }
        }
        .aspectRatio(totalW / totalH, contentMode: .fit)
        .background(allIn ? Color(hex: 0xF7FCFA) : RC.floor)
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private func bedView(_ bed: Bed, _ u: Double) -> some View {
        let n = bed.slots.count
        let size = CGSize(width: bed.w * u, height: bed.h * u / Double(n))
        // a bunk stacks its two people top and bottom; whether a slot puts the name beside the
        // buttons or above them follows the slot's own shape, so neither gets squeezed
        let wide = bed.w > (bed.h / Double(n)) * 1.4
        return ZStack {
            RoundedRectangle(cornerRadius: 5).fill(Color.white)
            VStack(spacing: 0) {
                ForEach(Array(bed.slots.enumerated()), id: \.offset) { i, pid in
                    slot(pid, n > 1 ? (i == 0 ? "top" : "bottom") : nil, wide, size)
                        .frame(width: size.width, height: size.height)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 5))
            RoundedRectangle(cornerRadius: 5).stroke(Color(hex: 0xC9CBD2), lineWidth: 1.5)
        }
    }

    private func walls(_ ctx: GraphicsContext, _ u: Double) {
        let door = room.door
        func ix(_ v: Double) -> Double { (MARGIN + v) * u }
        let left = ix(0), right = ix(room.w), top = ix(0), bottom = ix(room.h)
        let a = ix(door.pos - GAP), b = ix(door.pos + GAP), d = DOOR_D * u
        var p = Path()
        func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
            p.move(to: CGPoint(x: x1, y: y1)); p.addLine(to: CGPoint(x: x2, y: y2))
        }
        // the walls without the door run their full length; the door's wall is split round the gap
        if door.wall != .top { line(left, top, right, top) }
        if door.wall != .bottom { line(left, bottom, right, bottom) }
        if door.wall != .left { line(left, top, left, bottom) }
        if door.wall != .right { line(right, top, right, bottom) }
        switch door.wall {
        case .bottom:
            line(left, bottom, a, bottom); line(b, bottom, right, bottom)
            line(a, bottom, a, bottom + d); line(a, bottom + d, b, bottom + d); line(b, bottom + d, b, bottom)
        case .top:
            line(left, top, a, top); line(b, top, right, top)
            line(a, top, a, top - d); line(a, top - d, b, top - d); line(b, top - d, b, top)
        case .left:
            line(left, top, left, a); line(left, b, left, bottom)
            line(left, a, left - d, a); line(left - d, a, left - d, b); line(left - d, b, left, b)
        case .right:
            line(right, top, right, a); line(right, b, right, bottom)
            line(right, a, right + d, a); line(right + d, a, right + d, b); line(right + d, b, right, b)
        }
        ctx.stroke(p, with: .color(RC.wall), style: StrokeStyle(lineWidth: 1.6 * u, lineCap: .square))
    }
}

/// One person on the plan: the surname, the first name under it when there is height for both,
/// and here / not here - or, for someone off the walk, a word in place of the buttons.
struct PersonSlot: View {
    let first: String
    let last: String
    let bunk: String?
    let status: Mark?
    let wide: Bool
    let size: CGSize
    var highlighted = false
    let onName: () -> Void
    let onMark: (Mark) -> Void

    private var background: Color {
        switch status {
        case .inRoom: return RC.greenL
        case .out: return RC.redL
        case .exc, .away: return RC.greyL
        case nil: return .clear
        }
    }

    private var nameColor: Color {
        switch status {
        case .out: return RC.red
        case .exc, .away: return RC.grey
        default: return RC.text
        }
    }

    /// How big the buttons are and whether the first name fits, worked out from the slot's size.
    /// The buttons are served before the name, so a long name shortens itself instead of crushing
    /// them; the first name shows only where there is height for it under the surname.
    private struct Fit { let padH: CGFloat; let button: CGFloat; let showFirst: Bool }

    private var fit: Fit {
        let w: CGFloat = size.width, h: CGFloat = size.height
        let padH: CGFloat = wide ? 6 : 3
        let padV: CGFloat = 5
        let labelH: CGFloat = (bunk != nil && !wide) ? 10 : 0
        var button: CGFloat
        var nameRoom: CGFloat
        if wide {
            button = min(26, h - 2 * padV)
            nameRoom = h - 2 * padV
        } else {
            let byWidth: CGFloat = (w - 2 * padH - 3) / 2
            let byHeight: CGFloat = h - 2 * padV - labelH - 4 - 15
            button = min(30, min(byWidth, byHeight))
            nameRoom = h - 2 * padV - labelH - 4
        }
        button = max(15, button)
        if !wide { nameRoom -= button }
        return Fit(padH: padH, button: button, showFirst: nameRoom >= 27)
    }

    var body: some View {
        let f = fit
        let padH = f.padH, button = f.button, showFirst = f.showFirst
        Group {
            if wide {
                HStack(spacing: 6) {
                    if let bunk { Text(bunk).font(.system(size: 9)).foregroundColor(RC.bunk).lineLimit(1) }
                    name(showFirst, alignment: .leading)
                    Spacer(minLength: 0)
                    marks(button)
                }
            } else {
                VStack(spacing: 4) {
                    if let bunk {
                        Text(bunk).font(.system(size: 9)).foregroundColor(RC.bunk).lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    name(showFirst, alignment: .center)
                        .frame(maxHeight: .infinity)
                    marks(button)
                }
            }
        }
        .padding(.horizontal, padH)
        .padding(.vertical, 5)
        .frame(width: size.width, height: size.height)
        .background(background)
        .overlay { if highlighted { RoundedRectangle(cornerRadius: 4).stroke(RC.blue, lineWidth: 2.5) } }
    }

    private func name(_ showFirst: Bool, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 1) {
            // the surname is always one line: a long one in a narrow bed shrinks rather than breaks
            Text(last).font(.system(size: 13, weight: .bold)).foregroundColor(nameColor)
                .lineLimit(1).minimumScaleFactor(0.5)
            if showFirst {
                Text(first).font(.system(size: wide ? 9 : 9.5)).foregroundColor(RC.sub)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onName)
    }

    @ViewBuilder private func marks(_ size: CGFloat) -> some View {
        switch status {
        case .away: offTag("Not in town", RC.blue)
        case .exc: offTag("Excused", RC.grey)
        default:
            HStack(spacing: 3) {
                markButton(size, "checkmark", on: status == .inRoom, color: RC.green) { onMark(.inRoom) }
                markButton(size, "xmark", on: status == .out, color: RC.red) { onMark(.out) }
            }
        }
    }

    private func offTag(_ text: String, _ color: Color) -> some View {
        Text(text).font(.system(size: 11, weight: .semibold)).foregroundColor(color)
            .lineLimit(1).minimumScaleFactor(0.6)
    }

    private func markButton(_ size: CGFloat, _ symbol: String, on: Bool, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: size * 0.45, weight: .bold))
                .foregroundColor(on ? .white : RC.glyphOff)
                .frame(width: size, height: size)
                .background(on ? color : RC.buttonOff)
                .clipShape(RoundedRectangle(cornerRadius: 7))
        }
        .buttonStyle(.plain)
    }
}
