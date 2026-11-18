import AppKit
import Foundation
import Network

/// Everything the window shows about the TV, and every action on it. One TV at a time.
@MainActor
final class TVStore: ObservableObject {
    static let pkg = "com.rookmeister.cleanmysalutetv"
    static let homeActivity = pkg + "/.MainActivity"
    static let dream = pkg + "/.ClockDream"
    static let providerURI = "content://" + pkg + ".theme"
    private static var provider: String { providerURI }

    // connection
    @Published var host: String = UserDefaults.standard.string(forKey: "host") ?? ""
    @Published private(set) var device: Device?
    @Published var busy: String?                       // what is running now, for the status bar
    @Published var message: String?                    // the last result or error, shown once
    @Published private(set) var found: [String] = []   // TVs with adb open on the network

    // the TV
    @Published private(set) var model = ""
    @Published private(set) var android = ""
    @Published private(set) var freeSpace = ""
    @Published var launcherVersion: String?
    @Published var homeIsOurs = false
    @Published private(set) var screenshot: NSImage?

    // clean-up
    @Published private(set) var profile: [ProfileEntry] = []
    @Published private(set) var installed: Set<String> = []
    @Published private(set) var disabled: Set<String> = []
    private(set) var never: Set<String> = []

    // the launcher's settings: what the TV has, and the edits not yet sent
    @Published private(set) var state: LauncherState?
    @Published var draft: Choices?
    @Published var home: [TVApp] = []
    @Published var hidden: [TVApp] = []
    @Published private(set) var icons: [String: NSImage] = [:]
    /// The photo sent as wallpaper from this Mac, for the preview.
    @Published var wallpaper: NSImage?
    /// The last change is on its way to the TV, or there.
    @Published private(set) var saving = false
    private var sentHome: [String] = [], sentHidden: [String] = []

    let adb = ADB.locate()

    init() {
        // Sber first; the same package in a later profile is skipped. Only what is on the TV is shown.
        var seen = Set<String>()
        profile = Self.profiles.flatMap { ProfileEntry.parse(Bundled.text($0)) }.filter { seen.insert($0.package).inserted }
        never = ProfileEntry.parseNever(Bundled.text("never.txt"))
    }

    /// profiles/*.tsv shipped with the app (mac/build.sh): Sber, Xiaomi TVs, Mi Box, any Android TV / Google TV.
    static let profiles = ["sber-staros.tsv", "xiaomi-tv.tsv", "xiaomi-mibox.tsv", "android-tv.tsv"]

    var connected: Bool { device != nil }
    var dirty: Bool {
        guard let s = state, let d = draft else { return false }
        return d != s.choices || home.map(\.key) != sentHome || hidden.map(\.key) != sentHidden
    }

    // MARK: - connection

    func connect() async {
        guard let adb else { message = ADB.Failure.notFound.errorDescription; return }
        var h = host.trimmingCharacters(in: .whitespaces)
        guard !h.isEmpty else { message = "Введите IP-адрес телевизора"; return }
        if !h.contains(":") { h += ":5555" }
        busy = "Подключаюсь к \(h)…"
        defer { busy = nil }
        _ = await adb.run(["disconnect", h], timeout: 10)
        let out = await adb.run(["connect", h], timeout: 20)
        let state = await adb.run(["-s", h, "get-state"], timeout: 15)
        guard state.text.trimmingCharacters(in: .whitespacesAndNewlines) == "device" else {
            device = nil
            let why = out.text.contains("unauthorized") || state.error.contains("unauthorized")
                ? "Подтвердите отладку на экране ТВ («Разрешить») и нажмите «Подключить» ещё раз."
                : "ТВ не ответил (\(out.text.trimmingCharacters(in: .whitespacesAndNewlines))). Проверьте, что он включён и отладка по сети включена."
            message = why
            return
        }
        UserDefaults.standard.set(host, forKey: "host")
        device = Device(adb: adb, serial: h)
        message = nil
        await refreshAll()
    }

    func disconnect() async {
        if let d = device { _ = await d.adb.run(["disconnect", d.serial], timeout: 10) }
        device = nil
        state = nil
        draft = nil
    }

    /// Looks for TVs with adb (port 5555) open on this Mac's network, a /24 around its own address.
    func scan() async {
        guard let base = Self.localPrefix() else { message = "Не удалось узнать адрес Mac в сети"; return }
        busy = "Ищу телевизоры в сети \(base).0/24…"
        defer { busy = nil }
        found = await withTaskGroup(of: String?.self) { group in
            for i in 1...254 {
                let ip = "\(base).\(i)"
                group.addTask { await Self.portOpen(ip, 5555) ? ip : nil }
            }
            var hits: [String] = []
            for await r in group { if let r { hits.append(r) } }
            return hits.sorted { $0.compare($1, options: .numeric) == .orderedAscending }
        }
        if found.isEmpty { message = "Не нашёл ТВ с открытой отладкой (порт 5555)" }
        else if host.isEmpty, let first = found.first { host = first }
    }

    nonisolated static func portOpenPublic(_ ip: String, _ port: UInt16) async -> Bool { await portOpen(ip, port) }

    nonisolated private static func portOpen(_ ip: String, _ port: UInt16) async -> Bool {
        /// Resumes the continuation exactly once, whichever of "connected", "failed" or the timeout comes first.
        final class Once: @unchecked Sendable {
            private let lock = NSLock()
            private var done = false
            func run(_ body: () -> Void) {
                lock.lock(); defer { lock.unlock() }
                if done { return }
                done = true
                body()
            }
        }
        return await withCheckedContinuation { cont in
            let conn = NWConnection(host: NWEndpoint.Host(ip), port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
            let once = Once()
            let finish: @Sendable (Bool) -> Void = { ok in
                once.run {
                    conn.cancel()
                    cont.resume(returning: ok)
                }
            }
            conn.stateUpdateHandler = { st in
                switch st {
                case .ready: finish(true)
                case .failed, .cancelled: finish(false)
                default: break
                }
            }
            conn.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 1.2) { finish(false) }
        }
    }

    nonisolated private static func localPrefix() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }
        for p in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let a = p.pointee
            guard let sa = a.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: a.ifa_name)
            guard name.hasPrefix("en") else { continue }
            var buf = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            getnameinfo(sa, socklen_t(sa.pointee.sa_len), &buf, socklen_t(buf.count), nil, 0, NI_NUMERICHOST)
            let ip = String(cString: buf)
            let parts = ip.split(separator: ".")
            if parts.count == 4, !ip.hasPrefix("169.254") { return parts.prefix(3).joined(separator: ".") }
        }
        return nil
    }

    // MARK: - the TV

    func refreshAll() async {
        await refreshInfo()
        await refreshPackages()
        await loadLauncher()
    }

    func refreshInfo() async {
        guard let d = device else { return }
        model = await d.shell("getprop ro.product.model").text.trimmingCharacters(in: .whitespacesAndNewlines)
        android = await d.shell("getprop ro.build.version.release").text.trimmingCharacters(in: .whitespacesAndNewlines)
        let df = await d.shell("df -h /data").text.split(separator: "\n").last.map(String.init) ?? ""
        let cols = df.split(separator: " ").map(String.init)
        freeSpace = cols.count >= 4 ? "свободно \(cols[3]) из \(cols[1])" : ""
        let v = await d.shell("dumpsys package \(Self.pkg) | grep -m1 versionName").text
        launcherVersion = v.split(separator: "=").last.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        let homeNow = await d.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").text
        homeIsOurs = homeNow.split(separator: "\n").last.map { $0.trimmingCharacters(in: .whitespaces) == Self.homeActivity } ?? false
    }

    func takeScreenshot() async {
        guard let d = device else { return }
        busy = "Снимаю экран…"
        defer { busy = nil }
        let png = await d.bytes("screencap -p")
        screenshot = NSImage(data: png)
        if screenshot == nil { message = "Экран не снялся (идёт защищённое видео или вход HDMI?)" }
    }

    // MARK: - clean-up

    func refreshPackages() async {
        guard let d = device else { return }
        func list(_ flag: String) async -> Set<String> {
            Set(await d.shell("pm list packages \(flag)").text.split(separator: "\n").compactMap { line in
                line.hasPrefix("package:") ? String(line.dropFirst(8)).trimmingCharacters(in: .whitespaces) : nil
            })
        }
        installed = await list("")
        disabled = await list("-d")
    }

    /// profiles/guard.sh run on the TV (cleanyourtv's safety checks: core Android, the last launcher, HDMI inputs,
    /// Settings, the setup wizard's Wi-Fi). Returns the refused packages with the reason, or nil if the TV did not answer.
    func guardCheck(_ pkgs: [String]) async -> [String: String]? {
        guard let d = device, let script = Bundled.url("guard.sh") else { return nil }
        let names = pkgs.filter { $0.range(of: #"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$"#, options: .regularExpression) != nil }
        guard !names.isEmpty else { return [:] }
        guard (await d.push(script, to: "/data/local/tmp/cmst_guard.sh")).ok else { return nil }
        let out = await d.shell("sh /data/local/tmp/cmst_guard.sh " + names.joined(separator: " "))
        var refused: [String: String] = [:], answered = Set<String>()
        for line in out.text.split(separator: "\n") {
            let f = line.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ", maxSplits: 2).map(String.init)
            guard f.count >= 2 else { continue }
            answered.insert(f[1])
            if f[0] == "NO" { refused[f[1]] = f.count > 2 ? f[2] : "" }
        }
        for p in names where !answered.contains(p) { refused[p] = "проверка на ТВ не ответила" }
        return refused
    }

    func setDisabled(_ pkg: String, _ off: Bool, checked: Bool = false) async {
        guard let d = device else { return }
        if off && never.contains(pkg) { message = "\(pkg) нужен системе, его не отключаю"; return }
        if off && !checked {
            guard let refused = await guardCheck([pkg]) else { message = "Не удалось проверить \(pkg) на ТВ, не отключаю"; return }
            if let why = refused[pkg] { message = "\(pkg) не отключаю: \(why)"; return }
        }
        busy = off ? "Отключаю \(pkg)…" : "Включаю \(pkg)…"
        defer { busy = nil }
        if off { await backup() }
        let out = await d.shell(off ? "pm disable-user --user 0 \(pkg)" : "pm enable --user 0 \(pkg)")
        if out.text.contains(off ? "disabled-user" : "enabled") {
            noteDisabled(pkg, off)
        } else {
            message = "\(pkg): \(out.text.isEmpty ? out.error : out.text)"
        }
        await refreshPackages()
    }

    /// Switches off every package the profile marks "disable" that is on the TV and still on.
    func disableRecommended() async {
        let todo = profile.filter { $0.action == .disable && installed.contains($0.package) && !disabled.contains($0.package) && !never.contains($0.package) }
        guard let refused = await guardCheck(todo.map(\.package)) else {
            message = "Не удалось проверить список на ТВ: ничего не отключено"; return
        }
        for e in todo where refused[e.package] == nil { await setDisabled(e.package, true, checked: true) }
        message = refused.isEmpty ? "Рекомендуемые приложения отключены"
            : "Отключено, кроме \(refused.count): " + refused.map { "\($0.key) — \($0.value)" }.sorted().joined(separator: "; ")
    }

    /// Switches back on every profile package that is off.
    func enableAll() async {
        for e in profile where disabled.contains(e.package) { await setDisabled(e.package, false) }
        message = "Все приложения из списка снова включены"
    }

    // MARK: - the launcher

    /// Puts the APK on the TV and makes it Home, as scripts/install.sh does.
    func installLauncher(_ apk: URL) async {
        guard let d = device else { return }
        busy = "Устанавливаю лаунчер…"
        defer { busy = nil }
        // push + pm install: `adb install` can wait for a confirmation on screen on newer Android
        _ = await d.push(apk, to: "/data/local/tmp/cmst.apk")
        let out = await d.shell("pm install -r /data/local/tmp/cmst.apk", timeout: 300)
        _ = await d.shell("rm -f /data/local/tmp/cmst.apk")
        guard out.text.contains("Success") else {
            message = out.text.contains("UPDATE_INCOMPATIBLE")
                ? "На ТВ стоит CleanMySaluteTV с другой подписью: удалите его и поставьте снова."
                : "Установка не удалась: \(out.text) \(out.error)"
            return
        }
        _ = await d.shell("pm grant \(Self.pkg) android.permission.ACCESS_FINE_LOCATION")
        _ = await d.shell("dpm set-active-admin --user 0 '\(Self.pkg)/.PowerControl$Admin'")
        _ = await d.shell("appops set \(Self.pkg) SCHEDULE_EXACT_ALARM allow")
        await makeHome()
        message = "Лаунчер установлен"
        await refreshAll()
    }

    func makeHome() async {
        guard let d = device else { return }
        _ = await d.shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n \(Self.homeActivity)")
        _ = await d.shell("cmd package set-home-activity --user 0 \(Self.homeActivity)")
        await refreshInfo()
        if !homeIsOurs { message = "Кнопка Home всё ещё открывает другой лаунчер" }
    }

    func useClockScreensaver() async {
        guard let d = device else { return }
        _ = await d.shell("settings put secure screensaver_components \(Self.dream)")
        _ = await d.shell("settings put secure screensaver_enabled 1")
        message = "Заставка: часы CleanMySaluteTV"
    }

    // MARK: - the launcher's settings (RemoteSettings on the TV)

    func loadLauncher() async {
        guard let d = device, launcherVersion != nil else { state = nil; return }
        busy = "Читаю настройки лаунчера…"
        defer { busy = nil }
        let call = await d.shell("content call --uri \(Self.provider) --method export")
        guard call.text.contains("ok=true") else {
            state = nil
            message = "Лаунчер не отдал настройки. Обновите его в разделе «Лаунчер». (\(call.text))"
            return
        }
        let json = await d.bytes("content read --uri \(Self.provider)/remote/state.json")
        do {
            let s = try JSONDecoder().decode(LauncherState.self, from: json)
            state = s
            draft = s.choices
            home = s.apps.home
            hidden = s.apps.hidden
            sentHome = home.map(\.key)
            sentHidden = hidden.map(\.key)
            // icons come after, one by one, without holding up the window
            Task { await loadIcons(s.apps.home + s.apps.hidden) }
        } catch {
            message = "Не разобрал настройки лаунчера: \(error.localizedDescription)"
        }
    }

    private func loadIcons(_ apps: [TVApp]) async {
        guard let d = device else { return }
        for a in apps where icons[a.package] == nil {
            let png = await d.bytes("content read --uri \(Self.provider)/remote/icons/\(a.package).png")
            if let img = NSImage(data: png) { icons[a.package] = img }
        }
    }

    /// Sends the edits: the changed settings and, when it changed, where the apps stand. Home rebuilds with them.
    /// The edits stay as they are in the window (no reload), so typing and dragging carry on undisturbed.
    func apply() async {
        guard let d = device, let s = state, let draft else { return }
        let changes = draft.changes(from: s.choices)
        let appsChanged = home.map(\.key) != sentHome || hidden.map(\.key) != sentHidden
        guard !changes.isEmpty || appsChanged else { return }
        saving = true
        defer { saving = false }
        var body: [String: Any] = ["choices": changes]
        if appsChanged { body["apps"] = ["home": home.map(\.key), "hidden": hidden.map(\.key)] }
        do {
            let data = try JSONSerialization.data(withJSONObject: body, options: [.prettyPrinted])
            let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("cmst-apply.json")
            try data.write(to: tmp)
            _ = await d.push(tmp, to: "/data/local/tmp/cmst-apply.json")
            _ = await d.shell("content write --uri \(Self.provider)/remote/apply.json < /data/local/tmp/cmst-apply.json; rm -f /data/local/tmp/cmst-apply.json")
            let call = await d.shell("content call --uri \(Self.provider) --method apply")
            guard call.text.contains("ok=true") else { message = "ТВ не применил: \(call.text)"; return }
            state?.choices = draft
            sentHome = home.map(\.key)
            sentHidden = hidden.map(\.key)
        } catch {
            message = "Не получилось: \(error.localizedDescription)"
        }
    }

    private var pendingApply: Task<Void, Never>?

    /// Every edit on the Home screen step goes to the TV by itself, a moment after the last one (a slider or typing
    /// settles first), as CleanMyTV does: there is no Apply button.
    func scheduleApply(after seconds: Double = 0.7) {
        pendingApply?.cancel()
        pendingApply = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled, let self, self.dirty else { return }
            await self.apply()
        }
    }

    func discardEdits() {
        guard let s = state else { return }
        draft = s.choices
        let all = Dictionary((s.apps.home + s.apps.hidden).map { ($0.key, $0) }, uniquingKeysWith: { a, _ in a })
        home = sentHome.compactMap { all[$0] }
        hidden = sentHidden.compactMap { all[$0] }
    }

    // MARK: - apps: the dock, More apps and hidden (the launcher's AppsPlan, here on the Mac side)

    var dockSize: Int { draft?.columns ?? state?.apps.docked ?? 6 }

    /// The most apps the dock takes with the tile size in the edits: 6 large, 9 small.
    var dockMax: Int {
        draft?.dockStyle == "large" ? (state?.dockMaxLarge ?? 6) : (state?.dockMaxSmall ?? state?.dockMax ?? 9)
    }
    var docked: [TVApp] { Array(home.prefix(dockSize)) }
    var more: [TVApp] { Array(home.dropFirst(dockSize)) }

    enum Place { case dock, more, hidden }

    func place(of app: TVApp) -> Place {
        guard let i = home.firstIndex(of: app) else { return .hidden }
        return i < dockSize ? .dock : .more
    }

    /// Moves an app to `place`: into a full dock at its end (the dock's last steps out to More apps), to More apps
    /// at its start, to hidden on top. The dock keeps its size, as on the TV.
    func move(_ app: TVApp, to target: Place) {
        guard place(of: app) != target else { return }
        home.removeAll { $0 == app }
        hidden.removeAll { $0 == app }
        switch target {
        case .dock: home.insert(app, at: min(home.count, max(0, dockSize - 1)))
        case .more: home.insert(app, at: min(home.count, dockSize))
        case .hidden: hidden.insert(app, at: 0)
        }
    }

    /// A drag inside the dock or inside More apps: `key` takes `target`'s place. Across the groups it does nothing
    /// (the place picker moves an app from one group to another).
    func moveApp(_ key: String, before target: String) -> Bool {
        guard let from = home.firstIndex(where: { $0.key == key }), let to = home.firstIndex(where: { $0.key == target }),
              from != to, (from < dockSize) == (to < dockSize) else { return false }
        let app = home.remove(at: from)
        home.insert(app, at: to)
        return true
    }

    /// Every app, for picking the VPN button's app.
    var allApps: [TVApp] { (home + hidden).sorted { $0.label.localizedCompare($1.label) == .orderedAscending } }
}
