import SwiftUI

// MARK: - Вид

struct LookView: View {
    @EnvironmentObject var tv: TVStore
    @State private var city = ""
    @State private var places: [Place] = []
    @State private var searching = false

    struct Place: Hashable { var name, label, lat, lon: String }

    var body: some View {
        if let binding = Binding($tv.draft) {
            form(binding)
        }
    }

    private func form(_ d: Binding<Choices>) -> some View {
        Form {
            Section("Сцены") {
                Toggle("Сцены на главном экране", isOn: d.scenes)
                Picker("Смена сцены", selection: d.sceneSeconds) {
                    ForEach([10, 20, 30, 60, 120, 300], id: \.self) { s in
                        Text(s < 60 ? "\(s) с" : "\(s / 60) мин").tag(s)
                    }
                }
                Picker("Положение часов", selection: d.clockPosition) {
                    Text("Авто").tag("auto"); Text("Слева").tag("left")
                    Text("По центру").tag("center"); Text("Справа").tag("right")
                }
                Picker("Формат часов", selection: d.clockFormat) {
                    Text("Как на ТВ").tag("system"); Text("12 часов").tag("12"); Text("24 часа").tag("24")
                }
                if let all = tv.state?.scenes {
                    // An empty list on the TV means every scene.
                    ForEach(all, id: \.self) { id in
                        Toggle(SceneNames.name(id), isOn: Binding(
                            get: { d.wrappedValue.sceneIds.isEmpty || d.wrappedValue.sceneIds.contains(id) },
                            set: { on in
                                var ids = d.wrappedValue.sceneIds.isEmpty ? all : d.wrappedValue.sceneIds
                                if on { if !ids.contains(id) { ids.append(id) } } else { ids.removeAll { $0 == id } }
                                ids = all.filter(ids.contains)
                                if ids.isEmpty { return }   // at least one scene stays
                                d.wrappedValue.sceneIds = ids.count == all.count ? [] : ids
                            }))
                    }
                }
            }
            Section("Док") {
                Picker("Размер плиток", selection: d.dockStyle) {
                    Text("Маленькие").tag("small"); Text("Большие").tag("large")
                }
                .pickerStyle(.segmented)
                Stepper(value: d.columns, in: (tv.state?.dockMin ?? 4)...(tv.state?.dockMax ?? 9)) {
                    LabeledContent("Приложений в доке", value: "\(d.wrappedValue.columns)")
                }
                Picker("Непрозрачность панели дока", selection: d.dockOpacity) {
                    ForEach([100, 70, 50, 30], id: \.self) { Text("\($0)%").tag($0) }
                }
                Toggle("Стеклянные значки", isOn: d.glassIcons)
                Toggle("Плитка «Библиотека приложений»", isOn: d.libraryTile)
            }
            Section("Погода") {
                Toggle("Погода над часами", isOn: d.weather)
                LabeledContent("Город", value: d.wrappedValue.weatherPlace.isEmpty ? "не выбран" : d.wrappedValue.weatherPlace)
                HStack {
                    TextField("Найти город", text: $city).textFieldStyle(.roundedBorder)
                        .onSubmit { Task { await search() } }
                    Button("Найти") { Task { await search() } }.disabled(city.isEmpty || searching)
                    if searching { ProgressView().controlSize(.small) }
                }
                ForEach(places, id: \.self) { p in
                    Button(p.label) {
                        d.wrappedValue.weatherPlace = p.name
                        d.wrappedValue.weatherLat = p.lat
                        d.wrappedValue.weatherLon = p.lon
                        d.wrappedValue.weather = true
                        places = []
                        city = ""
                    }
                    .buttonStyle(.link)
                }
            }
            Section("AirPlay") {
                Toggle("Следить за приёмником AirPlay", isOn: d.airplayGuard)
            }
        }
        .formStyle(.grouped)
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
                return Place(name: p.name, label: label, lat: String(format: "%.4f", p.latitude),
                             lon: String(format: "%.4f", p.longitude))
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
        List {
            Section("В доке (\(tv.docked.count) из \(tv.dockSize))") {
                ForEach(tv.docked) { AppRow(app: $0) }
                    .onMove { tv.moveDocked(from: $0, to: $1) }
            }
            Section("Ещё приложения — под доком и в «Все приложения»") {
                if tv.more.isEmpty { Text("Нет").foregroundStyle(.secondary) }
                ForEach(tv.more) { AppRow(app: $0) }
                    .onMove { tv.moveMore(from: $0, to: $1) }
            }
            Section("Скрытые — только в «Все приложения»") {
                ForEach(tv.hidden) { AppRow(app: $0) }
            }
        }
        .safeAreaInset(edge: .top) {
            Text("Перетаскивайте приложения внутри группы, чтобы поменять порядок. Число плиток в доке — в разделе «Вид».")
                .font(.callout).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal).padding(.top, 8)
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
            .pickerStyle(.segmented)
            .frame(width: 230)
            .disabled(tv.place(of: app) == .dock && tv.home.count <= 1)
        }
        .padding(.vertical, 2)
    }
}

struct AppIcon: View {
    @EnvironmentObject var tv: TVStore
    var package: String
    var size: CGFloat = 32

    var body: some View {
        Group {
            if let img = tv.icons[package] {
                Image(nsImage: img).resizable().interpolation(.high)
            } else {
                RoundedRectangle(cornerRadius: 6).fill(.quaternary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
    }
}

// MARK: - Кнопки

struct ButtonsView: View {
    @EnvironmentObject var tv: TVStore

    private static let symbols: [String: String] = [
        "sleep": "timer", "picture": "tv", "sound": "speaker.wave.2", "screensaver": "clock",
        "screen_off": "power", "bluetooth": "dot.radiowaves.left.and.right", "memory": "memorychip",
        "inputs": "cable.connector", "network": "wifi", "vpn": "lock.shield", "apps": "square.grid.3x3",
        "tv_settings": "slider.horizontal.3", "settings": "gearshape",
    ]

    var body: some View {
        if let binding = Binding($tv.draft), let all = tv.state?.buttons {
            let chosen = binding.wrappedValue.buttons
            List {
                Section("На панели — слева направо (перетаскивайте)") {
                    ForEach(chosen, id: \.self) { kind in
                        row(kind, all) {
                            Button("Убрать") { binding.wrappedValue.buttons.removeAll { $0 == kind } }
                        }
                    }
                    .onMove { from, to in binding.wrappedValue.buttons.move(fromOffsets: from, toOffset: to) }
                }
                Section("Можно добавить") {
                    ForEach(all.map(\.kind).filter { !chosen.contains($0) }, id: \.self) { kind in
                        row(kind, all) {
                            Button("Добавить") {
                                var b = binding.wrappedValue.buttons
                                // before the gear when the gear ends the bar, as on the TV
                                let at = b.last == "settings" ? b.count - 1 : b.count
                                b.insert(kind, at: max(0, at))
                                binding.wrappedValue.buttons = b
                            }
                        }
                    }
                }
                Section("Кнопка VPN открывает") {
                    Picker("Приложение", selection: binding.vpnApp) {
                        Text("Не выбрано (настройки VPN)").tag("")
                        ForEach(tv.allApps) { a in
                            HStack { AppIcon(package: a.package, size: 16); Text(a.label) }.tag(a.package)
                        }
                    }
                }
            }
        }
    }

    private func row<B: View>(_ kind: String, _ all: [LauncherState.TopButton], @ViewBuilder action: () -> B) -> some View {
        HStack(spacing: 12) {
            Image(systemName: Self.symbols[kind] ?? "circle")
                .frame(width: 30, height: 30)
                .background(Circle().fill(.quaternary))
            Text(all.first { $0.kind == kind }?.label ?? kind)
            Spacer()
            action()
        }
    }
}
