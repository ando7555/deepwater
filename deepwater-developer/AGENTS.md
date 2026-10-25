# deepwater-developer for any AI coding assistant

This folder is a Claude Code plugin, but almost everything in it is plain Markdown in the open
**Agent Skills** format (a folder with a `SKILL.md` file and optional references), which many
assistants read: OpenAI Codex, OpenCode, Cursor, GitHub Copilot and others. This file tells any
assistant how to use it when it is not running as a Claude Code plugin.

## What is here

| Skill folder | What it is | Start it by saying |
| --- | --- | --- |
| `skills/course/` | A mentor-led course. First course: **Become an AI engineer** (`skills/course/courses/ai-engineer/`) | "Run the deepwater-developer course for me" |
| `skills/mentor/` | How to talk to the person: a patient senior colleague who teaches | (used by all the others) |
| `skills/help/` | Plain-language help: what to type next, what an error means. Beginner page: `START-HERE.md` | "Help me, I'm new to this" |
| `skills/ai-provider-integration/` | Provider-independent AI in an application: one port, adapters for Claude, OpenAI and OpenAI-compatible services, a compiled Java reference | "Add a model call to this project without tying it to one vendor" |
| `skills/spec-driven-delivery/` | The delivery method: ticket → specification → smallest slice → tests → definition of done | (used by the commands below) |
| `skills/init/`, `skills/spec/`, `skills/feature/`, `skills/dod/` | The four delivery commands | "Follow skills/feature/SKILL.md for ticket 42" |

## Rules for an assistant that is not Claude Code

- `${CLAUDE_PLUGIN_ROOT}` in the files means **this folder**; `${CLAUDE_SKILL_DIR}` means the
  folder of the skill being read; `$ARGUMENTS` means what the user asked for.
- A `skills:` list in a skill's header means "read those skills too before you start".
- Everything else (read files, run the project's tests, write the specification, never commit
  without being asked, never ask for secrets in the chat) applies unchanged.

## Installing it in other assistants

Each assistant has its own folder for skills, and these change between versions, so check the
assistant's documentation for the current path. The approach is always the same: copy or link
the folders under `skills/` into the assistant's skills folder, or point the assistant at this
`AGENTS.md`.

- **Claude Code:** install as a plugin (see `README.md`). Commands appear as
  `/deepwater-developer:help`, `:course`, `:init`, `:spec`, `:feature`, `:dod`.
- **Cursor:** reads project skills from `.cursor/skills/` and `.agents/skills/`, and for
  compatibility also `.claude/skills/` (Cursor documentation, "Agent Skills", checked 2026-10-25).
- **OpenAI Codex, OpenCode and others** that support Agent Skills: copy the `skills/` folders into
  the skills location their documentation names. Assistants that only read an `AGENTS.md` file can
  be told: "Read deepwater-developer/AGENTS.md and follow it."

The course and the method do not depend on which model the assistant uses. What a lab calls from
the learner's own code is a separate choice, covered by `skills/ai-provider-integration/`.
