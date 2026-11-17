import SwiftUI

@main
struct CleanMySaluteTVApp: App {
    @StateObject private var tv = TVStore()

    var body: some Scene {
        WindowGroup("CleanMySaluteTV") {
            RootView()
                .environmentObject(tv)
                .frame(minWidth: 900, minHeight: 600)
        }
        .windowResizability(.contentMinSize)
    }
}

enum Pane: String, CaseIterable, Identifiable {
    case tv, cleanup, launcher, look, apps, buttons
    var id: String { rawValue }

    var title: String {
        switch self {
        case .tv: "Телевизор"
        case .cleanup: "Очистка"
        case .launcher: "Лаунчер"
        case .look: "Вид"
        case .apps: "Приложения"
        case .buttons: "Кнопки"
        }
    }

    var icon: String {
        switch self {
        case .tv: "tv"
        case .cleanup: "sparkles"
        case .launcher: "house"
        case .look: "photo.on.rectangle"
        case .apps: "square.grid.2x2"
        case .buttons: "circle.grid.3x3"
        }
    }

    /// Needs the launcher's settings from the TV.
    var needsLauncher: Bool { [.look, .apps, .buttons].contains(self) }
}

struct RootView: View {
    @EnvironmentObject var tv: TVStore
    @State private var section: Pane = .tv

    var body: some View {
        NavigationSplitView {
            List(Pane.allCases, selection: $section) { s in
                Label(s.title, systemImage: s.icon)
                    .tag(s)
                    .foregroundStyle(s != .tv && !tv.connected ? .secondary : .primary)
            }
            .navigationSplitViewColumnWidth(190)
            .safeAreaInset(edge: .bottom) { ConnectionBadge().padding(10) }
        } detail: {
            Group {
                if section != .tv && !tv.connected {
                    Placeholder(icon: "tv.slash", text: "Сначала подключите телевизор в разделе «Телевизор»")
                } else if section.needsLauncher && tv.state == nil {
                    Placeholder(icon: "house.slash",
                                text: "Нет настроек лаунчера: установите или обновите его в разделе «Лаунчер»")
                } else {
                    switch section {
                    case .tv: TVView()
                    case .cleanup: CleanupView()
                    case .launcher: LauncherView()
                    case .look: LookView()
                    case .apps: AppsView()
                    case .buttons: ButtonsView()
                    }
                }
            }
            .navigationTitle(section.title)
            .toolbar {
                if section.needsLauncher && tv.state != nil { ApplyBar() }
            }
        }
        .overlay(alignment: .bottom) { StatusBar() }
        // The TV from last time: connect straight away.
        .task { if !tv.host.isEmpty && !tv.connected { await tv.connect() } }
    }
}

/// The settings sections' toolbar: send the edits to the TV, or drop them.
struct ApplyBar: ToolbarContent {
    @EnvironmentObject var tv: TVStore
    var body: some ToolbarContent {
        ToolbarItemGroup {
            Button("Сбросить") { tv.discardEdits() }
                .disabled(!tv.dirty || tv.busy != nil)
            Button {
                Task { await tv.apply() }
            } label: {
                Label("Применить на ТВ", systemImage: "paperplane")
            }
            .keyboardShortcut("s")
            .buttonStyle(.borderedProminent)
            .disabled(!tv.dirty || tv.busy != nil)
        }
    }
}

struct ConnectionBadge: View {
    @EnvironmentObject var tv: TVStore
    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(tv.connected ? .green : .secondary).frame(width: 8, height: 8)
            Text(tv.connected ? (tv.model.isEmpty ? "Подключён" : tv.model) : "Не подключён")
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
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(.regularMaterial, in: Capsule())
            .padding(.bottom, 12)
        }
    }
}

struct Placeholder: View {
    var icon: String
    var text: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 40)).foregroundStyle(.secondary)
            Text(text).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: 380).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
