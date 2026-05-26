# CLAUDE.md instructions for x-clicker

This project utilizes the **Superpowers** software development methodology and agentic skills framework to ensure high discipline, quality, planning, and TDD.

## 🛠️ Superpowers Integration

All AI Coding Agents (such as Claude Code, Cursor, and Gemini) **MUST** strictly follow the Superpowers skills found in:
👉 [.superpowers/skills/](file:///Users/xera/GitHub/x-clicker/.superpowers/skills/)

### Core Guidelines:
1. **Brainstorm First**: Before writing any implementation plans or changing code, you must invoke the `brainstorming` skill (`.superpowers/skills/brainstorming/SKILL.md`) to clarify requirements and write a specification.
2. **Write Detailed Plans**: After the brainstorming specification is approved by the user, invoke the `writing-plans` skill (`.superpowers/skills/writing-plans/SKILL.md`) to generate a step-by-step implementation plan. Save the plan under `.superpowers/plans/` or `docs/superpowers/plans/`.
3. **Strict TDD**: Always follow Test-Driven Development (TDD) as defined in `.superpowers/skills/test-driven-development/SKILL.md` (write a failing test -> verify it fails -> implement minimal code -> verify it passes -> refactor).
4. **Verification**: Always verify all changes before completion (`.superpowers/skills/verification-before-completion/SKILL.md`).

## 🏗️ Project Info
- **Tech Stack**: Android Accessibility Service (Kotlin), Jetpack Compose, Material 3, MVVM.
- **Build Command**: `./gradlew assembleDebug`
- **Test Command**: `./gradlew test` (or specific subproject tests)
