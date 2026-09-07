import XCTest
@testable import RoomCheckCore

/// The same cases Merge.kt and merge.js are run against. The rule exists three times now, and two
/// copies that quietly disagree would lose marks silently rather than fail loudly - this is what
/// keeps all three honest.
final class MergeFixturesTests: XCTestCase {

    private func canonical(_ obj: [String: Any]) -> String {
        func norm(_ v: Any) -> String {
            if let d = v as? [String: Any] {
                return "{" + d.keys.sorted().map { "\($0):\(norm(d[$0]!))" }.joined(separator: ",") + "}"
            }
            if let a = v as? [Any] {
                return "[" + a.map(norm).sorted().joined(separator: ",") + "]"
            }
            if let b = v as? Bool { return b ? "true" : "false" }
            return "\(v)"
        }
        return norm(obj)
    }

    func testKotlinAndJSAgree() throws {
        let url = Bundle.module.url(forResource: "Fixtures/merge-fixtures", withExtension: "json")!
        let data = try Data(contentsOf: url)
        let root = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let cases = root["cases"] as! [[String: Any]]
        XCTAssertGreaterThan(cases.count, 0)

        for c in cases {
            let name = c["name"] as! String
            let local = Night.fromJSONObject(c["local"] as? [String: Any])
            let remote = Night.fromJSONObject(c["remote"] as? [String: Any])
            let want = canonical(c["expect"] as! [String: Any])

            let ab = Merge.merge(local, remote)
            XCTAssertEqual(want, canonical(ab.toJSONObject()), "\(name) (local,remote)")

            let ba = Merge.merge(remote, local)
            XCTAssertEqual(want, canonical(ba.toJSONObject()), "\(name) (remote,local)")
        }
    }
}
