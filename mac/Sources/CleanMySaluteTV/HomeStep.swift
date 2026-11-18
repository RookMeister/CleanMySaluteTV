import SwiftUI
import UniformTypeIdentifiers

/// Step 4, CleanMyTV's Home screen step for our launcher: the TV's Home drawn live on top, the tabs under it. Every
/// edit goes to the TV by itself a moment later (TVStore.scheduleApply): there is no Apply button.
struct HomeStepView: View {
    @EnvironmentObject var tv: TVStore
    @State private var tab: HomeTab = .overview

    enum HomeTab: String, CaseIterable, Identifiable {
        case overview = "Обзор", layout = "Раскладка", apps = "Приложения", look = "Вид и обои", widgets = "Виджеты"
        var id: String { rawValue }
    }

    var body: some View {
        VStack(spacing: 14) {
            if tv.state != nil {
                TVPreview()
                    .frame(maxWidth: 640)
                    .padding(.top, 8)
                    .overlay(alignment: .bottomTrailing) { SaveBadge().padding(10) }
            }
            tabs
            GlassPage {
                if tv.state == nil {
                    OverviewTab()
                } else {
                    switch tab {
                    case .overview: OverviewTab()
                    case .layout: LayoutTab()
                    case .apps: AppsTab()
                    case .look: LookTab()
                    case .widgets: WidgetsTab()
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .onChange(of: tv.draft) { tv.scheduleApply() }
        .onChange(of: tv.home) { tv.scheduleApply(after: 0.3) }
        .onChange(of: tv.hidden) { tv.scheduleApply(after: 0.3) }
    }

    /// The tabs as one glass bar; all but Overview wait for the launcher's settings.
    private var tabs: some View {
        GlassEffectContainer(spacing: 6) {
            HStack(spacing: 6) {
                ForEach(HomeTab.allCases) { t in
                    let locked = t != .overview && tv.state == nil
                    Button(t.rawValue) { tab = t }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .foregroundStyle(locked ? .secondary : (tab == t ? Color.black : .primary))
                        .glassEffect(tab == t ? .regular.tint(.white).interactive() : .regular.interactive(), in: .capsule)
                        .disabled(locked)
                        .help(locked ? "Сначала установите или обновите лаунчер на вкладке «Обзор»" : "")
                }
            }
        }
    }
}

/// "Sending to the TV…" / "Saved on the TV", over the preview's corner.
struct SaveBadge: View {
    @EnvironmentObject var tv: TVStore
    var body: some View {
        HStack(spacing: 6) {
            if tv.saving { ProgressView().controlSize(.mini) } else { Image(systemName: "checkmark") }
            Text(tv.saving ? "Отправляю на ТВ…" : "Сохранено на ТВ")
        }
        .font(.caption)
        .padding(.horizontal, 10).padding(.vertical, 5)
        .glassEffect(.regular, in: .capsule)
    }
}

// MARK: - Обзор

struct OverviewTab: View {
    @EnvironmentObject var tv: TVStore
    @State private var pickFile = false

    var body: some View {
        GlassCard("На этом ТВ", footer: "Настройки на ТВ при обновлении сохраняются. Лаунчер Sber остаётся на ТВ — вернуть его можно одной кнопкой.") {
            GlassRow("Состояние", value: tv.homeIsOurs ? "главный экран" : tv.launcherVersion == nil ? "не установлен" : "установлен, но не главный")
            GlassDivider()
            GlassRow("На ТВ", value: tv.launcherVersion ?? "нет")
            GlassDivider()
            GlassRow("В приложении", value: tv.bundledVersion ?? (Bundled.apk == nil ? "нет APK" : "есть"))
            HStack {
                let needsInstall = tv.launcherVersion == nil || tv.launcherOutdated
                Button {
                    Task {
                        if needsInstall, let apk = Bundled.apk { await tv.installLauncher(apk) } else { await tv.makeHome() }
                        await tv.loadLauncher()
                    }
                } label: {
                    Label(tv.launcherVersion == nil ? "Установить и использовать" : tv.launcherOutdated ? "Обновить и использовать" : "Использовать CleanMySaluteTV",
                          systemImage: "house.fill")
                }
                .buttonStyle(.glassProminent)
                .disabled(tv.homeIsOurs && !tv.launcherOutdated)
                Button("Вернуть лаунчер Sber") { Task { await tv.useStockLauncher() } }
                    .buttonStyle(.glass).disabled(!tv.homeIsOurs)
                Button("Другой APK…") { pickFile = true }.buttonStyle(.glass)
            }
            .disabled(tv.busy != nil)
            .padding(.top, 4)
        }
        GlassCard("Что сейчас на ТВ", footer: "Снимок экрана ТВ прямо сейчас.") {
            Button {
                Task { await tv.takeScreenshot() }
            } label: { Label("Снять экран", systemImage: "camera.viewfinder") }.buttonStyle(.glass)
            if let img = tv.screenshot {
                Image(nsImage: img).resizable().aspectRatio(contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .fileImporter(isPresented: $pickFile, allowedContentTypes: [UTType(filenameExtension: "apk") ?? .data]) { r in
            if case .success(let url) = r {
                Task {
                    let ok = url.startAccessingSecurityScopedResource()
                    await tv.installLauncher(url)
                    if ok { url.stopAccessingSecurityScopedResource() }
                }
            }
        }
    }
}

// MARK: - Раскладка

struct LayoutTab: View {
    @EnvironmentObject var tv: TVStore

    var body: some View {
        if let d = Binding($tv.draft) {
            GlassCard("Док") {
                GlassRow("Плитки") {
                    Picker("", selection: Binding(get: { d.wrappedValue.dockStyle }, set: { style in
                        d.wrappedValue.dockStyle = style
                        // the large dock takes 6 at most, as the launcher does
                        d.wrappedValue.columns = min(d.wrappedValue.columns, tv.dockMax)
                    })) { Text("Маленькие").tag("small"); Text("Большие").tag("large") }
                        .labelsHidden().pickerStyle(.segmented).fixedSize()
                }
                GlassDivider()
                GlassRow("Приложений в доке", detail: "До \(tv.dockMax) · остальные — под доком и в «Все приложения»") {
                    Stepper(value: d.columns, in: (tv.state?.dockMin ?? 4)...tv.dockMax) {
                        Text("\(d.wrappedValue.columns)").monospacedDigit().frame(minWidth: 20)
                    }
                }
                GlassDivider()
                GlassRow("Прозрачность панели", detail: "70% — как в couch-tv, 100% — только рамка") {
                    Picker("", selection: d.dockTransparency) { ForEach([0, 25, 50, 70, 100], id: \.self) { Text("\($0)%").tag($0) } }
                        .labelsHidden().pickerStyle(.segmented).fixedSize()
                }
                GlassDivider()
                GlassRow("Плитка «Библиотека приложений»") {
                    Toggle("", isOn: d.libraryTile).labelsHidden().toggleStyle(.switch)
                }
            }
            TopButtonsCard(d: d)
            GlassCard("Часы") {
                GlassRow("Положение") {
                    Picker("", selection: d.clockPosition) {
                        Text("Авто").tag("auto"); Text("Слева").tag("left"); Text("По центру").tag("center"); Text("Справа").tag("right")
                    }.labelsHidden().pickerStyle(.segmented).fixedSize()
                }
                GlassDivider()
                GlassRow("Формат") {
                    Picker("", selection: d.clockFormat) {
                        Text("Как на ТВ").tag("system"); Text("12 ч").tag("12"); Text("24 ч").tag("24")
                    }.labelsHidden().pickerStyle(.segmented).fixedSize()
                }
            }
        }
    }
}

struct TopButtonsCard: View {
    @EnvironmentObject var tv: TVStore
    var d: Binding<Choices>

    var body: some View {
        let all = tv.state?.buttons ?? []
        let chosen = d.wrappedValue.buttons
        GlassCard("Верхние кнопки — слева направо", footer: "Перетащите кнопку на другую, чтобы поменять порядок.") {
            ForEach(Array(chosen.enumerated()), id: \.element) { i, kind in
                if i > 0 { GlassDivider() }
                row(kind, all) {
                    Button("Убрать") { d.wrappedValue.buttons.removeAll { $0 == kind } }.buttonStyle(.glass)
                }
                .draggable(kind)
                .dropDestination(for: String.self) { kinds, _ in
                    guard let k = kinds.first, let from = chosen.firstIndex(of: k), let to = chosen.firstIndex(of: kind), from != to else { return false }
                    var b = chosen
                    b.remove(at: from)
                    b.insert(k, at: to)
                    d.wrappedValue.buttons = b
                    return true
                }
            }
            let rest = all.map(\.kind).filter { !chosen.contains($0) }
            if !rest.isEmpty {
                Text("Можно добавить").font(.subheadline).foregroundStyle(.secondary).padding(.top, 6)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 8)], spacing: 8) {
                    ForEach(rest, id: \.self) { kind in
                        Button {
                            var b = d.wrappedValue.buttons
                            b.insert(kind, at: b.last == "settings" ? b.count - 1 : b.count)   // before the gear, as on the TV
                            d.wrappedValue.buttons = b
                        } label: {
                            Label(all.first { $0.kind == kind }?.label ?? kind, systemImage: ButtonsView.symbols[kind] ?? "plus")
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.glass)
                    }
                }
            }
            if chosen.contains("vpn") {
                GlassDivider()
                GlassRow("Кнопка VPN открывает") {
                    Picker("", selection: d.vpnApp) {
                        Text("Настройки VPN").tag("")
                        ForEach(tv.allApps) { a in Text(a.label).tag(a.package) }
                    }
                    .labelsHidden().fixedSize()
                }
            }
        }
    }

    private func row<B: View>(_ kind: String, _ all: [LauncherState.TopButton], @ViewBuilder action: () -> B) -> some View {
        HStack(spacing: 12) {
            Image(systemName: ButtonsView.symbols[kind] ?? "circle").frame(width: 34, height: 34).glassEffect(.regular, in: .circle)
            Text(all.first { $0.kind == kind }?.label ?? kind)
            Spacer()
            action()
        }
        .contentShape(Rectangle())
    }
}

/// The top bar's SF Symbols, close to the launcher's own glyphs.
enum ButtonsView {
    static let symbols: [String: String] = [
        "sleep": "timer", "picture": "tv", "sound": "speaker.wave.2", "screensaver": "clock",
        "screen_off": "power", "bluetooth": "dot.radiowaves.left.and.right", "memory": "memorychip",
        "inputs": "cable.connector", "network": "wifi", "vpn": "lock.shield", "apps": "square.grid.3x3",
        "tv_settings": "slider.horizontal.3", "settings": "gearshape",
    ]
}

// MARK: - Приложения

struct AppsTab: View {
    @EnvironmentObject var tv: TVStore

    var body: some View {
        GlassCard {
            Label("Перетащите приложение на другое в той же группе, чтобы поменять порядок. Сколько плиток в доке — на вкладке «Раскладка».",
                  systemImage: "hand.draw").font(.callout)
        }
        group("В доке · \(tv.docked.count) из \(tv.dockSize)", tv.docked, movable: true)
        group("Ещё — под доком и в «Все приложения»", tv.more, movable: true)
        group("Скрытые — только в «Все приложения»", tv.hidden, movable: false)
    }

    @ViewBuilder private func group(_ title: String, _ apps: [TVApp], movable: Bool) -> some View {
        GlassCard(title) {
            if apps.isEmpty { Text("Нет").foregroundStyle(.secondary) }
            ForEach(Array(apps.enumerated()), id: \.element.id) { i, app in
                if i > 0 { GlassDivider() }
                if movable {
                    AppRow(app: app)
                        .draggable(app.key)
                        .dropDestination(for: String.self) { keys, _ in
                            guard let key = keys.first else { return false }
                            return tv.moveApp(key, before: app.key)
                        }
                } else {
                    AppRow(app: app)
                }
            }
        }
    }
}

struct AppRow: View {
    @EnvironmentObject var tv: TVStore
    var app: TVApp

    var body: some View {
        HStack(spacing: 12) {
            AppIcon(package: app.package)
            Text(app.label)
            Spacer()
            Picker("", selection: Binding(get: { tv.place(of: app) }, set: { tv.move(app, to: $0) })) {
                Text("В доке").tag(TVStore.Place.dock)
                Text("Ещё").tag(TVStore.Place.more)
                Text("Скрыто").tag(TVStore.Place.hidden)
            }
            .labelsHidden().pickerStyle(.segmented).fixedSize()
            .disabled(tv.place(of: app) == .dock && tv.home.count <= 1)
        }
        .contentShape(Rectangle())
    }
}

struct AppIcon: View {
    @EnvironmentObject var tv: TVStore
    var package: String
    var size: CGFloat = 34

    var body: some View {
        Group {
            if let img = tv.icons[package] {
                Image(nsImage: img).resizable().interpolation(.high)
            } else {
                Image(systemName: "app.dashed").resizable().scaledToFit().padding(6).foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.24))
    }
}

// MARK: - Вид и обои

struct LookTab: View {
    @EnvironmentObject var tv: TVStore
    @State private var pickPhoto = false
    @State private var dropping = false

    var body: some View {
        if let d = Binding($tv.draft) {
            GlassCard("Фон главного экрана") {
                Picker("", selection: Binding(get: { d.wrappedValue.scenes }, set: { on in
                    if on { Task { await tv.backToScenes() } } else if tv.wallpaper != nil { d.wrappedValue.scenes = false } else { pickPhoto = true }
                })) {
                    Text("Сцены со стеклянными часами").tag(true)
                    Text("Своё фото").tag(false)
                }
                .labelsHidden().pickerStyle(.segmented).fixedSize()
                if d.wrappedValue.scenes {
                    GlassRow("Смена сцены") {
                        Picker("", selection: d.sceneSeconds) {
                            ForEach([10, 20, 30, 60, 120, 300], id: \.self) { s in Text(s < 60 ? "\(s) с" : "\(s / 60) мин").tag(s) }
                        }.labelsHidden().fixedSize()
                    }
                    if let all = tv.state?.scenes {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                            ForEach(all, id: \.self) { id in sceneTile(id, all, d) }
                        }
                    }
                } else {
                    photoDrop
                }
            }
            GlassCard("Значки") {
                GlassRow("Стеклянные значки", detail: "Символ приложения белым на стекле; в фокусе — в своих цветах") {
                    Toggle("", isOn: d.glassIcons).labelsHidden().toggleStyle(.switch)
                }
            }
            .fileImporter(isPresented: $pickPhoto, allowedContentTypes: [.image]) { r in
                if case .success(let u) = r { sendPhoto(u) }
            }
        }
    }

    /// One scene as a picture: on or off. An empty list on the TV means every scene; one stays on at least.
    private func sceneTile(_ id: String, _ all: [String], _ d: Binding<Choices>) -> some View {
        let on = d.wrappedValue.sceneIds.isEmpty || d.wrappedValue.sceneIds.contains(id)
        return Button {
            var ids = d.wrappedValue.sceneIds.isEmpty ? all : d.wrappedValue.sceneIds
            if on { ids.removeAll { $0 == id } } else { ids.append(id) }
            ids = all.filter(ids.contains)
            guard !ids.isEmpty else { return }
            d.wrappedValue.sceneIds = ids.count == all.count ? [] : ids
        } label: {
            ZStack(alignment: .bottomLeading) {
                Group {
                    if let img = SceneArt.image(id) { Image(nsImage: img).resizable().aspectRatio(16 / 9, contentMode: .fill) }
                    else { Rectangle().fill(.quaternary) }
                }
                .frame(height: 84).clipped()
                .opacity(on ? 1 : 0.35)
                HStack(spacing: 4) {
                    Image(systemName: on ? "checkmark.circle.fill" : "circle")
                    Text(SceneNames.name(id)).lineLimit(1)
                }
                .font(.caption).foregroundStyle(.white)
                .padding(6)
                .shadow(radius: 3)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(on ? .white : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }

    private var photoDrop: some View {
        VStack(spacing: 10) {
            if let img = tv.wallpaper {
                Image(nsImage: img).resizable().aspectRatio(16 / 9, contentMode: .fill).frame(height: 150).clipShape(RoundedRectangle(cornerRadius: 14))
            }
            Text("Перетащите фото сюда — оно станет фоном ТВ (1920×1080, обрежется по экрану)").font(.callout)
            Button("Выбрать фото…") { pickPhoto = true }.buttonStyle(.glass)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .glassEffect(dropping ? .regular.tint(.accentColor.opacity(0.4)) : .clear, in: .rect(cornerRadius: 18))
        .dropDestination(for: URL.self) { urls, _ in
            guard let u = urls.first else { return false }
            sendPhoto(u)
            return true
        } isTargeted: { dropping = $0 }
    }

    private func sendPhoto(_ url: URL) {
        let ok = url.startAccessingSecurityScopedResource()
        let img = NSImage(contentsOf: url)
        if ok { url.stopAccessingSecurityScopedResource() }
        guard let img else { tv.message = "Это не картинка"; return }
        Task { await tv.setWallpaper(img) }
    }
}

// MARK: - Виджеты

struct WidgetsTab: View {
    @EnvironmentObject var tv: TVStore
    @State private var city = ""
    @State private var places: [Place] = []
    @State private var searching = false

    struct Place: Hashable { var name, label, lat, lon: String }

    var body: some View {
        if let d = Binding($tv.draft) {
            GlassCard("Погода", footer: "Над часами: температура и небо. Данные — Open-Meteo, при недоступности — MET Norway.") {
                GlassRow("Показывать погоду") { Toggle("", isOn: d.weather).labelsHidden().toggleStyle(.switch) }
                GlassDivider()
                GlassRow("Город", value: d.wrappedValue.weatherPlace.isEmpty ? "не выбран" : d.wrappedValue.weatherPlace)
                HStack(spacing: 10) {
                    TextField("Найти город", text: $city)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .glassEffect(.regular, in: .capsule)
                        .onSubmit { Task { await search() } }
                    Button("Найти") { Task { await search() } }.buttonStyle(.glass).disabled(city.isEmpty || searching)
                    if searching { ProgressView().controlSize(.small) }
                }
                ForEach(places, id: \.self) { p in
                    Button {
                        d.wrappedValue.weatherPlace = p.name
                        d.wrappedValue.weatherLat = p.lat
                        d.wrappedValue.weatherLon = p.lon
                        d.wrappedValue.weather = true
                        places = []
                        city = ""
                    } label: { Label(p.label, systemImage: "mappin.and.ellipse") }
                    .buttonStyle(.glass)
                }
            }
        }
    }

    /// The same geocoding the TV's menu uses (Weather.geocodeUrl): Open-Meteo, names in Russian.
    private func search() async {
        let q = city.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty, var c = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search") else { return }
        c.queryItems = [.init(name: "count", value: "8"), .init(name: "format", value: "json"),
                        .init(name: "language", value: "ru"), .init(name: "name", value: q)]
        searching = true
        defer { searching = false }
        struct Reply: Decodable {
            struct R: Decodable { var name: String; var latitude: Double; var longitude: Double; var admin1: String?; var country: String? }
            var results: [R]?
        }
        do {
            let (data, _) = try await URLSession.shared.data(from: c.url!)
            let r = try JSONDecoder().decode(Reply.self, from: data)
            places = (r.results ?? []).map { p in
                let label = [p.name, p.admin1, p.country].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                return Place(name: p.name, label: label, lat: String(format: "%.4f", p.latitude), lon: String(format: "%.4f", p.longitude))
            }
            if places.isEmpty { tv.message = "Город не найден" }
        } catch {
            tv.message = "Поиск города не удался: \(error.localizedDescription)"
        }
    }
}
