import AppKit
import CoreText
import SwiftUI

/// The TV's Home as the launcher draws it, from the edits in the window (CleanMyTV's HomePreview, for our layout): the
/// scene or the photo, the date with the weather, the big clock, the round top buttons and the dock. Designed at
/// 1920×1080 like the launcher (Scenes.java, AppAdapter.java) and scaled to fit.
struct TVPreview: View {
    @EnvironmentObject var tv: TVStore
    var focusedIndex = 0

    private static let W: CGFloat = 1920, H: CGFloat = 1080

    var body: some View {
        if let d = tv.draft {
            GeometryReader { geo in
                let k = geo.size.width / Self.W
                ZStack(alignment: .topLeading) {
                    background(d)
                    caption(d, k)
                    clock(d, k)
                    topButtons(d, k)
                    dock(d, k)
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(0.18), lineWidth: 1))
        }
    }

    // MARK: parts

    @ViewBuilder private func background(_ d: Choices) -> some View {
        let scene = (d.sceneIds.first ?? tv.state?.scenes.first) ?? "night"
        Group {
            if !d.scenes, let photo = tv.wallpaper {
                Image(nsImage: photo).resizable().aspectRatio(contentMode: .fill)
            } else if d.scenes, let img = SceneArt.image(scene) {
                Image(nsImage: img).resizable().aspectRatio(contentMode: .fill)
            } else {
                LinearGradient(colors: [Color(white: 0.16), Color(white: 0.06)], startPoint: .top, endPoint: .bottom)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .overlay(LinearGradient(colors: [.clear, .black.opacity(0.45)], startPoint: UnitPoint(x: 0.5, y: 0.6), endPoint: .bottom))
    }

    private func clockX(_ d: Choices, _ k: CGFloat) -> (CGFloat, Alignment) {
        switch d.clockPosition {
        case "left": return (96 * k, .leading)
        case "right": return (Self.W * k - 96 * k, .trailing)
        default: return (Self.W * k / 2, .center)
        }
    }

    private func caption(_ d: Choices, _ k: CGFloat) -> some View {
        let date = Date().formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "ru_RU")))
            .replacingOccurrences(of: ",", with: "").capitalizedFirst
        let weather = d.weather && !d.weatherPlace.isEmpty ? "   \(d.weatherPlace)" : ""
        let (x, a) = clockX(d, k)
        return Text(date + weather)
            .font(.system(size: 34 * k, weight: .medium))
            .foregroundStyle(.white.opacity(0.92))
            .fixedSize()
            .frame(width: 1400 * k, alignment: a)
            .position(x: a == .center ? x : a == .leading ? x + 700 * k : x - 700 * k, y: (220 - 54 + 20) * k)
    }

    private func clock(_ d: Choices, _ k: CGFloat) -> some View {
        let now = Date()
        let h24 = d.clockFormat == "24" || (d.clockFormat == "system")
        let f = DateFormatter()
        f.dateFormat = h24 ? "HH:mm" : "h:mm"
        // as large as the dock lets it be (Scenes.fitSize), from its fixed top
        let dockTop = dockTopY(d)
        let size = min(430, max(110, (dockTop - 40 - 220) / 0.92))
        let (x, a) = clockX(d, k)
        return Text(f.string(from: now))
            .font(.custom(SceneArt.clockFont, size: size * k))
            .foregroundStyle(.white.opacity(0.78))
            .shadow(color: .black.opacity(0.18), radius: 12 * k)
            .fixedSize()
            .frame(width: 1600 * k, alignment: a)
            .position(x: a == .center ? x : a == .leading ? x + 800 * k : x - 800 * k, y: (220 + size * 0.46) * k)
    }

    private func topButtons(_ d: Choices, _ k: CGFloat) -> some View {
        HStack(spacing: 12 * k) {
            ForEach(Array(d.buttons.enumerated()), id: \.element) { i, kind in
                Image(systemName: ButtonsView.symbols[kind] ?? "circle")
                    .font(.system(size: 34 * k, weight: .medium))
                    .foregroundStyle(i == focusedIndex - 100 ? Color.black : Color.white)
                    .frame(width: 88 * k, height: 88 * k)
                    .background(Circle().fill(Color(red: 0.12, green: 0.08, blue: 0.11).opacity(0.42)))
                    .overlay(Circle().strokeBorder(.white.opacity(0.18), lineWidth: max(0.5, 2 * k)))
            }
        }
        .padding(.top, 60 * k)
        .padding(.trailing, 80 * k)
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    /// The dock's top edge at 1080 lines: the small one sits 46 px off the bottom, the large one higher.
    private func dockTopY(_ d: Choices) -> CGFloat {
        d.dockStyle == "large" ? Self.H - 46 - 300 : Self.H - 46 - 172
    }

    private func dock(_ d: Choices, _ k: CGFloat) -> some View {
        let apps = Array(tv.home.prefix(d.columns))
        let large = d.dockStyle == "large"
        let tileW: CGFloat = large ? min(360, (1696 - 40 - CGFloat(apps.count - 1) * 28) / CGFloat(max(1, apps.count))) : 112
        let tileH: CGFloat = large ? tileW * 101 / 180 : 112
        let gap: CGFloat = large ? 28 : 30
        return VStack {
            Spacer()
            HStack(spacing: gap * k) {
                ForEach(Array(apps.enumerated()), id: \.element.id) { i, app in
                    tile(app, i == focusedIndex, glass: d.glassIcons, w: tileW * k, h: tileH * k, large: large, k: k)
                }
            }
            .padding(.horizontal, 28 * k)
            .padding(.vertical, 22 * k)
            .background(
                // couch-tv's glass, as DockDecoration.glassAlpha draws it on the TV
                RoundedRectangle(cornerRadius: 46 * k)
                    .fill(Color(red: 30 / 255, green: 20 / 255, blue: 28 / 255).opacity(min(1, Double(100 - d.dockTransparency) / 100 * 1.07)))
                    .overlay(RoundedRectangle(cornerRadius: 46 * k).strokeBorder(Color(red: 1, green: 210 / 255, blue: 220 / 255).opacity(0.16), lineWidth: max(0.5, 2 * k)))
            )
            .padding(.bottom, 46 * k)
        }
        .frame(maxWidth: .infinity)
    }

    private func tile(_ app: TVApp, _ focused: Bool, glass: Bool, w: CGFloat, h: CGFloat, large: Bool, k: CGFloat) -> some View {
        let corner = min(w, h) * 0.225
        return ZStack {
            RoundedRectangle(cornerRadius: corner)
                .fill(glass && !focused ? AnyShapeStyle(.white.opacity(0.14)) : AnyShapeStyle(SceneArt.tint(tv.icons[app.package])))
            if let icon = tv.icons[app.package] {
                Image(nsImage: icon).resizable().interpolation(.high)
                    .frame(width: min(w, h) * (large ? 0.62 : 0.7), height: min(w, h) * (large ? 0.62 : 0.7))
                    .saturation(glass && !focused ? 0 : 1)
                    .opacity(glass && !focused ? 0.85 : 1)
            }
        }
        .frame(width: w, height: h)
        .overlay(RoundedRectangle(cornerRadius: corner).strokeBorder(.white, lineWidth: focused ? max(1, 4 * k) : 0))
        .scaleEffect(focused ? 1.12 : 1)
        .overlay(alignment: .bottom) {
            if focused {
                Text(app.label).font(.system(size: 26 * k, weight: .medium)).foregroundStyle(.white)
                    .fixedSize().offset(y: h * 0.5 + 28 * k)
            }
        }
    }
}

/// The launcher's scene pictures and clock font, shipped inside this app (mac/build.sh), or the repo's.
enum SceneArt {
    private static var cache: [String: NSImage] = [:]

    static func image(_ id: String) -> NSImage? {
        if let c = cache[id] { return c }
        guard let u = Bundled.url("scene-\(id).jpg"), let img = NSImage(contentsOf: u) else { return nil }
        cache[id] = img
        return img
    }

    /// Antonio Light, as the scene clock on the TV; the system font when it is missing.
    static let clockFont: String = {
        if let u = Bundled.url("antonio_light.ttf") {
            CTFontManagerRegisterFontsForURL(u as CFURL, .process, nil)
            return "Antonio-Light"
        }
        return "Helvetica Neue"
    }()

    /// The icon's own colour for a coloured tile: its average, a little darker.
    static func tint(_ icon: NSImage?) -> Color {
        guard let icon, let cg = icon.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return Color(white: 0.2) }
        var px = [UInt8](repeating: 0, count: 4)
        guard let ctx = CGContext(data: &px, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return Color(white: 0.2)
        }
        ctx.interpolationQuality = .medium
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        let a = max(1, Double(px[3])) / 255
        return Color(red: Double(px[0]) / 255 / a * 0.85, green: Double(px[1]) / 255 / a * 0.85, blue: Double(px[2]) / 255 / a * 0.85)
    }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
