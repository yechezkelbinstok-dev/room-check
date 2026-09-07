import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import RoomCheckCore

/// An in-process stand-in for the Cloudflare Worker, implementing the exact same `/push` and
/// `/pull` contract (merge on push, an index of when each night last changed, filter `/pull` by
/// `since`) using the same `Merge` type the real server, the website, and the Android app share.
///
/// This does not re-prove the merge rule is correct - MergeFixturesTests already does that across
/// all three other implementations. What this proves is that SyncClient talks the protocol
/// correctly: it sends the right shape, reads the response the right way, and adopts what comes
/// back without losing its own edits.
final class MockSyncServer {
    private var nights: [String: [String: Any]] = [:]
    private var updatedAt: [String: Int64] = [:]
    var token = "secret"
    private(set) var requestCount = 0
    var forceStatus: Int?

    func handle(path: String, authHeader: String?, bodyData: Data) -> (Int, Data) {
        requestCount += 1
        if let forced = forceStatus { return (forced, Data("{}".utf8)) }
        let bearer = authHeader?.replacingOccurrences(of: "Bearer ", with: "")
        guard bearer == token else { return (401, Data("{\"error\":\"unauthorized\"}".utf8)) }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let body = (try? JSONSerialization.jsonObject(with: bodyData) as? [String: Any]) ?? [:]

        if path.hasSuffix("/push") {
            let incoming = (body["nights"] as? [String: Any]) ?? [:]
            var merged: [String: Any] = [:]
            for (date, raw) in incoming {
                guard let obj = raw as? [String: Any] else { continue }
                let stored = nights[date].map { Night.fromJSONObject($0) } ?? Night()
                let out = Merge.merge(stored, Night.fromJSONObject(obj))
                nights[date] = out.toJSONObject()
                updatedAt[date] = now
                merged[date] = out.toJSONObject()
            }
            let resp: [String: Any] = ["now": now, "nights": merged]
            return (200, (try? JSONSerialization.data(withJSONObject: resp)) ?? Data())
        }

        if path.hasSuffix("/pull") {
            let since = (body["since"] as? NSNumber)?.int64Value ?? 0
            var out: [String: Any] = [:]
            for (date, at) in updatedAt where at > since {
                if let n = nights[date] { out[date] = n }
            }
            let resp: [String: Any] = ["now": now, "nights": out]
            return (200, (try? JSONSerialization.data(withJSONObject: resp)) ?? Data())
        }

        return (404, Data("{\"error\":\"not found\"}".utf8))
    }
}

/// Routes URLSession traffic in this test process to a MockSyncServer instead of the network.
final class MockURLProtocol: URLProtocol {
    static var server: MockSyncServer?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let server = Self.server, let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        let body = request.httpBodyStream.map { stream -> Data in
            stream.open(); defer { stream.close() }
            var data = Data()
            let bufSize = 4096
            var buf = [UInt8](repeating: 0, count: bufSize)
            while stream.hasBytesAvailable {
                let read = stream.read(&buf, maxLength: bufSize)
                if read <= 0 { break }
                data.append(buf, count: read)
            }
            return data
        } ?? (request.httpBody ?? Data())

        let (status, respData) = server.handle(
            path: url.path,
            authHeader: request.value(forHTTPHeaderField: "Authorization"),
            bodyData: body
        )
        let resp = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: resp, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: respData)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

func makeMockedSession(_ server: MockSyncServer) -> URLSession {
    MockURLProtocol.server = server
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [MockURLProtocol.self]
    return URLSession(configuration: config)
}
