import SwiftUI
import UIKit
import UniformTypeIdentifiers
import RoomCheckCore

/// The whole night as one picture to send, drawn the way the Android app draws it (NightImage.kt):
/// who was missing at each time up top, where it is read first, and the filled-in sheet under it.
///
/// Shaped to be read WITHOUT opening it. A chat app shows a picture in the thread only down to
/// about 1.4 times as tall as it is wide and crops whatever is past that, so it is laid out in two
/// columns and kept inside [maxRatio]. A Hebrew night is mirrored: laid out left to right, then
/// flipped, so there is one layout to reason about rather than two.
enum NightPicture {
    private static let maxRatio: CGFloat = 1.28   // height : width, with room to spare under the crop
    private static let pad: CGFloat = 64
    private static let gap: CGFloat = 56          // between the two sheet columns
    private static let rowH: CGFloat = 76         // one person
    private static let roomHeadH: CGFloat = 66    // the "Room 3" band above each group
    private static let bandH: CGFloat = 74        // the header strip over the sheet
    private static let chip: CGFloat = 52
    private static let colW: CGFloat = 106        // one time column
    private static let headerH: CGFloat = 190
    private static let titleH: CGFloat = 76       // the heading over the names
    private static let cardGap: CGFloat = 26
    private static let cardPad: CGFloat = 28
    private static let cardHead: CGFloat = 96     // the time at the top of a card, down to its first name
    private static let lineH: CGFloat = 50
    private static let nameSize: CGFloat = 32
    private static let innerGap: CGFloat = 34     // between name columns inside one card
    private static let maxCols = 4                // past this a column is too narrow to read down
    // a Hebrew sheet is Hebrew throughout - a lone English phrase in it reads like something forgotten
    private static let title = "חסרים"
    private static let allInHe = "כולם היו"
    private static let notMarkedHe = "לא סומן"

    private static func rgb(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
    // the picture is always light: it is read in a chat thread, not in this app's own dark mode
    private static let ink = rgb(0x111114), sub = rgb(0x6C6E76), faint = rgb(0xB7B9C2), hair = rgb(0xE4E4E9)
    private static let panel = rgb(0xF2F2F7), green = rgb(0x12795A), greenL = rgb(0xE6F4EE)
    private static let red = rgb(0xC42B21), redL = rgb(0xFBEBEA), grey = rgb(0x8A8A8E), greyL = rgb(0xEEEEF1)
    private static let unmarked = rgb(0xF7F7F9)

    private static func font(_ size: CGFloat, bold: Bool = false) -> UIFont {
        .systemFont(ofSize: size, weight: bold ? .bold : .regular)
    }

    private static func width(_ text: String, _ f: UIFont) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: f]).width
    }

    /// Greedy wrap - the missing names are the only text here long enough to need it.
    private static func wrap(_ text: String, _ f: UIFont, _ maxWidth: CGFloat) -> [String] {
        var out: [String] = []
        var line = ""
        for word in text.split(separator: " ").map(String.init) {
            let candidate = line.isEmpty ? word : line + " " + word
            if width(candidate, f) <= maxWidth || line.isEmpty { line = candidate }
            else { out.append(line); line = word }
        }
        out.append(line)
        return out
    }

    /// One card per time: its names one to a line, in as many columns as the card is wide enough
    /// for. Red for names, green only for a round actually walked and clear, grey for one nobody
    /// marked - green on "not marked" would read as an all-clear it has not earned.
    private struct Summary {
        let label: String
        let cols: [[String]]
        let colW: CGFloat
        let tone: UIColor
        var rows: Int { cols.map(\.count).max() ?? 0 }
    }

    private static func summarize(_ logic: NightLogic, _ slots: [Slot], _ hebrew: Bool, _ cardTextW: CGFloat) -> [Summary] {
        let f = font(nameSize)
        return slots.map { slot in
            let missing = logic.missingAt(slot.id)
            let started = logic.stats(slot.id).started
            let tone = !missing.isEmpty ? red : (started ? green : grey)
            if missing.isEmpty {
                let text = !started ? (hebrew ? notMarkedHe : "Not marked") : (hebrew ? allInHe : "Everybody there")
                return Summary(label: slot.label, cols: [[text]], colW: cardTextW, tone: tone)
            }
            let names = missing.map { logic.nameOf($0, hebrew: hebrew) }
            let widest = min(names.map { width($0, f) }.max() ?? 0, cardTextW)
            let cols = max(1, min(Int((cardTextW + innerGap) / (widest + innerGap)), min(names.count, maxCols)))
            let colW = (cardTextW - CGFloat(cols - 1) * innerGap) / CGFloat(cols)
            return Summary(label: slot.label, cols: flow(names.map { wrap($0, f, colW) }, cols), colW: colW, tone: tone)
        }
    }

    /// Fills columns top to bottom, breaking only between names so a wrapped one is never split.
    private static func flow(_ entries: [[String]], _ cols: Int) -> [[String]] {
        if cols <= 1 { return [entries.flatMap { $0 }] }
        let total = entries.reduce(0) { $0 + $1.count }
        let target = max(1, Int((Double(total) / Double(cols)).rounded(.up)))
        var out: [[String]] = []
        var cur: [String] = []
        for e in entries {
            if !cur.isEmpty && cur.count + e.count > target && out.count < cols - 1 { out.append(cur); cur = [] }
            cur += e
        }
        out.append(cur)
        return out
    }

    private static func cardWidth(_ w: CGFloat, _ n: Int) -> CGFloat { (w - 2 * pad - CGFloat(n - 1) * cardGap) / CGFloat(n) }
    private static func cardHeight(_ cards: [Summary]) -> CGFloat {
        cardHead + CGFloat(cards.map(\.rows).max() ?? 1) * lineH + cardPad
    }
    private static func summaryH(_ s: [Summary]) -> CGFloat { 28 + titleH + cardHeight(s) + 30 }

    private static func blockH(_ room: Room) -> CGFloat {
        roomHeadH + CGFloat(room.beds.reduce(0) { $0 + $1.slots.count }) * rowH
    }

    /// The rooms in two columns of near-equal height, so neither runs long alone.
    private static func splitPoint() -> Int {
        let half = Roster.PLAN.reduce(0) { $0 + blockH($1) } / 2
        var run: CGFloat = 0
        for (i, room) in Roster.PLAN.enumerated() {
            run += blockH(room)
            if run >= half { return i + 1 }
        }
        return Roster.PLAN.count
    }

    private static func heightOf(_ summaries: [Summary]) -> CGFloat {
        let split = splitPoint()
        let left = Roster.PLAN.prefix(split).reduce(0) { $0 + blockH($1) }
        let right = Roster.PLAN.dropFirst(split).reduce(0) { $0 + blockH($1) }
        return headerH + summaryH(summaries) + bandH + max(left, right) + pad
    }

    static func render(_ logic: NightLogic, dateKey: String, hebrew: Bool, slots: [Slot]) -> UIImage {
        // a column is built around the longest name there is, so nothing is shortened to fit
        let widest = Roster.PEOPLE.map { width(logic.nameOf($0.id, hebrew: hebrew), font(34)) }.max() ?? 300
        let sheetColW = widest + 40 + CGFloat(slots.count) * colW
        var w = 2 * sheetColW + gap + 2 * pad
        var summaries = summarize(logic, slots, hebrew, cardWidth(w, slots.count) - 2 * cardPad)
        var h = heightOf(summaries)
        // a heavy night makes the summary long: widen rather than let the thread crop it - the
        // extra width re-wraps the names shorter, so one pass always lands inside the ratio
        if h / w > maxRatio {
            w = h / maxRatio
            summaries = summarize(logic, slots, hebrew, cardWidth(w, slots.count) - 2 * cardPad)
            h = heightOf(summaries)
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: w.rounded(), height: h.rounded()), format: format).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
            let d = Dir(w: w, rtl: hebrew)
            drawHeader(dateKey, d)
            drawSummary(summaries, headerH, d)
            drawSheet(ctx.cgContext, logic, slots, hebrew, headerH + summaryH(summaries), d)
        }
    }

    /// Mirrors the whole sheet for a Hebrew night: points are measured in from the edge you start
    /// reading at, and text grows away from it.
    private struct Dir {
        let w: CGFloat
        let rtl: Bool
        func p(_ x: CGFloat) -> CGFloat { rtl ? w - x : x }
        func box(_ x: CGFloat, _ boxW: CGFloat) -> CGFloat { rtl ? w - x - boxW : x }
    }

    private enum Anchor { case start, center }

    /// Draws [text] with its baseline at [y], starting at [x] - from the reading edge, so on a
    /// Hebrew sheet it ends at x rather than beginning there.
    private static func text(_ s: String, _ x: CGFloat, _ y: CGFloat, _ f: UIFont, _ color: UIColor, _ d: Dir,
                             anchor: Anchor = .start, kern: CGFloat = 0) {
        let attrs: [NSAttributedString.Key: Any] = [.font: f, .foregroundColor: color, .kern: kern]
        let tw = (s as NSString).size(withAttributes: attrs).width
        let left: CGFloat
        switch anchor {
        case .center: left = x - tw / 2
        case .start: left = d.rtl ? x - tw : x
        }
        (s as NSString).draw(at: CGPoint(x: left, y: y - f.ascender), withAttributes: attrs)
    }

    private static func line(_ cg: CGContext, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat,
                             _ color: UIColor, _ width: CGFloat, round: Bool = false) {
        cg.setStrokeColor(color.cgColor)
        cg.setLineWidth(width)
        cg.setLineCap(round ? .round : .butt)
        cg.move(to: CGPoint(x: x1, y: y1))
        cg.addLine(to: CGPoint(x: x2, y: y2))
        cg.strokePath()
    }

    private static func drawHeader(_ dateKey: String, _ d: Dir) {
        text(Dates.hebrewDayMonth(dateKey), d.p(pad), 104, font(64, bold: true), ink, d)
        text(Dates.hebrewNightName(dateKey), d.p(pad), 156, font(38), sub, d)
        hair.setFill()
        UIRectFill(CGRect(x: pad, y: 185, width: d.w - 2 * pad, height: 2))
    }

    /// Who was missing, at the top, so it answers the question before anything is opened. Every
    /// card takes the tallest one's height: sized to their own contents they would stair-step.
    private static func drawSummary(_ summaries: [Summary], _ startY: CGFloat, _ d: Dir) {
        var y = startY + 28
        text(title, d.p(pad), y + 46, font(50, bold: true), ink, d)
        y += titleH
        let cardW = cardWidth(d.w, summaries.count)
        let cardH = cardHeight(summaries)
        for (i, s) in summaries.enumerated() {
            let x = pad + CGFloat(i) * (cardW + cardGap)
            panel.setFill()
            UIBezierPath(roundedRect: CGRect(x: d.box(x, cardW), y: y, width: cardW, height: cardH), cornerRadius: 20).fill()
            text(s.label, d.p(x + cardPad), y + 52, font(40, bold: true), ink, d)
            for (k, col) in s.cols.enumerated() {
                let cx = x + cardPad + CGFloat(k) * (s.colW + innerGap)
                for (j, name) in col.enumerated() {
                    text(name, d.p(cx), y + cardHead + CGFloat(j) * lineH, font(nameSize), s.tone, d)
                }
            }
        }
    }

    /// The filled-in sheet: every bochur, every time, in two columns so it all fits above the fold.
    private static func drawSheet(_ cg: CGContext, _ logic: NightLogic, _ slots: [Slot], _ hebrew: Bool,
                                  _ startY: CGFloat, _ d: Dir) {
        let sheetColW = (d.w - 2 * pad - gap) / 2
        let colX = [pad, pad + sheetColW + gap]
        let n = CGFloat(slots.count)
        // a column heading done as one: smaller than the names it labels, in ink, letter-spaced and
        // centred over its own column, with one hairline under the row
        for x in colX {
            for (i, slot) in slots.enumerated() {
                text(slot.label, d.p(x + sheetColW - (n - 0.5 - CGFloat(i)) * colW), startY + 40,
                     font(27, bold: true), ink, d, anchor: .center, kern: 0.8)
            }
            line(cg, d.p(x), startY + 58, d.p(x + sheetColW), startY + 58, hair, 2)
        }
        let split = splitPoint()
        drawColumn(cg, logic, slots, hebrew, Array(Roster.PLAN.prefix(split)), colX[0], startY + bandH, sheetColW, d)
        drawColumn(cg, logic, slots, hebrew, Array(Roster.PLAN.dropFirst(split)), colX[1], startY + bandH, sheetColW, d)
    }

    private static func drawColumn(_ cg: CGContext, _ logic: NightLogic, _ slots: [Slot], _ hebrew: Bool,
                                   _ rooms: [Room], _ x: CGFloat, _ top: CGFloat, _ cw: CGFloat, _ d: Dir) {
        var y = top
        for room in rooms {
            text(hebrew ? room.hebLabel : room.label.uppercased(), d.p(x), y + 44, font(28, bold: true), faint, d)
            y += roomHeadH
            for pid in room.beds.flatMap(\.slots) {
                drawPersonRow(cg, logic, slots, hebrew, pid, x, y, cw, d)
                line(cg, d.p(x), y + rowH, d.p(x + cw), y + rowH, hair, 1.5)
                y += rowH
            }
        }
    }

    private static func drawPersonRow(_ cg: CGContext, _ logic: NightLogic, _ slots: [Slot], _ hebrew: Bool,
                                      _ pid: String, _ x: CGFloat, _ y: CGFloat, _ cw: CGFloat, _ d: Dir) {
        let mid = y + rowH / 2
        // not in town: a grey name and nothing under the times - there was nobody to look for
        if logic.isAway(pid) {
            text(logic.nameOf(pid, hebrew: hebrew), d.p(x), mid + 12, font(34), faint, d)
            return
        }
        text(logic.nameOf(pid, hebrew: hebrew), d.p(x), mid + 12, font(34), ink, d)
        for (i, slot) in slots.enumerated() {
            let cx = x + cw - CGFloat(slots.count - i) * colW + (colW - chip) / 2
            drawChip(cg, logic.statusOf(pid, slot.id), d.box(cx, chip), mid - chip / 2, hebrew, d)
        }
    }

    private static func drawChip(_ cg: CGContext, _ mark: Mark?, _ x: CGFloat, _ y: CGFloat, _ hebrew: Bool, _ d: Dir) {
        let bg: UIColor, fg: UIColor
        switch mark {
        case .inRoom: bg = greenL; fg = green
        case .out: bg = redL; fg = red
        case .exc, .away: bg = greyL; fg = grey
        case nil: bg = unmarked; fg = faint
        }
        bg.setFill()
        UIBezierPath(roundedRect: CGRect(x: x, y: y, width: chip, height: chip), cornerRadius: 13).fill()
        let cx = x + chip / 2, cy = y + chip / 2
        // drawn as strokes rather than glyphs, so the sheet cannot depend on a font's tick mark
        switch mark {
        case .inRoom:
            line(cg, cx - 12, cy + 1, cx - 3, cy + 10, fg, 5, round: true)
            line(cg, cx - 3, cy + 10, cx + 12, cy - 10, fg, 5, round: true)
        case .out:
            line(cg, cx - 10, cy - 10, cx + 10, cy + 10, fg, 5, round: true)
            line(cg, cx + 10, cy - 10, cx - 10, cy + 10, fg, 5, round: true)
        case .exc:
            // פ׳ for patur on a Hebrew sheet - an English letter has no business on it
            text(hebrew ? "פ׳" : "E", cx, cy + 11, font(32, bold: true), fg, d, anchor: .center)
        case .away:
            break
        case nil:
            text("–", cx, cy + 11, font(32), fg, d, anchor: .center)
        }
    }
}

/// The picture for the share sheet, drawn only when it is actually sent - from a copy of the
/// night, so the drawing never reads a night that is being marked at the same moment.
struct NightPictureFile: Transferable {
    let night: Night
    let extra: [String: PersonOverride]
    let dateKey: String
    let hebrew: Bool
    let slots: [Slot]

    init(session: NightSession) {
        night = session.night.copy()
        extra = session.store.extra
        dateKey = session.dateKey
        hebrew = session.store.settings.hebrewInExport
        slots = Slots.forSheet(session.night, dateKey: session.dateKey)
    }

    func render() -> UIImage {
        NightPicture.render(NightLogic(night, extra, Slots.all(night, dateKey: dateKey)),
                            dateKey: dateKey, hebrew: hebrew, slots: slots)
    }

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .png) { pic in pic.render().pngData() ?? Data() }
    }
}
