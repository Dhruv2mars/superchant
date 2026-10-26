import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct SuperChantWidgets: WidgetBundle {
  var body: some Widget {
    ChantLiveActivity()
  }
}

private let gold = Color(red: 1, green: 0.725, blue: 0.353)  // #FFB95A, the dark theme's counter gold
private let cream = Color(red: 0.922, green: 0.878, blue: 0.851)  // #EBE0D9
private let roundSize = 108

/// The session on the Lock Screen and in the Dynamic Island: the bead count in the round, rounds done, the goal's
/// current mantra, and pause/resume. Updates arrive from the app at most 4 times a second.
struct ChantLiveActivity: Widget {
  var body: some WidgetConfiguration {
    ActivityConfiguration(for: ChantActivityAttributes.self) { context in
      LockScreenView(context: context)
        .activityBackgroundTint(Color(red: 0.086, green: 0.075, blue: 0.059).opacity(0.92))
        .activitySystemActionForegroundColor(gold)
    } dynamicIsland: { context in
      DynamicIsland {
        DynamicIslandExpandedRegion(.leading) {
          Ring(progress: progress(context.state), size: 44)
            .overlay(Text("\(context.state.roundCount)").font(.system(.callout, design: .rounded).weight(.semibold)).monospacedDigit())
            .padding(.leading, 4)
        }
        DynamicIslandExpandedRegion(.trailing) {
          PauseButton(paused: context.state.paused, disabled: context.state.goalComplete)
        }
        DynamicIslandExpandedRegion(.center) {
          VStack(spacing: 2) {
            Text(title(context)).font(.headline).lineLimit(1)
            Text(subtitle(context.state)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
          }
        }
      } compactLeading: {
        Ring(progress: progress(context.state), size: 22)
      } compactTrailing: {
        Text(context.state.paused ? "II" : "\(context.state.roundCount)")
          .font(.system(.body, design: .rounded).weight(.semibold))
          .monospacedDigit()
          .foregroundStyle(gold)
          .contentTransition(.numericText())
      } minimal: {
        Ring(progress: progress(context.state), size: 22)
      }
      .keylineTint(gold)
    }
  }
}

private func progress(_ s: ChantActivityAttributes.ContentState) -> Double { Double(s.roundCount) / Double(roundSize) }

private func title(_ context: ActivityViewContext<ChantActivityAttributes>) -> String {
  context.attributes.goalName ?? "SuperChant"
}

private func subtitle(_ s: ChantActivityAttributes.ContentState) -> String {
  if s.goalComplete { return "Goal complete" }
  if s.paused { return "Paused" }
  if let mantra = s.mantra { return "\(mantra) · \(s.mantraRounds)/\(s.mantraTarget)" }
  return s.rounds == 1 ? "1 round" : "\(s.rounds) rounds"
}

struct LockScreenView: View {
  let context: ActivityViewContext<ChantActivityAttributes>

  var body: some View {
    HStack(spacing: 16) {
      Ring(progress: progress(context.state), size: 56)
        .overlay(
          Text("\(context.state.roundCount)")
            .font(.system(.title3, design: .rounded).weight(.semibold))
            .monospacedDigit()
            .contentTransition(.numericText())
        )
      VStack(alignment: .leading, spacing: 4) {
        Text(title(context)).font(.headline).foregroundStyle(cream).lineLimit(1)
        Text(subtitle(context.state)).font(.subheadline).foregroundStyle(cream.opacity(0.7)).lineLimit(1)
        Text(context.attributes.startedAt, style: .timer).font(.caption).monospacedDigit().foregroundStyle(cream.opacity(0.6))
      }
      Spacer(minLength: 0)
      PauseButton(paused: context.state.paused, disabled: context.state.goalComplete)
    }
    .padding(16)
    .accessibilityElement(children: .combine)
  }
}

struct Ring: View {
  let progress: Double
  let size: CGFloat

  var body: some View {
    ZStack {
      Circle().stroke(gold.opacity(0.25), lineWidth: size * 0.1)
      Circle()
        .trim(from: 0, to: progress)
        .stroke(gold, style: StrokeStyle(lineWidth: size * 0.1, lineCap: .round))
        .rotationEffect(.degrees(-90))
    }
    .frame(width: size, height: size)
    .padding(size * 0.05)
  }
}

struct PauseButton: View {
  let paused: Bool
  let disabled: Bool

  var body: some View {
    Button(intent: TogglePauseIntent()) {
      Image(systemName: paused ? "play.fill" : "pause.fill")
        .font(.title3)
        .frame(width: 44, height: 44)
        .background(gold.opacity(0.2), in: Circle())
        .foregroundStyle(gold)
    }
    .buttonStyle(.plain)
    .disabled(disabled)
    .accessibilityLabel(paused ? "Resume" : "Pause")
  }
}
