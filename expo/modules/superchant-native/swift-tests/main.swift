// Replays the Kotlin-generated counter vectors (src/core/__fixtures__/golden.json) against the Swift engine's
// model (ios/ChantModel.swift). Run: bun run test:swift
import Foundation

struct Fixture: Decodable {
  let name: String
  let kind: String
  let input: String?
  let presses: Int?
  let accepted: Int?
  let output: String?
  let events: [String]?
}

let path = CommandLine.arguments.dropFirst().first ?? "src/core/__fixtures__/golden.json"
let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: URL(fileURLWithPath: path)))
var failures = 0
var checked = 0

for f in fixtures where f.kind == "counter" {
  var state = try JSONDecoder().decode(ChantState.self, from: Data(f.input!.utf8))
  var events: [String] = []
  var accepted = 0
  for i in 1...f.presses! {
    guard let r = ChantCounter.increment(state) else { continue }
    state = r.state
    accepted += 1
    if r.feedback != .count { events.append("\(i):\(r.feedback.rawValue)") }
  }
  let expected = try JSONDecoder().decode(ChantState.self, from: Data(f.output!.utf8))
  if state != expected || accepted != f.accepted! || events != f.events! {
    failures += 1
    print("FAIL \(f.name): accepted \(accepted) vs \(f.accepted!), events match \(events == f.events!), state match \(state == expected)")
  }
  checked += 1
}

// The stored active session: Kotlin's JSON decodes, and Swift's encoding decodes back to the same state.
for f in fixtures where f.kind == "activeSession" && f.output != nil {
  let state = try JSONDecoder().decode(ChantState.self, from: Data(f.output!.utf8))
  let again = try JSONDecoder().decode(ChantState.self, from: JSONEncoder().encode(state))
  if state != again { failures += 1; print("FAIL round trip \(f.name)") }
  checked += 1
}

// Mala geometry (MalaGeometryTest.kt)
precondition(MalaGeometry.beadsToShow(120) == 108 && MalaGeometry.beadsToShow(60) == 54 && MalaGeometry.beadsToShow(40) == 36)
precondition(MalaGeometry.beadsToShow(30) == 27 && MalaGeometry.beadsToShow(0) == 0)
let small = MalaGeometry.forRing(outerRadius: 300 / 2.625)
precondition(small.beadCount >= 1 && 108 % small.beadCount == 0)

print(failures == 0 ? "swift vectors: \(checked) passed" : "swift vectors: \(failures) of \(checked) failed")
exit(failures == 0 ? 0 : 1)
