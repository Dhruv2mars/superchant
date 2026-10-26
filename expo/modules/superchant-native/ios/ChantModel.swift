import Foundation

/// The session model and counting rules, the Swift twin of data/ChantState.kt and ChantCounter.kt. JSON uses the
/// same field names as the Kotlin app (and the TypeScript core), so the stored active session reads the same way
/// everywhere. Shared test vectors (modules/superchant-native/ios/Tests) check it against the Kotlin rules.
public let roundSize = 108
public let milestoneInterval = 27

public struct MantraGoal: Codable, Equatable {
  public var id: String
  public var name: String
  public var targetRounds: Int
}

public struct Goal: Codable, Equatable {
  public var id: String
  public var name: String
  public var mantras: [MantraGoal]
  public var createdAt: Int64

  public var totalRounds: Int { mantras.reduce(0) { $0 + $1.targetRounds } }
}

public struct ChantState: Codable, Equatable {
  public var count = 0
  public var rounds = 0
  public var isActive = false
  public var isPaused = false
  public var sessionStartTime: Int64 = 0
  public var touchCounting = false
  public var activeGoal: Goal?
  public var currentMantraIndex = 0
  public var mantraRoundsCompleted = 0
  public var sessionId = ""
  public var lastUpdatedAt: Int64 = 0

  public init() {}

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    count = try c.decodeIfPresent(Int.self, forKey: .count) ?? 0
    rounds = try c.decodeIfPresent(Int.self, forKey: .rounds) ?? 0
    isActive = try c.decodeIfPresent(Bool.self, forKey: .isActive) ?? false
    isPaused = try c.decodeIfPresent(Bool.self, forKey: .isPaused) ?? false
    sessionStartTime = try c.decodeIfPresent(Int64.self, forKey: .sessionStartTime) ?? 0
    touchCounting = try c.decodeIfPresent(Bool.self, forKey: .touchCounting) ?? false
    activeGoal = try c.decodeIfPresent(Goal.self, forKey: .activeGoal)
    currentMantraIndex = try c.decodeIfPresent(Int.self, forKey: .currentMantraIndex) ?? 0
    mantraRoundsCompleted = try c.decodeIfPresent(Int.self, forKey: .mantraRoundsCompleted) ?? 0
    sessionId = try c.decodeIfPresent(String.self, forKey: .sessionId) ?? ""
    lastUpdatedAt = try c.decodeIfPresent(Int64.self, forKey: .lastUpdatedAt) ?? 0
  }

  public func encode(to encoder: Encoder) throws {
    var c = encoder.container(keyedBy: CodingKeys.self)
    try c.encode(count, forKey: .count)
    try c.encode(rounds, forKey: .rounds)
    try c.encode(isActive, forKey: .isActive)
    try c.encode(isPaused, forKey: .isPaused)
    try c.encode(sessionStartTime, forKey: .sessionStartTime)
    try c.encode(touchCounting, forKey: .touchCounting)
    if let goal = activeGoal { try c.encode(goal, forKey: .activeGoal) } else { try c.encodeNil(forKey: .activeGoal) }
    try c.encode(currentMantraIndex, forKey: .currentMantraIndex)
    try c.encode(mantraRoundsCompleted, forKey: .mantraRoundsCompleted)
    try c.encode(sessionId, forKey: .sessionId)
    try c.encode(lastUpdatedAt, forKey: .lastUpdatedAt)
  }

  enum CodingKeys: String, CodingKey {
    case count, rounds, isActive, isPaused, sessionStartTime, touchCounting, activeGoal, currentMantraIndex,
      mantraRoundsCompleted, sessionId, lastUpdatedAt
  }

  public var currentRoundCount: Int { count % roundSize }
  public var progress: Double { Double(currentRoundCount) / Double(roundSize) }

  public var currentMantra: MantraGoal? {
    guard let goal = activeGoal, currentMantraIndex >= 0, currentMantraIndex < goal.mantras.count else { return nil }
    return goal.mantras[currentMantraIndex]
  }

  public var isCurrentMantraComplete: Bool {
    guard let mantra = currentMantra else { return false }
    return mantraRoundsCompleted >= mantra.targetRounds
  }

  public var isGoalComplete: Bool {
    guard let goal = activeGoal else { return false }
    return currentMantraIndex >= goal.mantras.count - 1 && isCurrentMantraComplete
  }

  public static func start(goal: Goal?, now: Int64, touchCounting: Bool) -> ChantState {
    var s = ChantState()
    s.isActive = true
    s.sessionStartTime = now
    s.touchCounting = touchCounting
    s.activeGoal = goal
    s.sessionId = UUID().uuidString.lowercased()
    s.lastUpdatedAt = now
    return s
  }
}

public enum CountFeedback: String {
  case count = "COUNT", milestone = "MILESTONE", round = "ROUND", section = "SECTION", goal = "GOAL"
}

public enum ChantCounter {
  /// Adds one count, rolling rounds every 108 beads and advancing goal mantras. Nil when counting isn't allowed.
  public static func increment(_ state: ChantState) -> (state: ChantState, feedback: CountFeedback)? {
    if !state.isActive || state.isPaused || state.isGoalComplete { return nil }
    let newCount = state.count + 1
    let newRounds = newCount / roundSize
    let roundCompleted = newRounds > state.rounds
    var mantraIndex = state.currentMantraIndex
    var mantraRounds = state.mantraRoundsCompleted
    var feedback: CountFeedback = roundCompleted ? .round : (newCount % milestoneInterval == 0 ? .milestone : .count)
    if let goal = state.activeGoal, roundCompleted {
      mantraRounds += 1
      if mantraIndex < goal.mantras.count, mantraRounds >= goal.mantras[mantraIndex].targetRounds {
        if mantraIndex < goal.mantras.count - 1 {
          feedback = .section
          mantraIndex += 1
          mantraRounds = 0
        } else {
          feedback = .goal
        }
      }
    }
    var next = state
    next.count = newCount
    next.rounds = newRounds
    next.currentMantraIndex = mantraIndex
    next.mantraRoundsCompleted = mantraRounds
    return (next, feedback)
  }
}

/// Drops repeat triggers within 150 ms of the last accepted one (a tap reported twice).
public final class CountDebouncer {
  private var lastAcceptedAt: TimeInterval?
  private let minInterval: TimeInterval
  public init(minInterval: TimeInterval = 0.150) { self.minInterval = minInterval }
  public func tryAccept(_ now: TimeInterval) -> Bool {
    if let last = lastAcceptedAt, now - last >= 0, now - last < minInterval { return false }
    lastAcceptedAt = now
    return true
  }
}

/// Sizes for the mala ring (MalaGeometry in CounterDisplay.kt), in points.
public struct MalaGeometry {
  public let ringRadius: Double
  public let strokeWidth: Double
  public let beadRadius: Double
  public let beadOrbit: Double
  public let meruRadius: Double
  public let beadCount: Int
  public let beadPitch: Double
  public let meruClearance: Double

  public func beadAngle(_ index: Int) -> Double { (meruClearance + Double(index) * beadPitch) / beadOrbit - .pi / 2 }

  public static func forRing(outerRadius: Double) -> MalaGeometry {
    let beadRadius = min(max(outerRadius * 0.028, 2.5), 6)
    let meruRadius = beadRadius * 1.7
    let beadOrbit = outerRadius - meruRadius
    let strokeWidth = min(max(outerRadius * 0.05, 5), 12)
    let ringRadius = beadOrbit - meruRadius - strokeWidth / 2 - 4
    let beadArc = 2 * .pi * beadOrbit - 2 * (meruRadius - beadRadius)
    let maxBeads = Int((2 * .pi * beadOrbit) / (beadRadius * 3.2)) - 2
    let beadCount = beadsToShow(maxBeads)
    let beadPitch = beadArc / Double(beadCount + 1)
    return MalaGeometry(
      ringRadius: ringRadius, strokeWidth: strokeWidth, beadRadius: beadRadius, beadOrbit: beadOrbit,
      meruRadius: meruRadius, beadCount: beadCount, beadPitch: beadPitch,
      meruClearance: meruRadius - beadRadius + beadPitch)
  }

  /// One bead per count when all 108 fit; otherwise the most beads that split the round evenly.
  public static func beadsToShow(_ maxBeads: Int) -> Int {
    var n = min(maxBeads, roundSize)
    while n >= 1 {
      if roundSize % n == 0 { return n }
      n -= 1
    }
    return 0
  }
}
