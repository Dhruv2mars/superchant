import AVFoundation
import AudioToolbox
import CoreHaptics
import UIKit

/// Counting feel, set from JS whenever the settings change (CountingFeel and VibrationSettings in Kotlin).
public struct FeedbackSettings {
  public var countVibration = true
  public var roundVibration = true
  public var milestonePulse = true
  public var beadClick = false
  /// 1-255, as on Android.
  public var countIntensity = 80
  public var roundIntensity = 255
}

/// Haptics and sounds for each count, the iOS twin of ChantingService.giveFeedback: a light tick per bead, a
/// fuller pulse every 27, one pulse per completed round (up to 10), and a celebration when a goal completes.
final class Feedback {
  static let shared = Feedback()

  var settings = FeedbackSettings()
  private var engine: CHHapticEngine?
  private let fallback = UIImpactFeedbackGenerator(style: .light)
  private lazy var click = BeadClick()

  private init() {
    guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
    engine = try? CHHapticEngine()
    engine?.isAutoShutdownEnabled = true
    engine?.resetHandler = { [weak self] in try? self?.engine?.start() }
    try? engine?.start()
  }

  static var supportsIntensity: Bool { CHHapticEngine.capabilitiesForHardware().supportsHaptics }

  func play(_ feedback: CountFeedback, rounds: Int) {
    if settings.beadClick { click.play() }
    switch feedback {
    case .goal:
      AudioServicesPlaySystemSound(1025)
      if settings.roundVibration { celebrate() }
    case .section:
      AudioServicesPlaySystemSound(1007)
      if settings.roundVibration { roundPulses(1) }
    case .round:
      if settings.roundVibration { roundPulses(rounds) }
    case .milestone:
      guard settings.countVibration else { return }
      if settings.milestonePulse {
        // Longer and fuller than a count tick, gentler than a round
        continuous(at: [0], duration: 0.09, intensity: Float(min(max(settings.countIntensity * 3 / 2, 100), 255)) / 255)
      } else {
        tick()
      }
    case .count:
      if settings.countVibration { tick() }
    }
  }

  /// A short tap at the count intensity (30 ms on Android).
  private func tick() {
    let intensity = Float(settings.countIntensity) / 255
    guard let engine else {
      fallback.impactOccurred(intensity: CGFloat(intensity))
      return
    }
    let event = CHHapticEvent(
      eventType: .hapticTransient,
      parameters: [
        CHHapticEventParameter(parameterID: .hapticIntensity, value: intensity),
        CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.45),
      ],
      relativeTime: 0)
    playPattern([event], on: engine)
  }

  /// One 150 ms pulse per completed round (capped at 10), 100 ms apart.
  private func roundPulses(_ rounds: Int) {
    let pulses = min(max(rounds, 1), 10)
    continuous(at: (0..<pulses).map { Double($0) * 0.25 }, duration: 0.15, intensity: Float(settings.roundIntensity) / 255)
  }

  /// Long-short-short-long.
  private func celebrate() {
    guard let engine else {
      UINotificationFeedbackGenerator().notificationOccurred(.success)
      return
    }
    let parts: [(Double, Double, Float)] = [(0, 0.3, 1), (0.4, 0.15, 0.78), (0.65, 0.15, 0.78), (0.9, 0.3, 1)]
    playPattern(parts.map { event(at: $0.0, duration: $0.1, intensity: $0.2) }, on: engine)
  }

  private func continuous(at times: [Double], duration: Double, intensity: Float) {
    guard let engine else {
      fallback.impactOccurred(intensity: CGFloat(intensity))
      return
    }
    playPattern(times.map { event(at: $0, duration: duration, intensity: intensity) }, on: engine)
  }

  private func event(at time: Double, duration: Double, intensity: Float) -> CHHapticEvent {
    CHHapticEvent(
      eventType: .hapticContinuous,
      parameters: [
        CHHapticEventParameter(parameterID: .hapticIntensity, value: intensity),
        CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.3),
      ],
      relativeTime: time, duration: duration)
  }

  private func playPattern(_ events: [CHHapticEvent], on engine: CHHapticEngine) {
    do {
      let player = try engine.makePlayer(with: CHHapticPattern(events: events, parameters: []))
      try engine.start()
      try player.start(atTime: CHHapticTimeImmediate)
    } catch {
      fallback.impactOccurred()
    }
  }
}

/// A soft wooden "tock" synthesized once (BeadClick.kt), played through an ambient audio session so it follows the
/// ringer switch and mixes with other audio.
final class BeadClick {
  private let engine = AVAudioEngine()
  private let player = AVAudioPlayerNode()
  private var buffer: AVAudioPCMBuffer?

  init() {
    let sampleRate = 44_100.0
    guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return }
    let samples = BeadClick.synthesize(sampleRate: sampleRate)
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)) else { return }
    buffer.frameLength = AVAudioFrameCount(samples.count)
    for (i, s) in samples.enumerated() { buffer.floatChannelData![0][i] = s }
    self.buffer = buffer
    engine.attach(player)
    engine.connect(player, to: engine.mainMixerNode, format: format)
  }

  func play() {
    guard let buffer else { return }
    do {
      try AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
      if !engine.isRunning { try engine.start() }
      player.scheduleBuffer(buffer, at: nil, options: .interrupts)
      if !player.isPlaying { player.play() }
    } catch {}
  }

  /// Two damped partials (a hollow wood body) plus a short noise transient (the bead strike), 45 ms.
  static func synthesize(sampleRate: Double, durationMs: Double = 45) -> [Float] {
    let n = Int(sampleRate * durationMs / 1000)
    var generator = SeededRandom(seed: 108)
    return (0..<n).map { i in
      let t = Double(i) / sampleRate
      let body = 0.65 * sin(2 * .pi * 1250 * t) * exp(-t / 0.008) + 0.35 * sin(2 * .pi * 2900 * t) * exp(-t / 0.004)
      let strike = t < 0.0015 ? (generator.next() * 2 - 1) * 0.5 * (1 - t / 0.0015) : 0
      let attack = t < 0.0004 ? 0.5 - 0.5 * cos(.pi * t / 0.0004) : 1
      return Float(max(min((body + strike) * attack * 0.32, 1), -1))
    }
  }
}

private struct SeededRandom {
  private var state: UInt64
  init(seed: UInt64) { state = seed &* 6_364_136_223_846_793_005 &+ 1 }
  mutating func next() -> Double {
    state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
    return Double(state >> 11) / Double(1 << 53)
  }
}
