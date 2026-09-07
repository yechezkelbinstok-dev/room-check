import Foundation

/// Every date function here works over its own absolute-day-number arithmetic rather than any
/// platform calendar object.
///
/// That is deliberate, not an oversight: the website has no ICU access at all, so it carries its
/// own from-scratch Hebrew calendar (molad-based new year with the standard leap/postponement
/// rules) - and that is the implementation this file ports, verbatim, rather than delegating to
/// Foundation's `Calendar(identifier: .hebrew)`. Foundation's own Hebrew calendar was tried first
/// and disagreed with the Android app's ICU-backed one on which years are leap (it called 5775 a
/// leap year; every other implementation, and the historical record, says it is not) - almost
/// certainly a difference between Linux's Foundation and Apple's own, which is exactly the kind of
/// platform drift this rewrite cannot risk. Pure integer arithmetic runs identically everywhere.
public enum Dates {

    // MARK: - Gregorian, as an absolute day number

    private static let GML = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    private static func gLeap(_ y: Int) -> Bool { (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 }
    private static func gML(_ m: Int, _ y: Int) -> Int { (m == 2 && gLeap(y)) ? 29 : GML[m - 1] }

    private static func gAbs(_ y: Int, _ m: Int, _ d: Int) -> Int {
        var a = 365 * (y - 1) + (y - 1) / 4 - (y - 1) / 100 + (y - 1) / 400
        for i in 0..<(m - 1) { a += GML[i] }
        if m > 2 && gLeap(y) { a += 1 }
        return a + d
    }
    private static func absToG(_ abs: Int) -> (y: Int, m: Int, d: Int) {
        var y = abs / 366
        while abs >= gAbs(y + 1, 1, 1) { y += 1 }
        var m = 1
        while abs > gAbs(y, m, gML(m, y)) { m += 1 }
        return (y, m, abs - gAbs(y, m, 1) + 1)
    }

    public static func keyOf(y: Int, m: Int, d: Int) -> String {
        String(format: "%04d-%02d-%02d", y, m, d)
    }
    private static func parts(ofKey key: String) -> (y: Int, m: Int, d: Int) {
        let p = key.split(separator: "-").map { Int($0) ?? 0 }
        return (p[0], p.count > 1 ? p[1] : 1, p.count > 2 ? p[2] : 1)
    }
    private static func absOfKey(_ key: String) -> Int {
        let p = parts(ofKey: key)
        return gAbs(p.y, p.m, p.d)
    }
    private static func keyFromAbs(_ abs: Int) -> String {
        let g = absToG(abs)
        return keyOf(y: g.y, m: g.m, d: g.d)
    }

    public static func shiftKey(_ key: String, _ days: Int) -> String { keyFromAbs(absOfKey(key) + days) }

    /// Same 6am rollover rule as the website and the Android app: a check after midnight still
    /// belongs to last night.
    public static func tonightKey(now: Date = Date()) -> String {
        let cal = Calendar.current
        let hour = cal.component(.hour, from: now)
        let effective = hour < 6 ? cal.date(byAdding: .day, value: -1, to: now)! : now
        let c = cal.dateComponents([.year, .month, .day], from: effective)
        return keyOf(y: c.year!, m: c.month!, d: c.day!)
    }

    public static func longDate(_ key: String) -> String {
        let p = parts(ofKey: key)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        var c = DateComponents()
        c.year = p.y; c.month = p.m; c.day = p.d; c.hour = 12
        let d = cal.date(from: c)!
        let dow = cal.component(.weekday, from: d)
        return "\(cal.weekdaySymbols[dow - 1]), \(cal.monthSymbols[p.m - 1]) \(p.d)"
    }

    // MARK: - Hebrew calendar

    private static let HEB_EPOCH = -1373427
    private static func hLeap(_ y: Int) -> Bool { ((y * 7 + 1) % 19) < 7 }
    private static func hMonths(_ y: Int) -> Int { hLeap(y) ? 13 : 12 }

    private static func hElapsed(_ y: Int) -> Int {
        let me = (235 * y - 234) / 19
        let pe = 12084 + 13753 * me
        var d = 29 * me + pe / 25920
        if ((3 * (d + 1)) % 7) < 3 { d += 1 }
        return d
    }
    private static func hNewYear(_ y: Int) -> Int {
        let l = hElapsed(y - 1), p = hElapsed(y), n = hElapsed(y + 1)
        var dl = 0
        if n - p == 356 { dl = 2 } else if p - l == 382 { dl = 1 }
        return HEB_EPOCH + p + dl
    }
    private static func hYearLen(_ y: Int) -> Int { hNewYear(y + 1) - hNewYear(y) }

    private static func hML(_ m: Int, _ y: Int) -> Int {
        if m == 2 || m == 4 || m == 6 || m == 10 || m == 13 { return 29 }
        if m == 12 && !hLeap(y) { return 29 }
        if m == 8 && hYearLen(y) % 10 != 5 { return 29 }
        if m == 9 && hYearLen(y) % 10 == 3 { return 29 }
        return 30
    }

    private static func hToAbs(_ y: Int, _ m: Int, _ d: Int) -> Int {
        var a = d
        if m < 7 {
            for i in 7...hMonths(y) { a += hML(i, y) }
            if m > 1 { for i in 1..<m { a += hML(i, y) } }
        } else {
            for i in 7..<m { a += hML(i, y) }
        }
        return a + hNewYear(y) - 1
    }

    private static func absToH(_ abs: Int) -> (y: Int, m: Int, d: Int) {
        var y = (abs - HEB_EPOCH) / 366
        while abs >= hToAbs(y + 1, 7, 1) { y += 1 }
        var m = abs < hToAbs(y, 1, 1) ? 7 : 1
        while abs > hToAbs(y, m, hML(m, y)) { m += 1 }
        return (y, m, abs - hToAbs(y, m, 1) + 1)
    }

    /// Month indices run 1=Nisan...6=Elul(wait, see array), 7=Tishrei...13=Adar II - the order the
    /// Hebrew year is actually laid out in starting from Nisan, matching the website exactly so the
    /// month-length table above lines up with it.
    private static let HM = ["", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול", "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳"]
    private static let HME = ["", "Nissan", "Iyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Teves", "Shevat", "Adar", "Adar II"]
    private static func hMName(_ m: Int, _ y: Int) -> String { (m == 12 && hLeap(y)) ? "אדר א׳" : HM[m] }
    private static func hMNameEn(_ m: Int, _ y: Int) -> String { (m == 12 && hLeap(y)) ? "Adar I" : HME[m] }

    private static let HEB_NUM: [(Int, String)] = [
        (400, "ת"), (300, "ש"), (200, "ר"), (100, "ק"), (90, "צ"), (80, "פ"), (70, "ע"),
        (60, "ס"), (50, "נ"), (40, "מ"), (30, "ל"), (20, "כ"), (10, "י"), (9, "ט"), (8, "ח"),
        (7, "ז"), (6, "ו"), (5, "ה"), (4, "ד"), (3, "ג"), (2, "ב"), (1, "א")
    ]
    public static func hebNum(_ n: Int) -> String {
        if n == 15 { return "טו" }
        if n == 16 { return "טז" }
        var v = n
        var s = ""
        while v > 0 {
            let (amt, ch) = HEB_NUM.first { v >= $0.0 }!
            s += ch; v -= amt
        }
        return s
    }
    private static func gershayim(_ s: String) -> String {
        s.count == 1 ? s + "׳" : String(s.dropLast()) + "״" + String(s.last!)
    }

    private struct HebParts { let y: Int, m: Int, d: Int }
    /// [key] is an evening; the Hebrew day that evening belongs to is the raw conversion's NEXT
    /// Hebrew day, one ahead of whatever daytime the Gregorian date itself overlaps.
    private static func hebParts(_ key: String) -> HebParts {
        let h = absToH(absOfKey(key) + 1)
        return HebParts(y: h.y, m: h.m, d: h.d)
    }

    public static func hebrewDate(_ key: String) -> String {
        let h = hebParts(key)
        return "\(gershayim(hebNum(h.d))) \(hMName(h.m, h.y)) \(gershayim(hebNum(h.y % 1000)))"
    }

    /// Day and month with no year - how the date is written at the top of a nightly report.
    public static func hebrewDayMonth(_ key: String) -> String {
        let h = hebParts(key)
        return "\(gershayim(hebNum(h.d))) \(hMName(h.m, h.y))"
    }

    /// What to call this night in Hebrew. A Hebrew day starts at nightfall, so the weekday rolls
    /// with the date - Wednesday evening is כ״א אלול, and the weekday that goes with כ״א אלול is
    /// חמישי, so the night is called ליל חמישי. Saturday night gets its own name, מוצאי שבת. This is
    /// plain Gregorian day-of-week arithmetic (no Hebrew calendar involved), so it is computed the
    /// same way regardless of which Hebrew calendar backs the rest of the file.
    /// Absolute day 1 (0001-01-01 proleptic Gregorian) is a Monday in this scheme, so
    /// abs%7: 1=Mon,2=Tue,3=Wed,4=Thu,5=Fri,6=Sat,0=Sun - which is already 0-Sunday-indexed.
    private static func sundayIndexedWeekday(_ abs: Int) -> Int { ((abs % 7) + 7) % 7 }

    public static func hebrewNightName(_ key: String) -> String {
        switch sundayIndexedWeekday(absOfKey(key) + 1) {
        case 0: return "מוצאי שבת"   // Sunday
        case 1: return "ליל שני"     // Monday
        case 2: return "ליל שלישי"   // Tuesday
        case 3: return "ליל רביעי"   // Wednesday
        case 4: return "ליל חמישי"   // Thursday
        case 5: return "ליל שישי"    // Friday
        default: return "ליל שבת"    // Saturday
        }
    }

    public struct HebMonth: Equatable { public let year: Int; public let month: Int }

    public static func hebMonthOf(_ key: String) -> HebMonth {
        let h = hebParts(key)
        return HebMonth(year: h.y, month: h.m)
    }
    private static func hNext(_ y: Int, _ m: Int) -> (y: Int, m: Int) {
        let mx = hMonths(y)
        if m == 6 { return (y + 1, 7) }
        if m == mx { return (y, 1) }
        return (y, m + 1)
    }
    private static func hPrev(_ y: Int, _ m: Int) -> (y: Int, m: Int) {
        if m == 7 { return (y - 1, 6) }
        if m == 1 { return (y, hMonths(y)) }
        return (y, m - 1)
    }
    public static func nextHebMonth(_ m: HebMonth) -> HebMonth {
        let n = hNext(m.year, m.month); return HebMonth(year: n.y, month: n.m)
    }
    public static func prevHebMonth(_ m: HebMonth) -> HebMonth {
        let n = hPrev(m.year, m.month); return HebMonth(year: n.y, month: n.m)
    }
    public static func hebMonthName(_ m: HebMonth) -> String { hMName(m.month, m.year) }
    public static func hebMonthNameEn(_ m: HebMonth) -> String { hMNameEn(m.month, m.year) }
    public static func hebYearStr(_ year: Int) -> String { gershayim(hebNum(year % 1000)) }

    public struct CalDay { public let hebDay: Int; public let dateKey: String; public let dayOfWeek: Int } // 0=Sun..6=Sat

    /// All nights (dateKeys) that fall in this Hebrew month, in order.
    public static func daysInHebMonth(_ m: HebMonth) -> [CalDay] {
        let length = hML(m.month, m.year)
        var out: [CalDay] = []
        for day in 1...length {
            let abs = hToAbs(m.year, m.month, day)
            let nightKey = keyFromAbs(abs - 1) // inverse of hebrewDate()'s +1 - the evening this day begins on
            let dow = sundayIndexedWeekday(abs) // this Hebrew day's own daytime weekday, not the night before it
            out.append(CalDay(hebDay: day, dateKey: nightKey, dayOfWeek: dow))
        }
        return out
    }
}
