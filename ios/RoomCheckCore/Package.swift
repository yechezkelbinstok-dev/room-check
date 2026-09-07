// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RoomCheckCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "RoomCheckCore", targets: ["RoomCheckCore"])
    ],
    targets: [
        .target(name: "RoomCheckCore"),
        .testTarget(
            name: "RoomCheckCoreTests",
            dependencies: ["RoomCheckCore"],
            resources: [.copy("Fixtures")]
        )
    ]
)
