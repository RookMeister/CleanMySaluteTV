import SwiftUI
import UniformTypeIdentifiers

// MARK: - Телевизор

struct TVView: View {
    @EnvironmentObject var tv: TVStore

    var body: some View {
        Form {
            Section("Подключение") {
                HStack {
                    TextField("IP-адрес телевизора", text: $tv.host, prompt: Text("192.168.1.20"))
                        .textFieldStyle(.roundedBorder)
                        .onSubmit { Task { await tv.connect() } }
                    Button(tv.connected ? "Переподключить" : "Подключить") { Task { await tv.connect() } }
                        .buttonStyle(.borderedProminent)
                    Button("Найти в сети") { Task { await tv.scan() } }
                }
                .disabled(tv.busy != nil)
                if !tv.found.isEmpty {
                    HStack {
                        Text("Найдены:").foregroundStyle(.secondary)
                        ForEach(tv.found, id: \.self) { ip in
                            Button(ip) { tv.host = ip; Task { await tv.connect() } }
                        }
                    }
                }
                Text("На ТВ: Настройки → Настройки устройства → Об устройстве → 7 раз OK на «Сборка», затем назад → Для разработчиков → Отладка по USB. При первом подключении ТВ спросит «Разрешить отладку?» — отметьте «Всегда» и нажмите OK. После работы отладку лучше выключить.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if tv.connected {
                Section("Телевизор") {
                    LabeledContent("Модель", value: tv.model)
                    LabeledContent("Android", value: tv.android)
                    LabeledContent("Память", value: tv.freeSpace)
                    LabeledContent("Лаунчер", value: tv.launcherVersion.map { "CleanMySaluteTV \($0)" } ?? "не установлен")
                    LabeledContent("Кнопка Home", value: tv.homeIsOurs ? "CleanMySaluteTV" : "другой лаунчер")
                    HStack {
                        Button("Обновить") { Task { await tv.refreshAll() } }
                        Button("Отключиться") { Task { await tv.disconnect() } }
                    }
                }
                Section("Экран") {
                    Button("Снять экран") { Task { await tv.takeScreenshot() } }
                    if let img = tv.screenshot {
                        Image(nsImage: img).resizable().aspectRatio(contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .frame(maxHeight: 340)
                    }
                }
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: - Очистка

struct CleanupView: View {
    @EnvironmentObject var tv: TVStore
    @State private var confirmAll = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Предустановленные приложения Sber. Отключённое не работает и не занимает память, а включить его обратно можно в любой момент. Ничего не удаляется.")
                    .font(.callout).foregroundStyle(.secondary)
                Spacer()
                Button("Отключить рекомендуемые") { Task { await tv.disableRecommended() } }
                Button("Включить всё обратно") { confirmAll = true }
            }
            .padding()
            .disabled(tv.busy != nil)
            Table(rows) {
                TableColumn("Приложение") { e in
                    VStack(alignment: .leading) {
                        Text(e.note.isEmpty ? e.package : e.note)
                        Text(e.package).font(.caption).foregroundStyle(.secondary)
                    }
                }
                TableColumn("Совет") { e in
                    Text(advice(e)).foregroundStyle(e.action == .disable ? .orange : .secondary)
                }
                .width(110)
                TableColumn("Состояние") { e in
                    if !tv.installed.contains(e.package) {
                        Text("нет на ТВ").foregroundStyle(.secondary)
                    } else {
                        Toggle(tv.disabled.contains(e.package) ? "Отключено" : "Работает", isOn: Binding(
                            get: { !tv.disabled.contains(e.package) },
                            set: { on in Task { await tv.setDisabled(e.package, !on) } }))
                        .toggleStyle(.switch)
                        .disabled(tv.busy != nil || (e.action == .keep && !tv.disabled.contains(e.package)))
                    }
                }
                .width(150)
            }
        }
        .alert("Включить обратно все приложения из списка?", isPresented: $confirmAll) {
            Button("Включить") { Task { await tv.enableAll() } }
            Button("Отмена", role: .cancel) {}
        }
    }

    /// On the TV first, then the ones that are not.
    private var rows: [ProfileEntry] {
        tv.profile.sorted { a, b in
            let ia = tv.installed.contains(a.package), ib = tv.installed.contains(b.package)
            return ia != ib ? ia : false
        }
    }

    private func advice(_ e: ProfileEntry) -> String {
        switch e.action {
        case .disable: "отключить"
        case .ask: "по желанию"
        case .keep: "оставить"
        }
    }
}

// MARK: - Лаунчер

struct LauncherView: View {
    @EnvironmentObject var tv: TVStore
    @State private var pickFile = false

    var body: some View {
        Form {
            Section("CleanMySaluteTV на ТВ") {
                LabeledContent("Установлен", value: tv.launcherVersion ?? "нет")
                LabeledContent("В приложении", value: Bundled.apk == nil ? "нет APK" : "есть")
                HStack {
                    Button(tv.launcherVersion == nil ? "Установить" : "Обновить") {
                        if let apk = Bundled.apk { Task { await tv.installLauncher(apk) } } else { pickFile = true }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Выбрать APK…") { pickFile = true }
                }
                .disabled(tv.busy != nil)
                Text("Настройки на ТВ при обновлении сохраняются.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Главный экран") {
                LabeledContent("Кнопка Home открывает", value: tv.homeIsOurs ? "CleanMySaluteTV" : "другой лаунчер")
                Button("Сделать главным экраном") { Task { await tv.makeHome() } }
                    .disabled(tv.busy != nil || tv.launcherVersion == nil)
            }
            Section("Заставка") {
                Button("Часы CleanMySaluteTV как заставка ТВ") { Task { await tv.useClockScreensaver() } }
                    .disabled(tv.busy != nil || tv.launcherVersion == nil)
            }
        }
        .formStyle(.grouped)
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
