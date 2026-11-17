import SwiftUI

// Liquid Glass (macOS 26): every block is a glass card floating over one of the launcher's scenes, as the
// launcher's own top buttons and dock float over theirs.

/// The window's backdrop: a scene photo the launcher ships, dimmed a little so text on glass stays readable.
struct Backdrop: View {
    var body: some View {
        GeometryReader { geo in
            Group {
                if let url = Bundled.url("Backdrop.jpg"), let img = NSImage(contentsOf: url) {
                    Image(nsImage: img).resizable().aspectRatio(contentMode: .fill)
                } else {
                    LinearGradient(colors: [Color(red: 0.94, green: 0.55, blue: 0.43), Color(red: 0.18, green: 0.14, blue: 0.40)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
            .overlay(Color.black.opacity(0.28))
        }
        .ignoresSafeArea()
    }
}

/// A titled glass card: one block of a section.
struct GlassCard<Content: View>: View {
    var title: String?
    var footer: String?
    @ViewBuilder var content: Content

    init(_ title: String? = nil, footer: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.footer = footer
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let title {
                Text(title).font(.headline)
            }
            content
            if let footer {
                Text(footer).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 26))
    }
}

/// A label on the left and its control or value on the right, as a settings row.
struct GlassRow<Trailing: View>: View {
    var label: String
    var detail: String?
    @ViewBuilder var trailing: Trailing

    init(_ label: String, detail: String? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.label = label
        self.detail = detail
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                if let detail { Text(detail).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 12)
            trailing
        }
        .frame(minHeight: 30)
    }
}

extension GlassRow where Trailing == Text {
    /// A row that only shows a value.
    init(_ label: String, value: String) {
        self.init(label) { Text(value).foregroundStyle(.secondary) }
    }
}

/// A section's page: cards stacked in a scroll view over the backdrop.
struct GlassPage<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 18) {
                VStack(spacing: 18) { content }
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                    .padding(.bottom, 80)   // room for the status capsule
                    .frame(maxWidth: 820)
                    .frame(maxWidth: .infinity)
            }
        }
        .scrollContentBackground(.hidden)
    }
}

/// A thin line between rows inside a card.
struct GlassDivider: View {
    var body: some View { Rectangle().fill(.white.opacity(0.12)).frame(height: 1) }
}
