import ExpoModulesCore
import SwiftUI

/// The mala ring and tap-to-count pad (CounterDisplay.kt and TapToCountPad in OnScreenCounter.kt) in SwiftUI.
/// It observes SessionEngine directly, so a tap counts, buzzes and redraws without JavaScript.
final class CounterViewProps: ExpoSwiftUI.ViewProps {
  @Field var dark: Bool = false
  @Field var volumeReady: Bool = false
}

struct CounterView: ExpoSwiftUI.View, ExpoSwiftUI.WithHostingView {
  @ObservedObject var props: CounterViewProps
  @ObservedObject private var engine = SessionEngine.shared

  init(props: CounterViewProps) {
    self.props = props
  }

  var body: some View {
    let palette = Palette(dark: props.dark)
    let state = engine.state
    if !state.isActive {
      MalaRing(state: ChantState(), palette: palette, footer: nil)
    } else {
      TapPad(
        enabled: state.touchCounting && !state.isPaused && !state.isGoalComplete,
        label: Strings.countDescription(state),
        palette: palette,
        onCount: { engine.countFromScreen() }
      ) {
        VStack(spacing: 0) {
          MalaRing(state: state, palette: palette, footer: state.rounds > 0 ? Strings.rounds(state.rounds) : nil)
            .padding(.top, 8)
            .padding(.horizontal, 8)
          Text(Strings.status(state))
            .font(.subheadline)
            .foregroundStyle(palette.onSurfaceVariant)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
            .accessibilityHidden(true)
        }
      }
    }
  }
}

/// App colors (src/theme/colors.ts).
struct Palette {
  let dark: Bool
  var gold: Color { dark ? Color(hex: 0xFFB95A) : Color(hex: 0xD4A574) }
  var track: Color { dark ? Color(hex: 0x3A3330) : Color(hex: 0xE8DDD4) }
  var onSurface: Color { dark ? Color(hex: 0xEBE0D9) : Color(hex: 0x1F1B16) }
  var onSurfaceVariant: Color { dark ? Color(hex: 0xD5C4B5) : Color(hex: 0x504539) }
  var surfaceVariant: Color { dark ? Color(hex: 0x504539) : Color(hex: 0xF2E0D0) }
  var primary: Color { dark ? Color(hex: 0xFFB95A) : Color(hex: 0x8C6136) }
}

extension Color {
  init(hex: UInt32) {
    self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
  }
}

/// Strings the native counter shows (res/values/strings.xml).
enum Strings {
  static func rounds(_ n: Int) -> String { n == 1 ? "1 round" : "\(n) rounds" }
  static func countDescription(_ s: ChantState) -> String { "Count \(s.currentRoundCount) of \(roundSize). \(rounds(s.rounds))" }
  static func status(_ s: ChantState) -> String {
    if s.isGoalComplete { return "Goal complete. Tap Stop to save it." }
    if s.isPaused { return "Paused. Tap ▶ to continue." }
    return s.touchCounting ? "Tap anywhere here to count" : "Turn on touch counting to count"
  }
  static let countAction = "Count"
}

/// The ring: beads around it (one per count when they fit), a larger meru bead at the top, a barely-there pulse
/// on each count and a slow glow when a round completes. Motion stays calm: it runs 108 times a round.
struct MalaRing: View {
  let state: ChantState
  let palette: Palette
  let footer: String?

  @State private var shownProgress: Double = 0
  @State private var glow: Double = 0
  @State private var pulse: CGFloat = 1
  @State private var lastCount = -1
  @State private var lastRounds = -1

  var body: some View {
    GeometryReader { geo in
      let size = min(geo.size.width, geo.size.height)
      let countSize = min(max(size * 0.22, 32), 96)
      ZStack {
        Canvas { context, canvasSize in
          draw(in: &context, size: canvasSize)
        }
        .padding(8)
        .accessibilityHidden(true)
        VStack(spacing: 0) {
          Text("\(state.currentRoundCount)")
            .font(.system(size: countSize, weight: .light))
            .monospacedDigit()
            .foregroundStyle(palette.onSurface)
            .scaleEffect(pulse)
          Text("/ \(roundSize)")
            .font(size < 220 ? .headline : .title2.weight(.semibold))
            .foregroundStyle(palette.onSurfaceVariant)
          if let footer {
            Text(footer)
              .font(.subheadline.weight(.medium))
              .foregroundStyle(palette.primary)
              .padding(.top, 4)
          }
        }
        .accessibilityHidden(true)
      }
      .frame(width: size, height: size)
      .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    .onAppear {
      shownProgress = state.progress
      lastCount = state.count
      lastRounds = state.rounds
    }
    .onChange(of: state.count) { _, count in
      let completedRound = count > 0 && state.currentRoundCount == 0 && shownProgress > 0.5
      if completedRound {
        // Close the circle, then start again from the meru bead
        withAnimation(.easeOut(duration: 0.16)) { shownProgress = 1 } completion: {
          var t = Transaction()
          t.disablesAnimations = true
          withTransaction(t) { shownProgress = 0 }
        }
      } else {
        withAnimation(.spring(response: 0.35, dampingFraction: 1)) { shownProgress = state.progress }
      }
      if count > lastCount && count > 0 {
        pulse = 1.03
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.14)) { pulse = 1 }
      }
      lastCount = count
    }
    .onChange(of: state.rounds) { _, rounds in
      if rounds > lastRounds && lastRounds >= 0 {
        glow = 1
        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 1.6)) { glow = 0 }
      }
      lastRounds = rounds
    }
  }

  private func draw(in context: inout GraphicsContext, size: CGSize) {
    let outer = min(size.width, size.height) / 2
    let g = MalaGeometry.forRing(outerRadius: outer)
    let center = CGPoint(x: size.width / 2, y: size.height / 2)
    func circle(_ c: CGPoint, _ r: Double) -> Path {
      Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }
    context.stroke(circle(center, g.ringRadius), with: .color(palette.track), lineWidth: g.strokeWidth)
    if glow > 0 {
      context.stroke(
        circle(center, g.ringRadius), with: .color(palette.gold.opacity(0.35 * glow)), lineWidth: g.strokeWidth * (1 + 2.5 * glow))
    }
    if shownProgress > 0 {
      var arc = Path()
      arc.addArc(
        center: center, radius: g.ringRadius, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * shownProgress), clockwise: false)
      context.stroke(arc, with: .color(palette.gold), style: StrokeStyle(lineWidth: g.strokeWidth, lineCap: .round))
    }
    for i in 0..<g.beadCount {
      let angle = g.beadAngle(i)
      let lit = shownProgress >= Double(i + 1) / Double(g.beadCount) - 0.0001
      let p = CGPoint(x: center.x + g.beadOrbit * cos(angle), y: center.y + g.beadOrbit * sin(angle))
      context.fill(circle(p, lit ? g.beadRadius : g.beadRadius * 0.62), with: .color(lit ? palette.gold : palette.track))
    }
    let meru = CGPoint(x: center.x, y: center.y - g.beadOrbit)
    context.fill(circle(meru, g.meruRadius), with: .color(palette.gold))
    context.fill(circle(meru, g.meruRadius * 0.42), with: .color(palette.track))
  }
}

/// While enabled, a soft tinted surface grows in under the ring and every touch counts one bead on finger-down;
/// extra fingers are ignored until all lift. VoiceOver reads the count and counts on double-tap.
struct TapPad<Content: View>: View {
  let enabled: Bool
  let label: String
  let palette: Palette
  let onCount: () -> Void
  @ViewBuilder let content: () -> Content

  @State private var pressed = false
  @State private var reveal: Double = 0

  var body: some View {
    let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
    content()
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .background {
        if reveal > 0 {
          let inset = (1 - reveal) * 12
          shape
            .fill(palette.surfaceVariant.opacity(0.45 * reveal))
            .overlay(shape.strokeBorder(palette.primary.opacity(0.22 * reveal), lineWidth: 1))
            .padding(inset)
        }
      }
      .overlay(shape.fill(palette.primary.opacity(pressed ? 0.10 : 0)).allowsHitTesting(false))
      .clipShape(shape)
      .contentShape(shape)
      .gesture(
        DragGesture(minimumDistance: 0)
          .onChanged { _ in
            guard enabled, !pressed else { return }
            withAnimation(.linear(duration: 0.04)) { pressed = true }
            onCount()
          }
          .onEnded { _ in withAnimation(.easeOut(duration: 0.22)) { pressed = false } },
        including: enabled ? .all : .none
      )
      .onAppear { reveal = enabled ? 1 : 0 }
      .onChange(of: enabled) { _, on in
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: on ? 0.24 : 0.16)) { reveal = on ? 1 : 0 }
        if !on { pressed = false }
      }
      .accessibilityElement(children: .ignore)
      .accessibilityLabel(label)
      .accessibilityAddTraits(.updatesFrequently)
      .accessibilityAction { if enabled { onCount() } }
      .accessibilityAction(named: Strings.countAction) { if enabled { onCount() } }
  }
}
