import Foundation

/// The `adb` binary: the one bundled in the app (mac/build.sh copies it in), else a usual install.
/// Every call is one process; nothing stays running but adb's own server.
struct ADB {
    let path: String

    struct Output {
        let code: Int32
        let data: Data
        let error: String
        var text: String { String(decoding: data, as: UTF8.self).replacingOccurrences(of: "\r", with: "") }
        var ok: Bool { code == 0 }
    }

    enum Failure: LocalizedError {
        case notFound
        var errorDescription: String? {
            "Не найден adb. Он входит в приложение; при запуске из исходников поставьте его: brew install android-platform-tools"
        }
    }

    static func locate() -> ADB? {
        var candidates: [String] = []
        if let res = Bundle.main.resourcePath { candidates.append(res + "/adb") }
        candidates += ["/opt/homebrew/bin/adb", "/usr/local/bin/adb",
                       NSHomeDirectory() + "/Library/Android/sdk/platform-tools/adb"]
        for c in candidates where FileManager.default.isExecutableFile(atPath: c) { return ADB(path: c) }
        return nil
    }

    /// Runs adb with `args`; stdout comes back as bytes (screenshots and icons are binary).
    func run(_ args: [String], timeout: TimeInterval = 120) async -> Output {
        await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let p = Process()
                p.executableURL = URL(fileURLWithPath: path)
                p.arguments = args
                let out = Pipe(), err = Pipe()
                p.standardOutput = out
                p.standardError = err
                p.standardInput = FileHandle.nullDevice
                do { try p.run() } catch {
                    cont.resume(returning: Output(code: -1, data: Data(), error: error.localizedDescription))
                    return
                }
                let killer = DispatchWorkItem { if p.isRunning { p.terminate() } }
                DispatchQueue.global().asyncAfter(deadline: .now() + timeout, execute: killer)
                // Read both pipes to the end before waiting, so a large output never blocks the child.
                var errData = Data()
                let group = DispatchGroup()
                group.enter()
                DispatchQueue.global().async {
                    errData = err.fileHandleForReading.readDataToEndOfFile()
                    group.leave()
                }
                let data = out.fileHandleForReading.readDataToEndOfFile()
                group.wait()
                p.waitUntilExit()
                killer.cancel()
                cont.resume(returning: Output(code: p.terminationStatus, data: data,
                                              error: String(decoding: errData, as: UTF8.self)))
            }
        }
    }
}

/// One TV, by its adb serial ("192.168.1.20:5555").
struct Device {
    let adb: ADB
    let serial: String

    func adb(_ args: [String], timeout: TimeInterval = 120) async -> ADB.Output {
        await adb.run(["-s", serial] + args, timeout: timeout)
    }

    /// One shell command (one `pm` per call: a chain can stop quietly after the first).
    func shell(_ command: String, timeout: TimeInterval = 120) async -> ADB.Output {
        await adb(["shell", command], timeout: timeout)
    }

    /// Bytes from a command, untouched by the terminal (exec-out).
    func bytes(_ command: String) async -> Data {
        await adb(["exec-out", command]).data
    }

    func push(_ local: URL, to remote: String) async -> ADB.Output {
        await adb(["push", local.path, remote], timeout: 600)
    }
}
