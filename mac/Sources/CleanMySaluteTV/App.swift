import SwiftUI

@main
struct CleanMySaluteTVApp: App {
    @StateObject private var tv = TVStore()

    var body: some Scene {
        WindowGroup("CleanMySaluteTV") {
            RootView()
                .environmentObject(tv)
                .frame(minWidth: 1040, minHeight: 680)
        }
        .windowResizability(.contentMinSize)
    }
}

/// The sidebar's steps, CleanMyTV's: one-button run, then each part on its own.
enum Step: Int, CaseIterable, Identifiable {
    case quick = 1, connect, cleanup, home, extras, sideload, finish
    var id: Int { rawValue }

    var title: String {
        switch self {
        case .quick: "Очистка и запуск"
        case .connect: "Подключение"
        case .cleanup: "Очистка"
        case .home: "Главный экран"
        case .extras: "Дополнительно"
        case .sideload: "Установка приложения"
        case .finish: "Завершение"
        }
    }

    var subtitle: String {
        switch self {
        case .quick: "Всё одной кнопкой"
        case .connect: "Телевизор и бэкап"
        case .cleanup: "Отключение лишнего"
        case .home: "Лаунчер и его вид"
        case .extras: "DNS, анимации, заставка"
        case .sideload: "Из файла .apk"
        case .finish: "Закрыть порт отладки"
        }
    }

    var needsTV: Bool { self != .connect }
}

struct RootView: View {
    @EnvironmentObject var tv: TVStore
    @State private var step: Step = .connect
    @State private var finished = false   // step 7 done in this session

    var body: some View {
        NavigationSplitView {
            ScrollView {
                VStack(spacing: 6) {
                    ForEach(Step.allCases) { s in
                        StepRow(step: s, selected: step == s) { step = s }
                    }
                }
                .padding(10)
            }
            .navigationSplitViewColumnWidth(min: 250, ideal: 260)
            .safeAreaInset(edge: .bottom) { ConnectionBadge().padding(12) }
        } detail: {
            Group {
                if step.needsTV && !tv.connected {
                    Placeholder(icon: "tv.slash", text: "Сначала подключите телевизор на шаге «Подключение»") {
                        Button("Перейти к подключению") { step = .connect }.buttonStyle(.glassProminent)
                    }
                } else {
                    switch step {
                    case .quick: QuickStartView(go: { step = $0 })
                    case .connect: ConnectView()
                    case .cleanup: CleanupView()
                    case .home: HomeStepView()
                    case .extras: ExtrasView()
                    case .sideload: SideloadView()
                    case .finish: FinishView(done: $finished)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // Liquid Glass: the scene runs under the floating sidebar too
            .background { Backdrop().backgroundExtensionEffect() }
            .navigationTitle(step.title)
        }
        .overlay(alignment: .bottom) { StatusBar() }
        // The TV from last time: connect straight away, then open the Home screen step, as CleanMyTV does.
        .task {
            guard !tv.host.isEmpty, !tv.connected else { return }
            await tv.connect()
            if tv.connected && tv.homeIsOurs { step = .home }
        }
    }
}

/// One step: its number in a ring, the name, what it is for.
struct StepRow: View {
    var step: Step
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().strokeBorder(selected ? Color.accentColor : .secondary, lineWidth: 1.5)
                    Text("\(step.rawValue)").font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(selected ? Color.accentColor : .primary)
                }
                .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(step.title).font(.system(size: 14, weight: .semibold))
                    Text(step.subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10).padding(.vertical, 9)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .glassEffect(selected ? .regular.interactive() : .identity, in: .rect(cornerRadius: 16))
    }
}

struct ConnectionBadge: View {
    @EnvironmentObject var tv: TVStore
    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(tv.connected ? .green : .secondary).frame(width: 8, height: 8)
            Text(tv.connected ? (tv.model.isEmpty ? "Подключён" : "\(tv.model) · \(tv.host)") : "Не подключён")
                .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            Spacer()
        }
    }
}

/// What is running now, and the last result, along the window's bottom.
struct StatusBar: View {
    @EnvironmentObject var tv: TVStore
    var body: some View {
        if let text = tv.busy ?? tv.message {
            HStack(spacing: 8) {
                if tv.busy != nil { ProgressView().controlSize(.small) }
                Text(text).font(.callout).lineLimit(2)
                if tv.busy == nil {
                    Button { tv.message = nil } label: { Image(systemName: "xmark") }
                        .buttonStyle(.plain).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .glassEffect(.regular, in: .capsule)
            .padding(.bottom, 14)
            .animation(.smooth, value: text)
        }
    }
}

struct Placeholder<Extra: View>: View {
    var icon: String
    var text: String
    @ViewBuilder var extra: Extra

    init(icon: String, text: String, @ViewBuilder extra: () -> Extra = { EmptyView() }) {
        self.icon = icon
        self.text = text
        self.extra = extra()
    }

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 40))
            Text(text).multilineTextAlignment(.center)
            extra
        }
        .padding(28)
        .frame(maxWidth: 420)
        .glassEffect(.regular, in: .rect(cornerRadius: 30))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
