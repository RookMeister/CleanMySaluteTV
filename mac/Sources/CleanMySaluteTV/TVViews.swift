import SwiftUI
import UniformTypeIdentifiers

// MARK: - Телевизор

struct TVView: View {
    @EnvironmentObject var tv: TVStore

    var body: some View {
        GlassPage {
            GlassCard("Подключение",
                      footer: "На ТВ: Настройки → Настройки устройства → Об устройстве → 7 раз OK на «Сборка», затем назад → Для разработчиков → Отладка по USB. При первом подключении ТВ спросит «Разрешить отладку?» — отметьте «Всегда» и нажмите OK. После работы отладку лучше выключить.") {
                HStack(spacing: 10) {
                    TextField("IP-адрес телевизора", text: $tv.host, prompt: Text("192.168.1.20"))
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .glassEffect(.regular, in: .capsule)
                        .onSubmit { Task { await tv.connect() } }
                    Button(tv.connected ? "Переподключить" : "Подключить") { Task { await tv.connect() } }
                        .buttonStyle(.glassProminent)
                    Button("Найти в сети") { Task { await tv.scan() } }
                        .buttonStyle(.glass)
                }
                .disabled(tv.busy != nil)
                if !tv.found.isEmpty {
                    HStack {
                        Text("Найдены:").foregroundStyle(.secondary)
                        ForEach(tv.found, id: \.self) { ip in
                            Button(ip) { tv.host = ip; Task { await tv.connect() } }.buttonStyle(.glass)
                        }
                    }
                }
            }
            if tv.connected {
                GlassCard("Телевизор") {
                    GlassRow("Модель", value: tv.model)
                    GlassDivider()
                    GlassRow("Android", value: tv.android)
                    GlassDivider()
                    GlassRow("Память", value: tv.freeSpace)
                    GlassDivider()
                    GlassRow("Лаунчер", value: tv.launcherVersion.map { "CleanMySaluteTV \($0)" } ?? "не установлен")
                    GlassDivider()
                    GlassRow("Кнопка Home", value: tv.homeIsOurs ? "CleanMySaluteTV" : "другой лаунчер")
                    HStack {
                        Button("Обновить") { Task { await tv.refreshAll() } }.buttonStyle(.glass)
                        Button("Отключиться") { Task { await tv.disconnect() } }.buttonStyle(.glass)
                    }
                    .padding(.top, 4)
                }
                GlassCard("Экран") {
                    Button {
                        Task { await tv.takeScreenshot() }
                    } label: { Label("Снять экран", systemImage: "camera.viewfinder") }
                        .buttonStyle(.glass)
                    if let img = tv.screenshot {
                        Image(nsImage: img).resizable().aspectRatio(contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
        }
    }
}

// MARK: - Очистка

struct CleanupView: View {
    @EnvironmentObject var tv: TVStore
    @State private var confirmAll = false

    var body: some View {
        GlassPage {
            GlassCard(footer: "Отключённое приложение не работает и не занимает память; включить его обратно можно в любой момент. Ничего не удаляется, а нужное системе не отключается.") {
                HStack {
                    Button {
                        Task { await tv.disableRecommended() }
                    } label: { Label("Отключить рекомендуемые", systemImage: "wand.and.stars") }
                        .buttonStyle(.glassProminent)
                    Button("Включить всё обратно") { confirmAll = true }.buttonStyle(.glass)
                }
                .disabled(tv.busy != nil)
            }
            ForEach(groups, id: \.title) { group in
                GlassCard(group.title) {
                    ForEach(Array(group.items.enumerated()), id: \.element.id) { i, e in
                        if i > 0 { GlassDivider() }
                        row(e)
                    }
                }
            }
        }
        .alert("Включить обратно все приложения из списка?", isPresented: $confirmAll) {
            Button("Включить") { Task { await tv.enableAll() } }
            Button("Отмена", role: .cancel) {}
        }
    }

    private struct Group { var title: String; var items: [ProfileEntry] }

    /// On the TV, by the advice; then the ones the TV doesn't have.
    private var groups: [Group] {
        let on = tv.profile.filter { tv.installed.contains($0.package) }
        let off = tv.profile.filter { !tv.installed.contains($0.package) }
        return [Group(title: "Советую отключить", items: on.filter { $0.action == .disable }),
                Group(title: "По желанию", items: on.filter { $0.action == .ask }),
                Group(title: "Лучше оставить", items: on.filter { $0.action == .keep }),
                Group(title: "Нет на этом ТВ", items: off)].filter { !$0.items.isEmpty }
    }

    @ViewBuilder private func row(_ e: ProfileEntry) -> some View {
        let installed = tv.installed.contains(e.package)
        GlassRow(e.note.isEmpty ? e.package : e.note, detail: e.package) {
            if installed {
                Toggle(tv.disabled.contains(e.package) ? "Отключено" : "Работает", isOn: Binding(
                    get: { !tv.disabled.contains(e.package) },
                    set: { on in Task { await tv.setDisabled(e.package, !on) } }))
                .toggleStyle(.switch)
                .disabled(tv.busy != nil || (e.action == .keep && !tv.disabled.contains(e.package)))
            } else {
                Text("удалено").foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Лаунчер

struct LauncherView: View {
    @EnvironmentObject var tv: TVStore
    @State private var pickFile = false

    var body: some View {
        GlassPage {
            GlassCard("CleanMySaluteTV на ТВ", footer: "Настройки на ТВ при обновлении сохраняются.") {
                GlassRow("Установлен", value: tv.launcherVersion ?? "нет")
                GlassDivider()
                GlassRow("В приложении", value: Bundled.apk == nil ? "нет APK" : "есть APK")
                HStack {
                    Button {
                        if let apk = Bundled.apk { Task { await tv.installLauncher(apk) } } else { pickFile = true }
                    } label: {
                        Label(tv.launcherVersion == nil ? "Установить" : "Обновить", systemImage: "arrow.down.app")
                    }
                    .buttonStyle(.glassProminent)
                    Button("Выбрать APK…") { pickFile = true }.buttonStyle(.glass)
                }
                .padding(.top, 4)
                .disabled(tv.busy != nil)
            }
            GlassCard("Главный экран") {
                GlassRow("Кнопка Home открывает", value: tv.homeIsOurs ? "CleanMySaluteTV" : "другой лаунчер")
                Button("Сделать главным экраном") { Task { await tv.makeHome() } }
                    .buttonStyle(.glass)
                    .disabled(tv.busy != nil || tv.launcherVersion == nil)
            }
            GlassCard("Заставка") {
                Button("Часы CleanMySaluteTV как заставка ТВ") { Task { await tv.useClockScreensaver() } }
                    .buttonStyle(.glass)
                    .disabled(tv.busy != nil || tv.launcherVersion == nil)
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
