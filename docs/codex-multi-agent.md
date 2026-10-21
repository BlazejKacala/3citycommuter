# Codex multi-agent workflow

This project includes local Codex CLI settings in `.codex/config.toml` and six project-scoped roles under `.codex/agents/`. Codex loads project configuration only after the project is trusted. The settings apply to future local Codex CLI sessions; ChatGPT Work does not read local project configuration.

The workflow caps spawned agents at three concurrent threads. The `supervisor` role uses GPT-6 Astra. `api_analyst`, `data_worker`, `viewmodel_worker`, and `test_worker` use GPT-6 Luna for focused work. The independent `reviewer` uses GPT-6.1 Sol at high reasoning effort. Explicit spawn settings take precedence over configured role defaults. The root `model` and `model_reasoning_effort` settings select Astra/high for future CLI sessions. They do not switch the model of an already running session.

## Assigning work

Ask Codex to delegate only when the work has independent, bounded parts. The supervisor should inspect the worktree first, account for existing user changes, assign exclusive file ownership to writing workers, and keep no more than three spawned threads open at a time. Use the API analyst and reviewer for read-only investigation. Coordinate shared interface decisions before implementation.

Workers must follow `AGENTS.md`, preserve all pre-existing dirty and untracked files, and report changed paths, checks run and outcomes, and unresolved risks. Workers must not make public API, DTO, persistence, provider, or cross-layer contract changes without explicit direction from the supervisor.

Role instructions guide behavior; they do not create file-level write permissions. Subagents inherit the primary session's sandbox and approval settings, so the primary session still controls the actual permission boundary.

Example prompt:

> Use the project agents to investigate this issue. Have `api_analyst` trace the provider behavior, then assign implementation to one worker with an exclusive file list. Keep the existing worktree changes intact, keep contract decisions with the supervisor, and have `reviewer` inspect the final diff. Use at most three agents at once and report changed files, checks, and risks.

## Configuration checks

The installed Codex CLI is `codex-cli 0.163.0`. On 2026-10-20, `codex --strict-config doctor --json` with an invocation-only trusted-project override reported `config.load: ok`, no unknown configuration keys, and `model: gpt-6-astra`.

To prove that the project file was actually read, a temporary unknown key was inserted into `.codex/config.toml`. Doctor reported that exact key and project path as a startup warning. The original file was restored immediately, then validated again successfully. No user-wide configuration or trust settings were modified. Local diagnostic logs are in ignored `build/codex-config-validation.json` and `build/codex-invalid-config.log`.

```powershell
codex --strict-config doctor --json
codex debug models --bundled
```

The bundled CLI model catalog exposed `gpt-6-astra`, `gpt-6-luna`, and `gpt-6.1-sol`. The six role files use the documented custom-agent schema. Doctor verifies configuration loading, not completion of a real task by every configured role; account access and usage limits still apply.

During this implementation the collaboration runtime launched Luna workers and an Astra reviewer using explicit model overrides and at most three concurrent subagents. Roles were assigned in stages: API analysis, data integration, ViewModel integration, test writing, and independent review. The root integrated their output and ran Gradle centrally. The runtime subsequently returned a usage-limit error for workers/reviewer; the root completed the remaining fixes and checks. This runtime delegation is separate from a future CLI session loading the files in `.codex/`.

## References

- [Codex configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Codex subagents and custom agents](https://learn.chatgpt.com/docs/agent-configuration/subagents)
