import AppKit
import Foundation

/// The steps beyond the launcher's settings: backup and restore, the one-button run, TV extras, installing an app,
/// and finishing (restart check, closed debug port). The same jobs scripts/*.sh do, from the window.
extension TVStore {
    // MARK: - backup (scripts/backup.sh): what the TV had before we changed anything

    /// ~/Library/Application Support/CleanMySaluteTV/<TV address>/
    var backupDir: URL? {
        guard let d = device else { return nil }
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("CleanMySaluteTV/" + d.serial.replacingOccurrences(of: ":", with: "_"))
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func saved(_ name: String) -> String? {
        guard let u = backupDir?.appendingPathComponent(name) else { return nil }
        return (try? String(contentsOf: u, encoding: .utf8))?.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func save(_ name: String, _ text: String, keep: Bool = true) {
        guard let u = backupDir?.appendingPathComponent(name) else { return }
        if keep && FileManager.default.fileExists(atPath: u.path) { return }   // the first one is the original
        try? text.write(to: u, atomically: true, encoding: .utf8)
    }

    var hasBackup: Bool { saved("stock_home.txt") != nil }

    /// Packages this app switched off, newest last (restore turns them back on, newest first).
    var disabledByUs: [String] {
        (saved("disabled_by_us.txt") ?? "").split(separator: "\n").map(String.init).filter { !$0.isEmpty }
    }

    func noteDisabled(_ pkg: String, _ off: Bool) {
        var list = disabledByUs.filter { $0 != pkg }
        if off { list.append(pkg) }
        save("disabled_by_us.txt", list.joined(separator: "\n"), keep: false)
    }

    /// Takes the TV's state once, before the first change: Home, the screensaver, private DNS, the package lists.
    func backup() async {
        guard let d = device, !hasBackup else { return }
        busy = "Сохраняю бэкап…"
        defer { busy = nil }
        let homeNow = await d.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").text
        save("stock_home.txt", homeNow.split(separator: "\n").last.map(String.init) ?? "")
        save("screensaver.txt", [await setting("secure", "screensaver_components"), await setting("secure", "screensaver_enabled")].joined(separator: "\n"))
        save("dns.txt", [await setting("global", "private_dns_mode"), await setting("global", "private_dns_specifier")].joined(separator: "\n"))
        save("packages_disabled.txt", await d.shell("pm list packages -d").text)
        save("packages_all.txt", await d.shell("pm list packages -f").text)
        save("date.txt", ISO8601DateFormatter().string(from: Date()))
    }

    func setting(_ ns: String, _ key: String) async -> String {
        guard let d = device else { return "" }
        return await d.shell("settings get \(ns) \(key)").text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// scripts/restore.sh: everything this app switched off back on, the stock Home, screensaver and DNS as they were.
    func restoreAll() async {
        guard let d = device else { return }
        busy = "Возвращаю всё как было…"
        defer { busy = nil }
        for p in disabledByUs.reversed() { _ = await d.shell("pm enable --user 0 \(p)") }
        save("disabled_by_us.txt", "", keep: false)
        await useStockLauncher()
        if let s = saved("screensaver.txt")?.split(separator: "\n").map(String.init), s.count == 2 {
            if s[0] != "null" && !s[0].isEmpty { _ = await d.shell("settings put secure screensaver_components \(s[0])") }
            if s[1] != "null" && !s[1].isEmpty { _ = await d.shell("settings put secure screensaver_enabled \(s[1])") }
        }
        if let n = saved("dns.txt")?.split(separator: "\n", omittingEmptySubsequences: false).map(String.init), n.count == 2 {
            _ = await d.shell("settings put global private_dns_mode \(n[0] == "null" || n[0].isEmpty ? "opportunistic" : n[0])")
            if n[1] == "null" || n[1].isEmpty { _ = await d.shell("settings delete global private_dns_specifier") }
            else { _ = await d.shell("settings put global private_dns_specifier \(n[1])") }
        }
        await refreshPackages()
        await refreshInfo()
        message = "Всё возвращено, как было до CleanMySaluteTV"
    }

    /// The TV's own launcher as Home again (from the backup, else the Sber one).
    func useStockLauncher() async {
        guard let d = device else { return }
        let stock = saved("stock_home.txt").flatMap { $0.contains("/") && !$0.hasPrefix(Self.pkg) ? $0 : nil }
            ?? "ru.sberdevices.starlauncher/.ui.mainscreen.MainActivity"
        let pkg = String(stock.split(separator: "/").first ?? "")
        _ = await d.shell("pm enable --user 0 \(pkg)")
        _ = await d.shell("cmd package set-home-activity --user 0 \(stock)")
        _ = await d.shell("input keyevent KEYCODE_HOME")
        await refreshInfo()
        message = homeIsOurs ? "Home не переключился" : "Главный экран — снова лаунчер Sber"
    }

    // MARK: - step 1: everything with one button

    enum RunStage: String, CaseIterable { case backup, cleanup, install, home }

    /// Backup, the profile's "disable" packages off, the launcher in and made Home. `cleanup` false skips the
    /// clean-up. `progress` names each stage as it starts.
    func quickRun(cleanup: Bool, progress: @escaping (RunStage) -> Void) async -> Bool {
        guard connected else { message = "Сначала подключите телевизор"; return false }
        progress(.backup)
        await backup()
        if cleanup {
            progress(.cleanup)
            await disableRecommended()
        }
        progress(.install)
        if launcherVersion == nil || launcherOutdated, let apk = Bundled.apk {
            await installLauncher(apk)
        }
        progress(.home)
        if !homeIsOurs { await makeHome() }
        await loadLauncher()
        return homeIsOurs
    }

    /// The TV has an older launcher than the one this app carries.
    var launcherOutdated: Bool {
        guard let tv = launcherVersion, let mine = bundledVersion else { return false }
        return tv.compare(mine, options: .numeric) == .orderedAscending
    }

    var bundledVersion: String? {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
    }

    // MARK: - step 5: extras

    struct Extras {
        var animation: String = "1"        // 0, 0.5 or 1
        var dnsMode: String = "off"        // off, opportunistic, hostname
        var dnsHost: String = ""
        var screensaver: String = ""       // the screensaver component, "" when off
    }

    func readExtras() async -> Extras {
        var e = Extras()
        let a = await setting("global", "animator_duration_scale")
        e.animation = a == "null" || a.isEmpty ? "1" : a.replacingOccurrences(of: ".0", with: "")
        let m = await setting("global", "private_dns_mode")
        e.dnsMode = m == "null" || m.isEmpty ? "opportunistic" : m
        let h = await setting("global", "private_dns_specifier")
        e.dnsHost = h == "null" ? "" : h
        let on = await setting("secure", "screensaver_enabled")
        e.screensaver = on == "0" ? "" : await setting("secure", "screensaver_components")
        return e
    }

    /// Window, transition and animator scales together, as the TV's developer options set them.
    func setAnimation(_ scale: String) async {
        guard let d = device else { return }
        for k in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"] {
            _ = await d.shell("settings put global \(k) \(scale)")
        }
        message = "Скорость анимаций: \(scale == "0" ? "выключены" : scale == "0.5" ? "быстрые" : "обычные")"
    }

    /// Private DNS: a resolver that blocks ads in every app (AdGuard by default), or off (back to automatic).
    func setDNS(_ host: String?) async {
        guard let d = device else { return }
        await backup()
        if let host, !host.isEmpty {
            _ = await d.shell("settings put global private_dns_specifier \(host)")
            _ = await d.shell("settings put global private_dns_mode hostname")
            message = "Частный DNS: \(host)"
        } else {
            _ = await d.shell("settings put global private_dns_mode opportunistic")
            _ = await d.shell("settings delete global private_dns_specifier")
            message = "Частный DNS выключен"
        }
    }

    /// The screensaver: the launcher's clock, the TV's own (from the backup), or none.
    func setScreensaver(_ choice: String) async {
        guard let d = device else { return }
        await backup()
        switch choice {
        case "clock":
            await useClockScreensaver()
        case "original":
            let s = saved("screensaver.txt")?.split(separator: "\n").map(String.init) ?? []
            if let c = s.first, c != "null", !c.isEmpty { _ = await d.shell("settings put secure screensaver_components \(c)") }
            _ = await d.shell("settings put secure screensaver_enabled 1")
            message = "Заставка: как была на ТВ"
        default:
            _ = await d.shell("settings put secure screensaver_enabled 0")
            message = "Заставка выключена"
        }
    }

    /// Android's own cache trim: every app's cache, nothing else.
    func trimCaches() async {
        guard let d = device else { return }
        busy = "Чищу кэш приложений…"
        defer { busy = nil }
        let before = await d.shell("df /data").text
        _ = await d.shell("pm trim-caches 64G", timeout: 180)
        let after = await d.shell("df /data").text
        func free(_ s: String) -> Int { Int(s.split(separator: "\n").last?.split(separator: " ").dropFirst(3).first ?? "0") ?? 0 }
        let mb = max(0, free(after) - free(before)) / 1024
        message = "Кэш очищен, освободилось около \(mb) МБ"
        await refreshInfo()
    }

    // MARK: - step 6: install an app

    /// The package and version inside an APK, read on the Mac (aapt is not needed: the TV tells after install).
    func sideload(_ apk: URL) async -> Bool {
        guard let d = device else { return false }
        busy = "Устанавливаю \(apk.lastPathComponent)…"
        defer { busy = nil }
        _ = await d.push(apk, to: "/data/local/tmp/cmst-sideload.apk")
        let out = await d.shell("pm install -r /data/local/tmp/cmst-sideload.apk", timeout: 600)
        _ = await d.shell("rm -f /data/local/tmp/cmst-sideload.apk")
        if out.text.contains("Success") {
            message = "\(apk.deletingPathExtension().lastPathComponent) установлено"
            await refreshPackages()
            return true
        }
        message = "Не установилось: \(out.text.isEmpty ? out.error : out.text)"
        return false
    }

    // MARK: - step 7: finish

    /// Restarts the TV and waits for it to come back to the launcher. True when Home is ours after the restart.
    func restartCheck() async -> Bool {
        guard let d = device else { return false }
        busy = "Перезагружаю ТВ и жду главный экран (до 3 минут)…"
        defer { busy = nil }
        _ = await d.adb(["reboot"], timeout: 20)
        try? await Task.sleep(for: .seconds(20))
        for _ in 0..<32 {
            _ = await d.adb.run(["connect", d.serial], timeout: 8)
            let booted = await d.shell("getprop sys.boot_completed", timeout: 8).text.trimmingCharacters(in: .whitespacesAndNewlines)
            if booted == "1" {
                try? await Task.sleep(for: .seconds(8))
                await refreshInfo()
                let top = await d.shell("dumpsys window | grep -m1 mCurrentFocus").text
                return homeIsOurs && top.contains(Self.pkg)
            }
            try? await Task.sleep(for: .seconds(5))
        }
        message = "ТВ не вернулся за 3 минуты"
        return false
    }

    /// After debugging is off on the TV: nobody answers on port 5555 any more.
    func debugPortClosed() async -> Bool {
        let ip = (device?.serial ?? host).split(separator: ":").first.map(String.init) ?? host
        return !(await Self.portOpenPublic(ip, 5555))
    }

    // MARK: - wallpaper: a photo from the Mac instead of the scenes

    /// Puts the photo on the TV as wallpaper.jpg (the launcher's own wallpaper, behind the clock) and turns the
    /// scenes off so it shows. 1920×1080 JPEG, cropped to fill.
    func setWallpaper(_ image: NSImage) async {
        guard let d = device, let jpeg = Self.wallpaperJPEG(image) else { message = "Не получилось подготовить фото"; return }
        busy = "Отправляю обои на ТВ…"
        defer { busy = nil }
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("cmst-wallpaper.jpg")
        do { try jpeg.write(to: tmp) } catch { message = error.localizedDescription; return }
        _ = await d.push(tmp, to: "/data/local/tmp/cmst-wallpaper.jpg")
        _ = await d.shell("content write --uri \(Self.providerURI)/wallpaper.jpg < /data/local/tmp/cmst-wallpaper.jpg; rm -f /data/local/tmp/cmst-wallpaper.jpg")
        wallpaper = image
        draft?.scenes = false
        await apply()
    }

    /// Scenes again instead of the photo (the photo stays on the TV, unseen).
    func backToScenes() async {
        draft?.scenes = true
        await apply()
    }

    nonisolated static func wallpaperJPEG(_ image: NSImage) -> Data? {
        let w = 1920, h = 1080
        guard let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8,
                                         samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                                         colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0) else { return nil }
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
        let s = image.size
        let k = max(CGFloat(w) / s.width, CGFloat(h) / s.height)
        let dw = s.width * k, dh = s.height * k
        image.draw(in: NSRect(x: (CGFloat(w) - dw) / 2, y: (CGFloat(h) - dh) / 2, width: dw, height: dh))
        NSGraphicsContext.restoreGraphicsState()
        return rep.representation(using: .jpeg, properties: [.compressionFactor: 0.88])
    }
}
