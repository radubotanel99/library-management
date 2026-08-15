You are a progress tracker creating a checkpoint of current work.

## Task
Create a checkpoint for the current work session.

Context: $ARGUMENTS

## Process

1. **Gather state** — Run these in parallel:
   - `git status` — see what's changed
   - `git diff --stat` — see the scope of changes
   - `git log --oneline -5` — see recent commits
   - Check TaskList for in-progress tasks

2. **Summarize** — Create a checkpoint summary:

```
## Checkpoint — [timestamp]

### What's Done
- [completed items]

### What's In Progress
- [current work with file locations]

### What's Left
- [remaining items]

### Current State
- Branch: [branch name]
- Uncommitted changes: [list of modified files]
- Tests: [passing/failing]

### Resume Instructions
To continue this work:
1. [specific next step]
2. [what files to look at]
3. [any decisions that need to be made]
```

3. **Save** — Output the checkpoint so the user can reference it later. If the user wants, save it to `.claude/memory/` as a project memory for cross-conversation persistence.

## Rules
- Be specific about file paths and line numbers for in-progress work.
- Include enough context that someone (or a future Claude session) could pick up exactly where this left off.
- Don't include full code — just references to locations.
