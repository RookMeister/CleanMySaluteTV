import Foundation

/// What the launcher sends over (RemoteSettings.export on the TV: remote/state.json).
struct LauncherState: Decodable {
    var version: String
    var choices: Choices
    var scenes: [String]
    /// Scenes whose clock goes behind the picture's hills and trees (a cut-out over it); the rest are on top.
    var scenesBehind: [String]?
    /// Where those scenes keep their clock (couch-tv's place, 1080-line pixels): the position choice moves only photos.
    var sceneClocks: [String: SceneClock]?
    var buttons: [TopButton]
    var apps: Apps
    var dockMin: Int
    var dockMax: Int
    /// The most apps for each dock size (6 large, 9 small); older launchers send only dockMax.
    var dockMaxSmall: Int?
    var dockMaxLarge: Int?

    struct SceneClock: Decodable, Hashable {
        var top: Double
        var size: Double
        var align: String
    }

    struct TopButton: Decodable, Hashable {
        var kind: String
        var label: String
    }

    struct Apps: Decodable {
        var home: [TVApp]
        var hidden: [TVApp]
        var docked: Int
    }
}

struct TVApp: Decodable, Hashable, Identifiable {
    var key: String
    var package: String
    var label: String
    var id: String { key }
}

/// The settings the Mac app can change, as the launcher names them (OnTvChoices and Config keys).
struct Choices: Codable, Equatable {
    var scenes: Bool
    var sceneSeconds: Int
    var sceneList: String
    var clockPosition: String
    var weather: Bool
    var weatherPlace: String
    var weatherLat: String
    var weatherLon: String
    var dockStyle: String
    var columns: Int
    var dockOpacity: Int
    var dockTransparency: Int
    var glassIcons: Bool
    var libraryTile: Bool
    var topButtons: String
    var vpnApp: String
    var clockFormat: String

    enum CodingKeys: String, CodingKey {
        case scenes, weather, columns
        case sceneSeconds = "scene_seconds", sceneList = "scene_list", clockPosition = "clock_position"
        case weatherPlace = "weather_place", weatherLat = "weather_lat", weatherLon = "weather_lon"
        case dockStyle = "dock_style", dockOpacity = "dock_opacity", dockTransparency = "dock_transparency", glassIcons = "glass_icons"
        case libraryTile = "library_tile", topButtons = "top_buttons"
        case vpnApp = "vpn_app", clockFormat = "clock_format"
    }

    var sceneIds: [String] {
        get { sceneList.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
        set { sceneList = newValue.joined(separator: ",") }
    }

    var buttons: [String] {
        get { topButtons.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
        set { topButtons = newValue.joined(separator: ",") }
    }

    /// Only what differs from `old`, as JSON values: the launcher saves just those, so the rest keeps following
    /// config.json and the built-in defaults.
    func changes(from old: Choices) -> [String: Any] {
        guard let new = try? JSONSerialization.jsonObject(with: JSONEncoder().encode(self)) as? [String: Any],
              let was = try? JSONSerialization.jsonObject(with: JSONEncoder().encode(old)) as? [String: Any]
        else { return [:] }
        var out: [String: Any] = [:]
        for (k, v) in new where !(was[k].map { "\($0)" == "\(v)" } ?? false) { out[k] = v }
        return out
    }
}

/// Names for the launcher's scenes (Scenes.java ids), as the settings menu on the TV shows them.
enum SceneNames {
    static let ru: [String: String] = [
        "night": "Ночь", "golden": "Золотой час", "water": "Вода", "fog": "Туман", "dawn": "Рассвет в лесу",
        "pavement": "Мозаичная мостовая, Тасмания", "valley": "Долина Гроуз, Голубые горы",
        "storm": "Гроза на закате, Олбери", "beach": "Пляж Арескье, Франция", "paddy": "Ванвьенг, Лаос",
    ]
    static func name(_ id: String) -> String { ru[id] ?? id }
}

/// One line of profiles/sber-staros.tsv: what to do with a preinstalled package.
struct ProfileEntry: Identifiable, Hashable {
    enum Action: String { case disable, ask, keep }
    var package: String
    var action: Action
    var category: String
    var note: String
    var id: String { package }

    /// Reads the tab-separated profile; '#' lines are comments.
    static func parse(_ text: String) -> [ProfileEntry] {
        text.split(separator: "\n").compactMap { line in
            let s = line.trimmingCharacters(in: .whitespaces)
            guard !s.isEmpty, !s.hasPrefix("#") else { return nil }
            let f = s.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard f.count >= 2, let a = Action(rawValue: f[1]) else { return nil }
            return ProfileEntry(package: f[0], action: a, category: f.count > 2 ? f[2] : "",
                                note: f.count > 3 ? f[3] : "")
        }
    }

    /// profiles/never.txt: packages that are never switched off (first word of each line).
    static func parseNever(_ text: String) -> Set<String> {
        Set(text.split(separator: "\n").compactMap { line in
            let s = line.trimmingCharacters(in: .whitespaces)
            guard !s.isEmpty, !s.hasPrefix("#") else { return nil }
            return s.split(separator: " ").first.map(String.init)
        })
    }
}

/// Files that ship inside the app (mac/build.sh copies them to Resources), or the repo's when run from source.
enum Bundled {
    static func url(_ name: String) -> URL? {
        if let res = Bundle.main.resourceURL {
            let u = res.appendingPathComponent(name)
            if FileManager.default.fileExists(atPath: u.path) { return u }
        }
        // swift run: walk up from the executable to the repo
        var dir = URL(fileURLWithPath: CommandLine.arguments[0]).deletingLastPathComponent()
        for _ in 0..<8 {
            for sub in ["profiles/" + name, "launcher/app/build/outputs/apk/release/" + name,
                        "launcher/app/src/main/assets/scenes/" + name,
                        name == "Backdrop.jpg" ? "launcher/app/src/main/assets/scenes/scene-valley.jpg" : name] {
                let u = dir.appendingPathComponent(sub)
                if FileManager.default.fileExists(atPath: u.path) { return u }
            }
            dir.deleteLastPathComponent()
        }
        return nil
    }

    static func text(_ name: String) -> String {
        guard let u = url(name), let s = try? String(contentsOf: u, encoding: .utf8) else { return "" }
        return s
    }

    /// The launcher's APK the app carries (CleanMySaluteTV.apk), or the repo's release build.
    static var apk: URL? { url("CleanMySaluteTV.apk") ?? url("app-release.apk") }
}
