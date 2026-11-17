import SwiftUI

// MARK: - Вид

struct LookView: View {
    @EnvironmentObject var tv: TVStore
    @State private var city = ""
    @State private var places: [Place] = []
    @State private var searching = false

    struct Place: Hashable { var name, label, lat, lon: String }

    var body: some View {
        if let d = Binding($tv.draft) {
            GlassPage {
                scenes(d)
                dock(d)
                weather(d)
                GlassCard("AirPlay") {
                    GlassRow("Следить за приёмником AirPlay") { Toggle("", isOn: d.airplayGuard).labelsHidden().toggleStyle(.switch) }
                }
            }
        }
    }

    private func scenes(_ d: Binding<Choices>) -> some View {
        GlassCard("Сцены") {
            GlassRow("Сцены на главном экране") { Toggle("", isOn: d.scenes).labelsHidden().toggleStyle(.switch) }
            GlassDivider()
            GlassRow("Смена сцены") {
                Picker("", selection: d.sceneSeconds) {
                    ForEach([10, 20, 30, 60, 120, 300], id: \.self) { s in Text(s < 60 ? "\(s) с" : "\(s / 60) мин").tag(s) }
                }.labelsHidden().fixedSize()
            }
            GlassDivider()
            GlassRow("Положение часов") {
                Picker("", selection: d.clockPosition) {
                    Text("Авто").tag("auto"); Text("Слева").tag("left"); Text("По центру").tag("center"); Text("Справа").tag("right")
                }.labelsHidden().pickerStyle(.segmented).fixedSize()
            }
            GlassDivider()
            GlassRow("Формат часов") {
                Picker("", selection: d.clockFormat) {
                    Text("Как на ТВ").tag("system"); Text("12 ч").tag("12"); Text("24 ч").tag("24")
                }.labelsHidden().pickerStyle(.segmented).fixedSize()
            }
            if let all = tv.state?.scenes {
                Text("Какие показывать").font(.subheadline).foregroundStyle(.secondary).padding(.top, 6)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 220), spacing: 10)], spacing: 10) {
                    ForEach(all, id: \.self) { id in sceneChip(id, all, d) }
                }
            }
        }
    }

    /// One scene, on or off. An empty list on the TV means every scene; at least one stays on.
    private func sceneChip(_ id: String, _ all: [String], _ d: Binding<Choices>) -> some View {
        let on = d.wrappedValue.sceneIds.isEmpty || d.wrappedValue.sceneIds.contains(id)
        return Button {
            var ids = d.wrappedValue.sceneIds.isEmpty ? all : d.wrappedValue.sceneIds
            if on { ids.removeAll { $0 == id } } else { ids.append(id) }
            ids = all.filter(ids.contains)
            guard !ids.isEmpty else { return }
            d.wrappedValue.sceneIds = ids.count == all.count ? [] : ids
        } label: {
            HStack {
                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                Text(SceneNames.name(id)).lineLimit(1)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.glass)
        .tint(on ? .green : nil)
    }

    private func dock(_ d: Binding<Choices>) -> some View {
        GlassCard("Док") {
            GlassRow("Плитки") {
                Picker("", selection: d.dockStyle) { Text("Маленькие").tag("small"); Text("Большие").tag("large") }
                    .labelsHidden().pickerStyle(.segmented).fixedSize()
            }
            GlassDivider()
            GlassRow("Приложений в доке") {
                Stepper(value: d.columns, in: (tv.state?.dockMin ?? 4)...(tv.state?.dockMax ?? 9)) {
                    Text("\(d.wrappedValue.columns)").monospacedDigit().frame(minWidth: 20)
                }
            }
            GlassDivider()
            GlassRow("Непрозрачность панели") {
                Picker("", selection: d.dockOpacity) { ForEach([100, 70, 50, 30], id: \.self) { Text("\($0)%").tag($0) } }
                    .labelsHidden().pickerStyle(.segmented).fixedSize()
            }
            GlassDivider()
            GlassRow("Стеклянные значки", detail: "Символ приложения белым на стекле") {
                Toggle("", isOn: d.glassIcons).labelsHidden().toggleStyle(.switch)
            }
            GlassDivider()
            GlassRow("Плитка «Библиотека приложений»") {
                Toggle("", isOn: d.libraryTile).labelsHidden().toggleStyle(.switch)
            }
        }
    }

    private func weather(_ d: Binding<Choices>) -> some View {
        GlassCard("Погода") {
            GlassRow("Погода над часами") { Toggle("", isOn: d.weather).labelsHidden().toggleStyle(.switch) }
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

// MARK: - Приложения

struct AppsView: View {
    @EnvironmentObject var tv: TVStore

    var body: some View {
        GlassPage {
            GlassCard {
                Label("Перетащите приложение на другое внутри группы, чтобы поменять порядок. Сколько плиток в доке — в разделе «Вид».",
                      systemImage: "hand.draw")
                    .font(.callout)
            }
            group("В доке · \(tv.docked.count) из \(tv.dockSize)", tv.docked, movable: true)
            group("Ещё приложения — под доком и в «Все приложения»", tv.more, movable: true)
            group("Скрытые — только в «Все приложения»", tv.hidden, movable: false)
        }
    }

    @ViewBuilder private func group(_ title: String, _ apps: [TVApp], movable: Bool) -> some View {
        GlassCard(title) {
            if apps.isEmpty { Text("Нет").foregroundStyle(.secondary) }
            ForEach(Array(apps.enumerated()), id: \.element.id) { i, app in
                if i > 0 { GlassDivider() }
                let row = AppRow(app: app)
                if movable {
                    row.draggable(app.key)
                        .dropDestination(for: String.self) { keys, _ in
                            guard let key = keys.first else { return false }
                            return tv.moveApp(key, before: app.key)
                        }
                } else {
                    row
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
            .labelsHidden()
            .pickerStyle(.segmented)
            .fixedSize()
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

// MARK: - Кнопки

struct ButtonsView: View {
    @EnvironmentObject var tv: TVStore

    static let symbols: [String: String] = [
        "sleep": "timer", "picture": "tv", "sound": "speaker.wave.2", "screensaver": "clock",
        "screen_off": "power", "bluetooth": "dot.radiowaves.left.and.right", "memory": "memorychip",
        "inputs": "cable.connector", "network": "wifi", "vpn": "lock.shield", "apps": "square.grid.3x3",
        "tv_settings": "slider.horizontal.3", "settings": "gearshape",
    ]

    var body: some View {
        if let d = Binding($tv.draft), let all = tv.state?.buttons {
            let chosen = d.wrappedValue.buttons
            GlassPage {
                GlassCard("Как на ТВ") { preview(chosen) }
                GlassCard("На панели — слева направо", footer: "Перетащите кнопку на другую, чтобы поменять порядок.") {
                    ForEach(Array(chosen.enumerated()), id: \.element) { i, kind in
                        if i > 0 { GlassDivider() }
                        row(kind, all) {
                            Button("Убрать") { d.wrappedValue.buttons.removeAll { $0 == kind } }.buttonStyle(.glass)
                        }
                        .draggable(kind)
                        .dropDestination(for: String.self) { kinds, _ in
                            guard let k = kinds.first, let from = chosen.firstIndex(of: k), let to = chosen.firstIndex(of: kind),
                                  from != to else { return false }
                            var b = chosen
                            b.remove(at: from)
                            b.insert(k, at: to)
                            d.wrappedValue.buttons = b
                            return true
                        }
                    }
                }
                GlassCard("Можно добавить") {
                    let rest = all.map(\.kind).filter { !chosen.contains($0) }
                    if rest.isEmpty { Text("Все кнопки уже на панели").foregroundStyle(.secondary) }
                    ForEach(Array(rest.enumerated()), id: \.element) { i, kind in
                        if i > 0 { GlassDivider() }
                        row(kind, all) {
                            Button("Добавить") {
                                var b = d.wrappedValue.buttons
                                // before the gear when the gear ends the bar, as on the TV
                                b.insert(kind, at: b.last == "settings" ? b.count - 1 : b.count)
                                d.wrappedValue.buttons = b
                            }
                            .buttonStyle(.glassProminent)
                        }
                    }
                }
                GlassCard("Кнопка VPN открывает") {
                    Picker("", selection: d.vpnApp) {
                        Text("Не выбрано (настройки VPN)").tag("")
                        ForEach(tv.allApps) { a in Text(a.label).tag(a.package) }
                    }
                    .labelsHidden()
                }
            }
        }
    }

    /// The bar as the TV draws it: round glass buttons, the first one focused (white).
    private func preview(_ chosen: [String]) -> some View {
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 8) {
                Spacer()
                ForEach(Array(chosen.enumerated()), id: \.element) { i, kind in
                    Image(systemName: Self.symbols[kind] ?? "circle")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(i == 0 ? Color.black : Color.white)
                        .frame(width: 44, height: 44)
                        .glassEffect(i == 0 ? .regular.tint(.white) : .regular, in: .circle)
                }
            }
        }
    }

    private func row<B: View>(_ kind: String, _ all: [LauncherState.TopButton], @ViewBuilder action: () -> B) -> some View {
        HStack(spacing: 12) {
            Image(systemName: Self.symbols[kind] ?? "circle")
                .frame(width: 34, height: 34)
                .glassEffect(.regular, in: .circle)
            Text(all.first { $0.kind == kind }?.label ?? kind)
            Spacer()
            action()
        }
        .contentShape(Rectangle())
    }
}
