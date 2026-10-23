# deepwater-developer: how to use it

`deepwater-developer` is a plugin for Claude Code. After you install it, you type one command with
an issue number, and Claude builds that issue the way our team wants: it reads the issue, looks at
the existing code, writes a feature plan for you to approve, implements the smallest working
slice, runs the tests, and reports what is done. You stay in control: it never commits or pushes.

This page is the whole manual. Read it top to bottom once; later you only need the "Daily use"
part.

---

## 1. What you need (once)

| You need | How to get it | How to check |
| --- | --- | --- |
| Claude Code | Install the Claude Code CLI from <https://claude.com/claude-code> and sign in by running `claude` once in a terminal | `claude --version` prints a version |
| IntelliJ plugin (optional, recommended) | In IntelliJ: Settings → Plugins → Marketplace → search "Claude Code" → Install. It opens Claude Code in a tool window inside IntelliJ | A "Claude Code" tab appears in the IDE |
| GitHub CLI | Install `gh` from <https://cli.github.com> and run `gh auth login` | `gh issue list` shows issues of the repository |
| The project's toolchain | For Deepwater: JDK 25 and Node 22 (the Gradle wrapper is in the repo) | `./gradlew --version` works |

If you skip the GitHub CLI, everything still works; you paste the issue text instead of the number.

## 2. Install the plugin (once per computer)

1. Make sure your clone of the repository contains the folder `deepwater-developer/` (it is on
   `main` after this merge; otherwise check out the branch that has it).
2. Open the project in IntelliJ and open the Claude Code tab (or open a terminal in the project
   folder and run `claude`).
3. Type these two lines, one after the other, replacing the path with the real one on your disk:

   ```text
   /plugin marketplace add C:\path\to\deepwater\deepwater-developer
   /plugin install deepwater-developer@deepwater
   ```

   On macOS or Linux the path looks like `/Users/you/code/deepwater/deepwater-developer`.
   When asked for a scope, pick **user** (works in every project on this computer).

4. Check: type `/deepwater-` and the three commands `feature`, `spec` and `dod` are offered.

That is the whole installation. If someone changes the plugin later, run
`/plugin marketplace update deepwater` and then `/plugin install deepwater-developer@deepwater`
again.

## 3. Daily use

### Build an issue

```text
/deepwater-developer:feature 42
```

Replace `42` with the GitHub issue number. You can also write a sentence instead of a number:
`/deepwater-developer:feature "accept several correct answers for typed exercises"`.

Here is what happens, step by step, and what you do at each step:

**Step A, Clarify.** Claude reads the issue and its comments and writes down the problem, the
expected outcome, who uses it, what is in and out of scope, which interface is needed (GraphQL,
REST, MCP...), and what it could not find out.
*You:* answer the questions it could not resolve from the issue. If there are none, do nothing.

**Step B, Inspect.** Claude reads the project documentation and the code the issue touches (services,
GraphQL schema, tests, CI) and says for each part whether it will reuse, extend, wrap, refactor,
replace or add it.
*You:* nothing, unless it misunderstood something.

**Step C, Plan.** Claude writes the feature plan to `docs/specs/42-<name>.md`. The plan has 18
short sections: metadata, business outcome, actors and interfaces, what exists today, business rules
and contracts, acceptance scenarios (Given/When/Then), design, interface contracts, whether AI is
involved, implementation steps, KISS/YAGNI/DRY check, security, observability, test plan, ADR and
rollback, risks and open questions, definition of done, review record. Sections that do not apply
say "not applicable".
*You:* **this is the moment to read and decide.** Open the plan in IntelliJ, answer the open
questions, change anything you disagree with (edit the file or just tell Claude), then say
"continue". If a question blocks the work, Claude stops here and waits.

**Step D, Implement.** Claude writes the code in this order: domain rule → use case → fake port
→ tests → the one adapter the issue needs (for example a GraphQL mutation) → contract test → real
adapter. Business logic goes into the use case, never into the controller or resolver.
*You:* nothing.

**Step E, Verify.** Claude compiles, runs the focused test class, then the whole backend test
suite (`./gradlew :backend:test`), and shows you the real output. If something cannot run on your
machine it says so instead of pretending.
*You:* read the output.

**Step F, Report.** Claude ticks the definition-of-done list in the plan, and summarises: files
changed, test results, what is done, what is not, remaining questions.
*You:* review the diff in IntelliJ (Git → Local Changes). If you are happy, commit and push
yourself, or say "commit and push". Open the pull request and link the plan file in its description.

### Only write the plan (no code)

```text
/deepwater-developer:spec 42
```

Steps A to C only. Use it when you want to agree on scope first, or to turn a vague issue into a
testable one. Ends with the open questions and a yes/no on whether the issue is ready to implement.

### Check a branch before a pull request

```text
/deepwater-developer:dod 42
```

Compares the branch you are on with the plan and the definition of done, runs the tests, and lists
what is met, what is not, and what to fix. It changes nothing.

### Let it run on its own (advanced)

```text
@agent-deepwater-developer:spec-driven-feature implement issue 42
```

Same method, but as a background agent that does not ask you questions in the middle; it stops at
the plan if a question blocks it, and reports at the end.

## 4. Example of a session

```text
you:    /deepwater-developer:feature 42

claude: Step A. Issue #42 "Teachers can archive a lesson". Outcome: an archived lesson is hidden
        from learners but kept for the teacher. Interface: GraphQL (the frontend already uses it).
        Open question: should learners who started the lesson still see it? (asking you)

you:    Yes, learners who already started keep access.

claude: Step B. Found: EchoService.publish(), the Lesson table, schema.graphqls, MvpEndToEndTest.
        Decisions: EXTEND EchoService, EXTEND schema, NEW archiveLesson use case, NEW test.
        Step C. Plan written to docs/specs/42-archive-lesson.md. 16 sections filled, 2 not
        applicable (MCP, AG-UI). No blocking questions. Continue?

you:    continue

claude: Step D. ... (files it creates and changes)
        Step E. ./gradlew :backend:test --tests ArchiveLessonTest  → BUILD SUCCESSFUL, 3 tests
                ./gradlew :backend:test                            → BUILD SUCCESSFUL, 31 tests
        Step F. Done: 11 of 13 definition-of-done lines. Not done: documentation (README) and
                the pull request link. Files changed: ... Nothing committed.
```

The names and numbers above are made up to show the shape of a session; your output will differ.

## 5. Rules the plugin follows

- It never commits, pushes, opens pull requests or merges.
- It never invents requirements; unknowns become questions in the plan.
- It keeps business logic in use cases and domain code, not in GraphQL resolvers, REST
  controllers, MCP tools or UI code.
- It does not add frameworks, services, Kafka, CQRS or AI calls unless the issue needs them now.
- It shows real test output; if a check could not run, it says so.
- It never puts secrets, tokens, personal data or production data into code, tests or docs.

## 6. Problems and fixes

| Problem | Fix |
| --- | --- |
| `/deepwater-developer:...` is not offered | Type `/plugin`, check the plugin is installed and enabled; look at the Errors tab |
| "gh: command not found" or a login error at step A | Install `gh` and run `gh auth login`, or paste the issue text into the command instead of the number |
| Step E says a command cannot run | JDK 25 or Node 22 is missing; install it, or run the command yourself and paste the result |
| The plan went to the wrong folder | Edit `deepwater-developer/deepwater/repository-mapping.md` (where specs live) |
| You pulled a newer plugin but see old behaviour | `/plugin marketplace update deepwater`, reinstall, or restart Claude Code |
| You want to remove it | `/plugin uninstall deepwater-developer@deepwater` |

## 7. What is inside the folder (for maintainers)

```text
deepwater-developer/
├── README.md                     this manual
├── .claude-plugin/               plugin manifest, marketplace manifest, icon
├── agents/spec-driven-feature.md the background agent
├── skills/                       COMMON method, no project names inside
│   ├── feature/  spec/  dod/     the three commands
│   └── spec-driven-delivery/     the method itself + references:
│       └── references/           architecture-overview.md, feature-plan-template.md,
│                                 adr-template.md, definition-of-done.md
└── deepwater/                    PROJECT-SPECIFIC settings (review these separately)
    ├── repository-mapping.md     where specs/ADRs/contracts live, test commands, current state
    ├── specs-convention.md       naming of docs/specs files
    └── adr-0001-...md            why the plugin exists in this form
```

To use the plugin in another Java/Spring project, copy the folder, rewrite `deepwater/` for that
project, and install it the same way. To change how it works, edit the files under `skills/`, then
run `claude plugin validate ./deepwater-developer`.
