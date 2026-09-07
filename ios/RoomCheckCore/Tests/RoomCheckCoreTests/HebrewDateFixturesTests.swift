import XCTest
@testable import RoomCheckCore

/// Foundation's Hebrew calendar and Android's `android.icu.util.HebrewCalendar` are both ICU
/// underneath, but the two platforms number months differently and the leap-year/Adar naming is
/// exactly the kind of place a silent off-by-one hides. Rather than trust the arithmetic by
/// construction, this diffs the Swift output against 2,232 real dates - six years, crossing
/// several 19-year leap cycles - dumped straight from the Kotlin app's own Dates.kt.
final class HebrewDateFixturesTests: XCTestCase {
    private struct Row: Decodable { let key: String; let heb: String; let dayMonth: String; let night: String }

    func testMatchesTheAndroidAppExactly() throws {
        let url = Bundle.module.url(forResource: "Fixtures/hebrew-fixtures", withExtension: "json")!
        let data = try Data(contentsOf: url)
        let rows = try JSONDecoder().decode([Row].self, from: data)
        XCTAssertGreaterThan(rows.count, 2000)

        var mismatches: [String] = []
        for row in rows {
            let heb = Dates.hebrewDate(row.key)
            let dayMonth = Dates.hebrewDayMonth(row.key)
            let night = Dates.hebrewNightName(row.key)
            if heb != row.heb { mismatches.append("\(row.key): heb got \(heb) want \(row.heb)") }
            if dayMonth != row.dayMonth { mismatches.append("\(row.key): dayMonth got \(dayMonth) want \(row.dayMonth)") }
            if night != row.night { mismatches.append("\(row.key): night got \(night) want \(row.night)") }
            if mismatches.count > 20 { break } // enough to diagnose without flooding the log
        }
        XCTAssertTrue(mismatches.isEmpty, "\(mismatches.count)+ mismatches, first ones:\n" + mismatches.joined(separator: "\n"))
    }
}
