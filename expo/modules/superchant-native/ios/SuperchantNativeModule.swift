import ExpoModulesCore
import UIKit
import UniformTypeIdentifiers
import UserNotifications

/// iOS face of the engine: the same session API as Android (getSession, start, setPaused, ...), plus the small
/// native services the TypeScript side needs (storage, reminders, backup files).
public class SuperchantNativeModule: Module {
  private var documentDelegate: DocumentDelegate?

  public func definition() -> ModuleDefinition {
    Name("SuperchantNative")

    Events("onSession")

    OnCreate {
      Task { @MainActor in
        SessionEngine.shared.onCoarseChange = { [weak self] state in
          self?.sendEvent("onSession", ["state": SessionEngine.json(state)])
        }
      }
    }

    // MARK: Session
    // Sync calls run on the JS thread: reads come from a locked snapshot and commands hop to the main thread
    // asynchronously, so JS never blocks on UI work. finish() is async so it returns the exact final state.

    Function("getSession") { () -> String in SessionEngine.json(SessionEngine.snapshot) }

    Function("start") { (goalJson: String?, touchCounting: Bool) in
      let goal = goalJson.flatMap { try? JSONDecoder().decode(Goal.self, from: Data($0.utf8)) }
      Self.onMain { $0.start(goal: goal, touchCounting: touchCounting) }
    }

    AsyncFunction("finish") { () -> String in
      MainActor.assumeIsolated { SessionEngine.json(SessionEngine.shared.finish()) }
    }.runOnQueue(.main)

    Function("setPaused") { (paused: Bool) in Self.onMain { $0.setPaused(paused) } }
    Function("setTouchCounting") { (on: Bool) in Self.onMain { $0.setTouchCounting(on) } }
    Function("countFromScreen") { Self.onMain { _ = $0.countFromScreen() } }

    Function("loadActiveSession") { () -> String? in SessionEngine.loadStored().map(SessionEngine.json) }

    Function("clearActiveSession") { (sessionId: String) in Self.onMain { $0.clearStored(sessionId: sessionId) } }

    Function("resume") { (stateJson: String) in
      guard let state = try? JSONDecoder().decode(ChantState.self, from: Data(stateJson.utf8)) else { return }
      Self.onMain { $0.resume(state) }
    }

    Function("configureFeedback") { (f: [String: Any]) in
      var s = FeedbackSettings()
      s.countVibration = f["countVibration"] as? Bool ?? true
      s.roundVibration = f["roundVibration"] as? Bool ?? true
      s.milestonePulse = f["milestonePulse"] as? Bool ?? true
      s.beadClick = f["beadClick"] as? Bool ?? false
      s.countIntensity = (f["countIntensity"] as? NSNumber)?.intValue ?? 80
      s.roundIntensity = (f["roundIntensity"] as? NSNumber)?.intValue ?? 255
      DispatchQueue.main.async { Feedback.shared.settings = s }
    }

    Function("hasAmplitudeControl") { Feedback.supportsIntensity }

    // MARK: Storage (UserDefaults, written synchronously so a write made before a crash is kept)

    Function("getItem") { (key: String) -> String? in UserDefaults.standard.string(forKey: "superchant.\(key)") }
    Function("setItem") { (key: String, value: String) in UserDefaults.standard.set(value, forKey: "superchant.\(key)") }
    Function("removeItem") { (key: String) in UserDefaults.standard.removeObject(forKey: "superchant.\(key)") }

    // MARK: Reminders (local notifications; see src/native/reminders.ts for which days get one)

    AsyncFunction("notificationStatus") { () async -> String in
      let settings = await UNUserNotificationCenter.current().notificationSettings()
      switch settings.authorizationStatus {
      case .authorized, .provisional, .ephemeral: return "granted"
      case .denied: return "denied"
      default: return "undetermined"
      }
    }

    AsyncFunction("requestNotificationPermission") { () async -> Bool in
      (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    AsyncFunction("scheduleReminders") { (reminders: [[String: Any]]) async in
      let center = UNUserNotificationCenter.current()
      let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("reminder-") }
      center.removePendingNotificationRequests(withIdentifiers: pending)
      for r in reminders {
        guard let id = r["id"] as? String, let ms = (r["epochMs"] as? NSNumber)?.doubleValue else { continue }
        let content = UNMutableNotificationContent()
        content.title = r["title"] as? String ?? ""
        content.body = r["body"] as? String ?? ""
        content.sound = .default
        content.interruptionLevel = .active
        let date = Date(timeIntervalSince1970: ms / 1000)
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        try? await center.add(UNNotificationRequest(identifier: "reminder-\(id)", content: content, trigger: trigger))
      }
    }

    AsyncFunction("cancelReminders") { () async in
      let center = UNUserNotificationCenter.current()
      let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("reminder-") }
      center.removePendingNotificationRequests(withIdentifiers: pending)
    }

    // MARK: Backup files (the Files app, like Android's storage picker)

    AsyncFunction("exportFile") { (fileName: String, text: String, promise: Promise) in
      let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
      do {
        try text.write(to: url, atomically: true, encoding: .utf8)
      } catch {
        promise.reject("ERR_BACKUP_FILE", error.localizedDescription)
        return
      }
      let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
      self.present(picker) { picked in promise.resolve(picked != nil) }
    }.runOnQueue(.main)

    AsyncFunction("importFile") { (promise: Promise) in
      let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data], asCopy: true)
      self.present(picker) { picked in
        guard let url = picked else { return promise.resolve(nil) }
        // Backups are small; refuse anything absurd rather than running out of memory
        if let size = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize, size > 20 * 1024 * 1024 {
          return promise.reject("ERR_BACKUP_FORMAT", "Too large")
        }
        promise.resolve(try? String(contentsOf: url, encoding: .utf8))
      }
    }.runOnQueue(.main)

    View(CounterView.self)
  }

  private static func onMain(_ body: @escaping @MainActor (SessionEngine) -> Void) {
    DispatchQueue.main.async { MainActor.assumeIsolated { body(SessionEngine.shared) } }
  }

  private func present(_ picker: UIDocumentPickerViewController, done: @escaping (URL?) -> Void) {
    let delegate = DocumentDelegate { [weak self] url in
      self?.documentDelegate = nil
      done(url)
    }
    documentDelegate = delegate
    picker.delegate = delegate
    guard let root = appContext?.utilities?.currentViewController() else { return done(nil) }
    root.present(picker, animated: true)
  }
}

private final class DocumentDelegate: NSObject, UIDocumentPickerDelegate {
  let done: (URL?) -> Void
  init(done: @escaping (URL?) -> Void) { self.done = done }
  func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { done(urls.first) }
  func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { done(nil) }
}
