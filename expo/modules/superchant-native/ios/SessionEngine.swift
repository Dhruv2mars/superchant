import Foundation
import QuartzCore

/// The live session on iOS, the counterpart of Android's ChantingService. Counting, haptics, persistence and
/// the Live Activity happen here, so a tap never waits for JavaScript; JS hears only coarse changes. History,
/// goals and settings live in TypeScript (src/native/platform.ios.ts), which saves a finished session before
/// clearing the stored copy, in the same order as the Kotlin repository.
@MainActor
public final class SessionEngine: ObservableObject {
  public static let shared = SessionEngine()

  @Published public private(set) var state = ChantState() {
    didSet { Self.publishSnapshot(state) }
  }
  /// Called on start, stop, pause, touch counting, a new round and goal progress: never once per count.
  var onCoarseChange: ((ChantState) -> Void)?

  static let storageKey = "superchant.active_session"
  private static let staleAfterMs: Int64 = 3 * 60 * 60 * 1000
  private let defaults = UserDefaults.standard
  private let debouncer = CountDebouncer()

  private init() {
    // Launched just to run the Live Activity's pause button: pick up the stored session so it can be paused.
    if let stored = loadStored(), stored.isActive, Self.now() - stored.lastUpdatedAt <= Self.staleAfterMs {
      state = stored
    }
    Self.publishSnapshot(state)
  }

  static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

  // The latest state for readers on other threads (JS calls getSession on its own thread).
  private static let lock = NSLock()
  nonisolated(unsafe) private static var latest = ChantState()
  private static func publishSnapshot(_ s: ChantState) {
    lock.lock()
    latest = s
    lock.unlock()
  }
  nonisolated static var snapshot: ChantState {
    lock.lock()
    defer { lock.unlock() }
    return latest
  }

  // MARK: Commands

  func start(goal: Goal?, touchCounting: Bool) {
    set(ChantState.start(goal: goal, now: Self.now(), touchCounting: touchCounting))
    LiveActivityController.shared.start(for: state)
  }

  /// A tap on the counter. Extra triggers within 150 ms are dropped (a tap reported twice).
  @discardableResult
  func countFromScreen() -> Bool {
    guard state.touchCounting, debouncer.tryAccept(CACurrentMediaTime()) else { return false }
    return increment()
  }

  @discardableResult
  private func increment() -> Bool {
    guard let result = ChantCounter.increment(state) else { return false }
    var next = result.state
    next.lastUpdatedAt = Self.now()
    set(next)
    Feedback.shared.play(result.feedback, rounds: next.rounds)
    return true
  }

  func setPaused(_ paused: Bool) {
    guard state.isActive, state.isPaused != paused else { return }
    var next = state
    next.isPaused = paused
    next.lastUpdatedAt = Self.now()
    set(next)
  }

  func setTouchCounting(_ on: Bool) {
    guard state.isActive, state.touchCounting != on else { return }
    var next = state
    next.touchCounting = on
    next.lastUpdatedAt = Self.now()
    set(next)
  }

  /// The Live Activity's pause/resume button (TogglePauseIntent), possibly in a process JS never started.
  public func togglePauseFromLiveActivity() {
    if !state.isActive, let stored = loadStored(), stored.isActive { state = stored }
    guard state.isActive, !state.isGoalComplete else { return }
    setPaused(!state.isPaused)
  }

  /// Ends the session in memory and returns its final state. The stored copy stays until JS has saved the
  /// record to history and calls clearStored, so a crash in between loses nothing.
  func finish() -> ChantState {
    let final = state
    state = ChantState()
    LiveActivityController.shared.end()
    onCoarseChange?(state)
    return final
  }

  /// Resumes a session persisted by a process that died.
  func resume(_ stored: ChantState) {
    set(stored, persist: false)
    LiveActivityController.shared.start(for: state)
  }

  // MARK: Storage

  func loadStored() -> ChantState? { Self.loadStored() }

  /// Thread-safe: UserDefaults can be read from any thread.
  nonisolated static func loadStored() -> ChantState? {
    guard let data = UserDefaults.standard.data(forKey: storageKey) else { return nil }
    return try? JSONDecoder().decode(ChantState.self, from: data)
  }

  /// Clears the stored session only if it is still session `sessionId`.
  func clearStored(sessionId: String) {
    guard let stored = loadStored() else { return }
    if stored.sessionId == sessionId { defaults.removeObject(forKey: Self.storageKey) }
  }

  nonisolated static func json(_ state: ChantState) -> String {
    (try? String(data: JSONEncoder().encode(state), encoding: .utf8)) ?? "{}"
  }

  // MARK: Publishing

  private func set(_ next: ChantState, persist: Bool = true) {
    let previous = state
    state = next
    if persist, next.isActive, let data = try? JSONEncoder().encode(next) {
      defaults.set(data, forKey: Self.storageKey)
    }
    if Self.coarse(previous) != Self.coarse(next) { onCoarseChange?(next) }
    LiveActivityController.shared.update(for: next)
  }

  private static func coarse(_ s: ChantState) -> ChantState {
    var c = s
    c.count = 0
    c.lastUpdatedAt = 0
    return c
  }
}
