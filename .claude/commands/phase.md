You are orchestrating a full build pipeline for one phase of the Library Management System, chaining the project's existing subagents in order: researcher → architect → **[stop for approval]** → implementer → test-runner (+ fixer on failure) → security-auditor → reviewer → report.

## Task
Build phase: $ARGUMENTS (a number like `1` or a name like `Schema`)

## Process

### 0. Resolve the phase
Read `docs/BUILD_PLAN.md` and find the phase matching `$ARGUMENTS`. If it's ambiguous or not found, stop and ask which phase is meant. Note its **Goal** and **Done when** criteria — everything below is judged against those.

### 1. Research — `researcher` agent
Invoke `researcher` to map what already exists that's relevant to this phase: current entities, migrations, controllers, services, and Angular files touching this area; existing tests; conventions already established elsewhere in the codebase. Read-only. Return structured findings.

### 2. Design — `architect` agent
Feed the architect agent: the researcher's findings, this phase's Goal/scope from `BUILD_PLAN.md`, and the relevant sections of `DATA_MODEL.md` / `API_CONTRACT.md` / `FUNCTIONAL_SPEC.md`. It must produce a numbered implementation plan (files touched, complexity, dependencies between steps) and explicitly list any open questions or risks. Create a tracked task per step with `TaskCreate`.

If the plan requires something absent from the docs, that's an open question — do not let the architect guess (BUILD_PLAN.md ground rule: "No feature invention").

### 3. GATE — stop and wait for Radu
**Do not proceed past this point without Radu's explicit approval of the plan.** Present the plan in full, including open questions. This gate exists because `CLAUDE.md`'s working rule is that Claude Code doesn't write feature code unprompted — his approval here *is* the prompt. If there are open questions, they must be resolved (by Radu, in conversation) before the gate can pass. Do not soften this into "let me know if you want changes, otherwise I'll continue" — wait.

### 4. Implement — `implementer` agent
Once approved, invoke `implementer` per step of the approved plan, updating each task with `TaskUpdate` as it completes. Enforce the BUILD_PLAN.md ground rules while doing so: backend and frontend for a feature ship in the same pass; if an endpoint needs to change, `API_CONTRACT.md` is edited first; migrations are immutable once committed (`V{n}__…`, never edit an applied file).

### 5. Test — `test-runner` agent
Run the checks that prove this phase's specific **Done when** line in `BUILD_PLAN.md` (not just "tests pass" generically — e.g. Phase 1 means the app starts with `ddl-auto=validate`; Phase 5 means both lending safeguards produce translated errors).

- If something fails and looks like a small, obvious bug (under ~20 lines), invoke `fixer` once to correct it, then re-run test-runner.
- If it still fails after that one retry, **stop and report to Radu** with the specific failure — don't keep looping.

### 6. Security — `security-auditor` agent
Run against the files touched this phase. Any **CRITICAL** or **HIGH** finding is a hard stop: report it to Radu before continuing to step 7. MEDIUM/LOW findings are carried into the final report but don't block.

### 7. Review — `reviewer` agent
Full review of the diff (`git diff`, full file reads, not just the diff). If the verdict is `REQUEST_CHANGES` on any **Critical** item, stop and report to Radu rather than silently sending it back to `implementer` — reviewer is read-only by design so a human decides what happens with its findings.

### 8. Report
One consolidated summary back to Radu:

```
## Phase [N] — [name] — Pipeline Report

**Done when (from BUILD_PLAN.md)**: [criterion] — MET / NOT MET

### Built
- [files created/modified, one line each]

### Tests
- [status, what was run]

### Security
- [CRITICAL/HIGH findings if any, else "clean"]

### Review
- Verdict: [APPROVE / REQUEST_CHANGES / NEEDS_DISCUSSION]
- Critical items: [if any]

### Needs Radu's attention
- [anything unresolved — be specific: file, line, question. "Nothing" if genuinely clean.]

### Next
Phase [N+1] — [name], per BUILD_PLAN.md
```

## Rules
- Never skip the Gate in step 3. Never skip a stop condition in steps 5–7 by "fixing it and moving on" — those stops exist specifically so Radu stays the one deciding, per his working rule in `CLAUDE.md`.
- When you do stop, name the exact thing that needs a decision — not "something needs review," but the file, line, and question.
- One phase per invocation. Do not cascade automatically into the next phase, even if this one reports clean — Radu decides when to run `/phase` again.
- Follow every convention in `CLAUDE.md` throughout (DTOs only across the controller boundary, constructor injection, `FetchType.LAZY`, enums as strings, package-by-feature, etc.) — the agents already know these, but the orchestration itself shouldn't introduce anything that contradicts them.
