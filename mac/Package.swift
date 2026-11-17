// swift-tools-version:5.10
// CleanMySaluteTV for Mac: clean up a Sber Salute TV and set up the launcher over Wi-Fi (adb).
// Build the .app with mac/build.sh; `swift run` works for development.
import PackageDescription

let package = Package(
    name: "CleanMySaluteTV",
    defaultLocalization: "ru",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(name: "CleanMySaluteTV", path: "Sources/CleanMySaluteTV")
    ]
)
