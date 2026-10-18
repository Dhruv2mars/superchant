# CLAUDE.md

@AGENTS.md

## Notes for Claude Code

- `AGENTS.md` (imported above) is the single source of truth, shared with Codex. Put project knowledge there,
  not here; this file only holds Claude-specific notes.
- The project skills in `.claude/skills/` are symlinks to `.agents/skills/`. Use them as the "Design and motion
  skills" table says: `emil-design-eng`/`animate` for any animation or interaction, `frontend-design` for screen
  design, `review-animations` when reviewing motion diffs.
- For broad audits, split the search across subagents and verify every finding against the code (and the emulator
  where possible) before changing anything.
