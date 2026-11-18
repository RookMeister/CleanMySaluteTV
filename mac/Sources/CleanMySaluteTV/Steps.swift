import SwiftUI
import UniformTypeIdentifiers
import CoreImage

// MARK: - 1. Очистка и запуск

struct QuickStartView: View {
    @EnvironmentObject var tv: TVStore
    var go: (Step) -> Void
    @State private var stage: TVStore.RunStage?
    @State private var running = false
    @State private var result: Bool?

    private let stages: [(TVStore.RunStage, String)] = [
        (.backup, "Бэкап: что было на ТВ до изменений"),
        (.cleanup, "Отключить рекомендуемые приложения Sber"),
        (.install, "Поставить или обновить CleanMySaluteTV"),
        (.home, "Сделать его главным экраном"),
    ]

    var body: some View {
        GlassPage {
            GlassCard("Всё одной кнопкой",
                      footer: "Ничего не удаляется. «Вернуть всё как было» на шаге «Подключение» включит отключённое и вернёт лаунчер Sber; каждый шаг можно пройти и по отдельности в меню слева.") {
                Text("Бэкап, отключение лишнего из списка для Sber, установка лаунчера и он же — главный экран. Потом откроется шаг «Главный экран», где всё настраивается.")
                ForEach(stages, id: \.0) { s, text in
                    HStack(spacing: 10) {
                        Group {
                            if let cur = stage, index(of: s) < index(of: cur) || (result != nil && running == false) {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                            } else if stage == s && running {
                                ProgressView().controlSize(.small)
                            } else {
                                Image(systemName: "circle").foregroundStyle(.secondary)
                            }
                        }
                        .frame(width: 20)
                        Text(text)
                    }
                }
                HStack {
                    Button {
                        run(cleanup: true)
                    } label: { Label("Очистить и запустить", systemImage: "sparkles") }
                        .buttonStyle(.glassProminent)
                    Button("Только главный экран") { run(cleanup: false) }.buttonStyle(.glass)
                    Button("Пройду по шагам") { go(.connect) }.buttonStyle(.glass)
                }
                .disabled(running || tv.busy != nil)
                .padding(.top, 4)
            }
            if let ok = result {
                GlassCard(ok ? "Готово" : "Не всё получилось") {
                    Text(ok ? "CleanMySaluteTV — главный экран ТВ. Дальше: расставить приложения и выбрать вид."
                            : "Посмотрите сообщение внизу окна и пройдите шаги по отдельности.")
                    HStack {
                        Button("Настроить главный экран") { go(.home) }.buttonStyle(.glassProminent)
                        Button("Завершение: перезагрузка и порт") { go(.finish) }.buttonStyle(.glass)
                    }
                }
            }
        }
    }

    private func index(of s: TVStore.RunStage) -> Int { stages.firstIndex { $0.0 == s } ?? 0 }

    private func run(cleanup: Bool) {
        running = true
        result = nil
        Task {
            let ok = await tv.quickRun(cleanup: cleanup) { s in stage = s }
            running = false
            result = ok
        }
    }
}

// MARK: - 2. Подключение

/// How to turn ADB on: from a phone in Sber Studio (docs/SBER.md), with a QR code that opens it there.
struct EnableADBCard: View {
    @EnvironmentObject var tv: TVStore
    static let studio = URL(string: "https://developers.sber.ru/studio/")!
    @State private var open = false

    private static let steps = [
        "На ТВ: «Настройки → Об устройстве» — найдите серийный номер. Он нужен только вам, никому его не отправляйте.",
        "На телефоне откройте Сбер Studio (QR-код справа) и войдите под тем же Сбер ID, что и на ТВ.",
        "Значок меню слева вверху → «Настройки» → снова значок меню → «Настройки сервисов» → SmartApp. Если SmartApp нет: на главной «Создать проект» → «Мини-приложения Салют» → NativeApp → любое название → «Создать проект» (публиковать и загружать APK не нужно), затем снова «Настройки сервисов».",
        "Вкладка «Мои устройства» вверху (если не видно — проведите по вкладкам влево).",
        "Если ТВ нет в списке — «Добавить»: серийный номер в «Идентификатор устройства» с точным регистром букв, «Тип устройства» — TV.",
        "В строке ТВ включите «Функция ADB» (таблицу можно провести влево). Подождите минуту и перезагрузите ТВ.",
        "Введите ниже IP-адрес ТВ. После установки выключите ADB в Studio — шаг «Завершение» напомнит.",
    ]

    var body: some View {
        GlassCard("Сначала включите на ТВ отладку", footer: "Xiaomi, Mi Box и другие Android TV / Google TV, а также Салют ТВ с открытым меню разработчика: Настройки → Настройки устройства (на Google TV — Система) → Об устройстве → 7 раз OK на «Сборка» → назад → Для разработчиков → Отладка по USB (и «Отладка по сети», если есть).") {
            DisclosureGroup(isExpanded: $open) {
                HStack(alignment: .top, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(Self.steps.enumerated()), id: \.offset) { i, step in
                            Label(step, systemImage: "\(i + 1).circle").fixedSize(horizontal: false, vertical: true)
                        }
                        Link("Открыть Сбер Studio на этом Mac", destination: Self.studio).padding(.top, 4)
                    }
                    VStack(spacing: 6) {
                        if let qr = QRCode.image(Self.studio.absoluteString) {
                            Image(nsImage: qr).interpolation(.none).resizable()
                                .frame(width: 132, height: 132)
                                .padding(8).background(.white, in: RoundedRectangle(cornerRadius: 12))
                        }
                        Text("Сбер Studio").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.top, 8)
            } label: {
                Text("Салют ТВ: с телефона через Сбер Studio — 7 шагов, около 5 минут").font(.headline)
            }
            .onAppear { open = !tv.connected }
        }
    }
}

/// A QR code for a link, drawn by Core Image.
enum QRCode {
    static func image(_ text: String) -> NSImage? {
        guard let f = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        f.setValue(Data(text.utf8), forKey: "inputMessage")
        f.setValue("M", forKey: "inputCorrectionLevel")
        guard let out = f.outputImage else { return nil }
        let rep = NSCIImageRep(ciImage: out.transformed(by: CGAffineTransform(scaleX: 8, y: 8)))
        let img = NSImage(size: rep.size)
        img.addRepresentation(rep)
        return img
    }
}

struct ConnectView: View {
    @EnvironmentObject var tv: TVStore
    @State private var confirmRestore = false

    var body: some View {
        GlassPage {
            EnableADBCard()
            GlassCard("Телевизор в сети",
                      footer: "IP-адрес на ТВ: Настройки → Об устройстве → Статус системы → IP-адрес. Если ТВ спросит «Разрешить отладку?», отметьте «Всегда» и нажмите OK.") {
                HStack(spacing: 10) {
                    TextField("IP-адрес телевизора", text: $tv.host, prompt: Text("192.168.1.20"))
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .glassEffect(.regular, in: .capsule)
                        .onSubmit { Task { await tv.connect() } }
                    Button(tv.connected ? "Переподключить" : "Подключить") { Task { await tv.connect() } }
                        .buttonStyle(.glassProminent)
                    Button("Найти в сети") { Task { await tv.scan() } }.buttonStyle(.glass)
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
                    GlassRow("Главный экран", value: tv.homeIsOurs ? "CleanMySaluteTV \(tv.launcherVersion ?? "")" : "лаунчер Sber")
                    HStack {
                        Button("Обновить") { Task { await tv.refreshAll() } }.buttonStyle(.glass)
                        Button {
                            Task { await tv.takeScreenshot() }
                        } label: { Label("Снять экран", systemImage: "camera.viewfinder") }.buttonStyle(.glass)
                        Button("Отключиться") { Task { await tv.disconnect() } }.buttonStyle(.glass)
                    }
                    .padding(.top, 4)
                    if let img = tv.screenshot {
                        Image(nsImage: img).resizable().aspectRatio(contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
                GlassCard("Бэкап", footer: "Хранится на этом Mac: ~/Library/Application Support/CleanMySaluteTV. Делается сам перед первым изменением.") {
                    GlassRow("Бэкап этого ТВ", value: tv.hasBackup ? "есть" : "ещё нет")
                    GlassDivider()
                    GlassRow("Отключено этим приложением", value: "\(tv.disabledByUs.count)")
                    HStack {
                        Button("Сделать бэкап") { Task { await tv.backup() } }.buttonStyle(.glass).disabled(tv.hasBackup)
                        Button("Вернуть всё как было") { confirmRestore = true }.buttonStyle(.glass)
                    }
                    .padding(.top, 4)
                }
            }
        }
        .alert("Вернуть ТВ к состоянию до CleanMySaluteTV?", isPresented: $confirmRestore) {
            Button("Вернуть") { Task { await tv.restoreAll() } }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Включатся все отключённые здесь приложения, главным экраном станет лаунчер Sber, заставка и DNS — как были. Сам CleanMySaluteTV останется установлен.")
        }
    }
}

// MARK: - 3. Очистка

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
                    Button("Очистить кэш приложений") { Task { await tv.trimCaches() } }.buttonStyle(.glass)
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

    private var groups: [Group] {
        let on = tv.profile.filter { tv.installed.contains($0.package) }
        let off = tv.profile.filter { !tv.installed.contains($0.package) }
        return [Group(title: "Советую отключить", items: on.filter { $0.action == .disable }),
                Group(title: "По желанию", items: on.filter { $0.action == .ask }),
                Group(title: "Лучше оставить", items: on.filter { $0.action == .keep }),
                Group(title: "Нет на этом ТВ", items: off)].filter { !$0.items.isEmpty }
    }

    @ViewBuilder private func row(_ e: ProfileEntry) -> some View {
        GlassRow(e.note.isEmpty ? e.package : e.note, detail: e.package) {
            if tv.installed.contains(e.package) {
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

// MARK: - 5. Дополнительно

struct ExtrasView: View {
    @EnvironmentObject var tv: TVStore
    @State private var extras = TVStore.Extras()
    @State private var loaded = false
    @State private var dnsHost = "dns.adguard-dns.com"

    var body: some View {
        GlassPage {
            GlassCard("Скорость анимаций", footer: "Как быстро открываются меню и приложения на всём ТВ. «Выкл» — самое отзывчивое на слабом ТВ.") {
                Picker("", selection: Binding(get: { extras.animation }, set: { v in
                    extras.animation = v
                    Task { await tv.setAnimation(v) }
                })) {
                    Text("Выкл").tag("0"); Text("Быстро (0.5×)").tag("0.5"); Text("Обычно (1×)").tag("1")
                }
                .labelsHidden().pickerStyle(.segmented).fixedSize()
            }
            GlassCard("Блокировка рекламы в приложениях",
                      footer: "Частный DNS отсекает рекламу и слежку во всех приложениях. Если что-то перестанет грузиться или ТВ напишет «Нет интернета» — выключите.") {
                GlassRow("Сейчас", value: extras.dnsMode == "hostname" ? extras.dnsHost : "выключено (автоматически)")
                HStack(spacing: 10) {
                    TextField("Сервер", text: $dnsHost)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .glassEffect(.regular, in: .capsule)
                    Button("Включить") {
                        Task { await tv.setDNS(dnsHost); extras = await tv.readExtras() }
                    }.buttonStyle(.glassProminent)
                    Button("Выключить") {
                        Task { await tv.setDNS(nil); extras = await tv.readExtras() }
                    }.buttonStyle(.glass)
                }
                Text("AdGuard: dns.adguard-dns.com · или свой, например dns.nextdns.io").font(.caption).foregroundStyle(.secondary)
            }
            GlassCard("Заставка") {
                GlassRow("Сейчас", value: screensaverName)
                HStack {
                    Button("Часы CleanMySaluteTV") { set("clock") }.buttonStyle(.glass)
                    Button("Как была на ТВ") { set("original") }.buttonStyle(.glass)
                    Button("Выключить") { set("off") }.buttonStyle(.glass)
                }
            }
        }
        .task {
            guard !loaded else { return }
            extras = await tv.readExtras()
            if extras.dnsMode == "hostname" && !extras.dnsHost.isEmpty { dnsHost = extras.dnsHost }
            loaded = true
        }
    }

    private var screensaverName: String {
        if extras.screensaver.isEmpty { return "выключена" }
        if extras.screensaver.contains(TVStore.pkg) { return "часы CleanMySaluteTV" }
        return extras.screensaver == "null" ? "по умолчанию" : extras.screensaver
    }

    private func set(_ choice: String) {
        Task {
            await tv.setScreensaver(choice)
            extras = await tv.readExtras()
        }
    }
}

// MARK: - 6. Установка приложения

struct SideloadView: View {
    @EnvironmentObject var tv: TVStore
    @State private var pick = false
    @State private var dropping = false
    @State private var last: String?

    var body: some View {
        GlassPage {
            GlassCard("Установить приложение", footer: "Ставьте только приложения, которым доверяете. Если оно уже есть на ТВ, обновится с сохранением данных.") {
                VStack(spacing: 12) {
                    Image(systemName: "arrow.down.app").font(.system(size: 44))
                    Text("Перетащите сюда файл .apk").font(.title3)
                    Button("Выбрать файл…") { pick = true }.buttonStyle(.glassProminent)
                    if let last { Text(last).font(.callout).foregroundStyle(.secondary) }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
                .glassEffect(dropping ? .regular.tint(.accentColor.opacity(0.4)) : .clear, in: .rect(cornerRadius: 22))
                .dropDestination(for: URL.self) { urls, _ in
                    guard let u = urls.first(where: { $0.pathExtension.lowercased() == "apk" }) else { return false }
                    install(u)
                    return true
                } isTargeted: { dropping = $0 }
            }
        }
        .fileImporter(isPresented: $pick, allowedContentTypes: [UTType(filenameExtension: "apk") ?? .data]) { r in
            if case .success(let u) = r { install(u) }
        }
    }

    private func install(_ url: URL) {
        Task {
            let ok = url.startAccessingSecurityScopedResource()
            let done = await tv.sideload(url)
            if ok { url.stopAccessingSecurityScopedResource() }
            last = done ? "\(url.lastPathComponent) — установлено" : "\(url.lastPathComponent) — не установилось"
        }
    }
}

// MARK: - 7. Завершение

struct FinishView: View {
    @EnvironmentObject var tv: TVStore
    @Binding var done: Bool
    @State private var restartOK: Bool?
    @State private var portClosed: Bool?

    var body: some View {
        GlassPage {
            GlassCard("1. Проверка перезагрузкой",
                      footer: "Перезагрузите ТВ один раз, пока отладка включена: если он вернётся к чёрному экрану, приложение ещё сможет вернуть лаунчер Sber. С выключенной отладкой помог бы только сброс.") {
                HStack {
                    Button {
                        Task { restartOK = await tv.restartCheck() }
                    } label: { Label("Перезагрузить и проверить", systemImage: "arrow.clockwise") }
                        .buttonStyle(.glassProminent).disabled(tv.busy != nil)
                    if let ok = restartOK {
                        Label(ok ? "ТВ вернулся на CleanMySaluteTV" : "После перезагрузки не наш экран",
                              systemImage: ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundStyle(ok ? .green : .orange)
                    }
                }
                if restartOK == false {
                    Button("Вернуть лаунчер Sber") { Task { await tv.useStockLauncher() } }.buttonStyle(.glass)
                }
            }
            GlassCard("2. На телевизоре") {
                Label("Сбер Studio → Мои устройства → выключите «Функция ADB» (или на ТВ: Для разработчиков → «Отладка по USB»).", systemImage: "1.circle")
                Label("По желанию: «Отозвать авторизацию отладки», чтобы ТВ больше не доверял этому Mac.", systemImage: "2.circle")
                Label("Перезагрузите ТВ.", systemImage: "3.circle")
                Text("Чтобы потом что-то поменять: включите отладку снова и откройте это приложение — оно само подключится и откроет «Главный экран».")
                    .font(.caption).foregroundStyle(.secondary)
            }
            GlassCard("3. Проверка с этого Mac", footer: "Когда отладка выключена, порт 5555 не должен отвечать: к ТВ больше никто не подключится по сети.") {
                HStack {
                    Button("Проверить, что порт 5555 закрыт") {
                        Task {
                            portClosed = await tv.debugPortClosed()
                            done = portClosed == true
                        }
                    }
                    .buttonStyle(.glass)
                    if let closed = portClosed {
                        Label(closed ? "Закрыт" : "Ещё открыт", systemImage: closed ? "lock.fill" : "lock.open.fill")
                            .foregroundStyle(closed ? .green : .orange)
                    }
                }
            }
        }
    }
}
