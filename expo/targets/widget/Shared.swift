import ActivityKit
import AppIntents
import Foundation

// Copies of the types in modules/superchant-native/ios/LiveActivity.swift. ActivityKit and App Intents match them
// by name; keep the fields identical.

public struct ChantActivityAttributes: ActivityAttributes {
  public struct ContentState: Codable, Hashable {
    public var roundCount: Int
    public var rounds: Int
    public var paused: Bool
    public var goalComplete: Bool
    public var mantra: String?
    public var mantraRounds: Int
    public var mantraTarget: Int
  }

  public var goalName: String?
  public var startedAt: Date
}

/// The pause/resume button. As a LiveActivityIntent it runs in the app's process; this copy only lets the
/// widget refer to it, and its perform() is never called here.
public struct TogglePauseIntent: LiveActivityIntent {
  public static var title: LocalizedStringResource = "Pause or resume counting"
  public static var isDiscoverable = false

  public init() {}

  public func perform() async throws -> some IntentResult { .result() }
}
