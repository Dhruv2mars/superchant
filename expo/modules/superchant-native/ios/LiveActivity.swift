import ActivityKit
import AppIntents
import Foundation

/// What the Live Activity shows. The widget extension (targets/widget) has an identical copy: ActivityKit and
/// App Intents match the two by type name.
public struct ChantActivityAttributes: ActivityAttributes {
  public struct ContentState: Codable, Hashable {
    /// Beads in the current round, 0-107.
    public var roundCount: Int
    public var rounds: Int
    public var paused: Bool
    public var goalComplete: Bool
    /// The current mantra and its progress ("1 / 2 rounds"), for goal sessions.
    public var mantra: String?
    public var mantraRounds: Int
    public var mantraTarget: Int
  }

  public var goalName: String?
  /// When the session started, for the elapsed timer.
  public var startedAt: Date
}

/// Starts, updates (at most 4 times a second, trailing edge kept) and ends the session's Live Activity.
@MainActor
final class LiveActivityController {
  static let shared = LiveActivityController()

  private var activity: Activity<ChantActivityAttributes>?
  private var pending: ChantActivityAttributes.ContentState?
  private var lastSent: ChantActivityAttributes.ContentState?
  private var lastSentAt: CFTimeInterval = 0
  private var timer: Timer?
  private static let interval: CFTimeInterval = 0.25

  private init() {
    // A Live Activity left by a previous process belongs to the stored session; adopt it instead of stacking.
    activity = Activity<ChantActivityAttributes>.activities.first
  }

  private static func content(_ s: ChantState) -> ChantActivityAttributes.ContentState {
    .init(
      roundCount: s.currentRoundCount, rounds: s.rounds, paused: s.isPaused, goalComplete: s.isGoalComplete,
      mantra: s.currentMantra?.name, mantraRounds: s.mantraRoundsCompleted, mantraTarget: s.currentMantra?.targetRounds ?? 0)
  }

  func start(for state: ChantState) {
    guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
    endAll()
    let attributes = ChantActivityAttributes(
      goalName: state.activeGoal?.name,
      startedAt: Date(timeIntervalSince1970: TimeInterval(state.sessionStartTime) / 1000))
    let content = Self.content(state)
    activity = try? Activity.request(attributes: attributes, content: .init(state: content, staleDate: nil))
    lastSent = content
    lastSentAt = CACurrentMediaTime()
  }

  func update(for state: ChantState) {
    guard activity != nil, state.isActive else { return }
    let content = Self.content(state)
    if content == lastSent { return }
    pending = content
    let wait = Self.interval - (CACurrentMediaTime() - lastSentAt)
    if wait <= 0 {
      flush()
    } else if timer == nil {
      timer = Timer.scheduledTimer(withTimeInterval: wait, repeats: false) { _ in
        Task { @MainActor in LiveActivityController.shared.flush() }
      }
    }
  }

  private func flush() {
    timer?.invalidate()
    timer = nil
    guard let activity, let content = pending else { return }
    pending = nil
    lastSent = content
    lastSentAt = CACurrentMediaTime()
    Task { await activity.update(.init(state: content, staleDate: nil)) }
  }

  func end() {
    timer?.invalidate()
    timer = nil
    pending = nil
    endAll()
    activity = nil
  }

  private func endAll() {
    for a in Activity<ChantActivityAttributes>.activities {
      Task { await a.end(nil, dismissalPolicy: .immediate) }
    }
  }
}

/// The Live Activity's pause/resume button. As a LiveActivityIntent it runs in the app's process (launched in the
/// background if needed) and changes the native session directly, with no JavaScript involved.
@available(iOS 17.0, *)
public struct TogglePauseIntent: LiveActivityIntent {
  public static var title: LocalizedStringResource = "Pause or resume counting"
  public static var isDiscoverable = false

  public init() {}

  public func perform() async throws -> some IntentResult {
    await MainActor.run { SessionEngine.shared.togglePauseFromLiveActivity() }
    return .result()
  }
}
