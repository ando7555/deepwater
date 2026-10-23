# deepwater-developer: the manual

This is for anyone who has to turn a GitHub issue into working code in this repository and would
like the careful parts done properly: reading what already exists, writing the plan down, writing
the tests, running them, and telling the truth about the result. The plugin makes Claude Code work
that way. You stay the developer. You answer its questions, you read the plan, you decide when the
code is good enough, and you are the one who commits.

If you have never used Claude Code, that is fine. This manual assumes nothing. The first read
takes about ten minutes. After that, part 3 is the only part you will come back to.

---

## 1. Before you start: a quick check of your computer

Three tools need to be in place, and you only check this once. Open a terminal (in IntelliJ, the
**Terminal** tab at the bottom of the window), type each command, and compare what comes back
with the middle column.

| Type this | You should see | If not |
| --- | --- | --- |
| `claude --version` | a version number, like `2.1.308 (Claude Code)` | Install Claude Code from <https://claude.com/claude-code>, then type `claude` once and sign in with your Anthropic account |
| `gh auth status` | `Logged in to github.com account ...` | Install the GitHub CLI from <https://cli.github.com>, then type `gh auth login` and follow the prompts (GitHub.com, HTTPS, login with a web browser) |
| `java -version` | `openjdk version "25...` | Install JDK 25 (IntelliJ: File → Project Structure → SDKs → + → Download JDK) |

Why these three: Claude Code is what runs the plugin, the GitHub CLI is how it reads the issue
without you copying anything, and the JDK is what it needs to compile and run the tests on your
machine. If the GitHub CLI is a nuisance to set up, skip it. You can paste the issue text instead
of its number, and everything else works the same.

## 2. Installing the plugin

The plugin is just a folder inside this repository. Installing it means telling Claude Code where
that folder is, and then asking it to switch the plugin on. You do this once per computer. It takes
about two minutes, and the only step people get wrong is the path, so we let IntelliJ copy it.

**Step 1. Open the project.** In IntelliJ, open the `deepwater` project, the folder that contains
`backend`, `frontend` and `deepwater-developer`. If you do not see a `deepwater-developer` folder
in the Project panel on the left, you have an older copy: pull the latest `main` first
(Git → Pull), and it appears.

**Step 2. Open Claude Code.** On the right-hand side bar of IntelliJ, click the **Claude Code**
icon. If it is not there, use View → Tool Windows → Claude Code. If that entry is missing too, the
IntelliJ plugin is not installed yet: Settings → Plugins → Marketplace, search for "Claude Code",
install, restart IntelliJ. A panel opens with a text box at the bottom. Everything in this manual
is typed into that text box and sent with Enter. If you would rather work in a terminal, open one
in the project folder and type `claude`. You get the same text box, and nothing else changes.

**Step 3. Copy the folder path.** In the Project panel, right-click the `deepwater-developer`
folder → **Copy Path/Reference...** → **Absolute Path**. The full path is now in your clipboard,
something like `C:\Users\anna\IdeaProjects\deepwater\deepwater-developer`. Copying it this way
means no typos and no guessing about slashes.

**Step 4. Tell Claude Code where the folder is.** In the Claude Code text box, type
`/plugin marketplace add ` (with a space after `add`), paste the path, and press Enter:

```text
/plugin marketplace add C:\Users\anna\IdeaProjects\deepwater\deepwater-developer
```

You should see a confirmation line like `Successfully added marketplace: deepwater`. If you see
"not found" or "does not exist" instead, the path did not come through cleanly. Redo step 3 and
try again. Nothing has been changed on your machine yet.

**Step 5. Switch it on.** Type this and press Enter:

```text
/plugin install deepwater-developer@deepwater
```

Claude Code shows what the plugin contains and asks where to install it. Choose **User**. That
means "for me, in every project on this computer", which is what you want. (Project scope would
record the choice in the repository's shared settings, and that is a team decision, not an
install step.) You should see `Successfully installed plugin: deepwater-developer@deepwater`.

**Step 6. Make sure it worked.** Type `/deepwater-` in the text box and stop there. Claude Code
completes commands as you type, so a small list should appear with three entries:
`/deepwater-developer:feature`, `/deepwater-developer:spec` and `/deepwater-developer:dod`. That
list is your proof. If it stays empty, nothing is broken for good: type `/plugin`, look at the
**Errors** tab, and see part 6 for the two usual causes.

## 3. Using it: from an issue to reviewed code

Here is the honest shape of the work. You give the plugin an issue. It goes through six steps, A
to F, and at two of them it genuinely needs you: at the start, if the issue is unclear, and at the
plan, before any code is written. The rest of the time you can watch, or go and get a coffee.

The order matters more than it looks. Understand the problem, look at what already exists, write
the plan down, then code, then proof, then a report. That is the order a careful senior developer
follows, and it is the order that keeps a codebase in one piece as it grows.

### 3.1 Find the issue number

Open the issue on GitHub. The number sits in the title after the `#`, for example
`Teachers can archive a lesson #42`. The number is `42`.

### 3.2 Start

In the Claude Code text box, type:

```text
/deepwater-developer:feature 42
```

and press Enter. From here on, each step below tells you what appears on your screen and what, if
anything, is expected from you.

### 3.3 Step A: Claude restates the issue

**You see:** a short summary in its own words: the problem, the expected outcome, who uses it,
what is in and out of scope, which interface is needed (GraphQL, REST...), and a list titled "Open
questions" if the issue left something unclear.

**You do:** if there are open questions, answer them in plain words in the text box, for example
`Yes, learners who already started the lesson keep access.` If there are none, nothing. Claude
continues on its own.

Why it restates first: most wasted work on a feature comes from building the wrong thing well.
Thirty seconds of reading the summary catches that before any code exists.

### 3.4 Step B: Claude looks at the existing code

**You see:** a list of the files it read (services, GraphQL schema, tests, CI) and, for each part
the issue touches, one word: REUSE, EXTEND, WRAP, REFACTOR, REPLACE or NEW, with the reason.

**You do:** nothing, unless you know the codebase better than it does on this point, which is
common. Then say so, for example `The lesson status already exists in EchoService.publish, reuse
it.` It will take that on board and adjust.

### 3.5 Step C: Claude writes the plan and waits for you

**You see:** `Plan written to docs/specs/42-archive-lesson.md`, followed by a list of open
questions, or "No blocking questions. Continue?"

**You do:** this is the one moment to slow down. Everything after it is built on this file. In
the Project panel, open `docs/specs/42-...md` and read it. It has 18 short, numbered sections, and
the ones that do not apply simply say "not applicable". Four are worth your full attention:

- section 2 is the promise: what will be built and, just as important, what will not;
- section 5 holds the business rules and the input/output contract;
- section 6 has the Given/When/Then scenarios, and these become the tests, so a missing scenario
  means a missing test;
- section 16 is where Claude writes down what it is unsure about. Read it precisely because it is
  the part a tool would rather hide.

If you disagree with something, either edit the file directly in IntelliJ, or write what you want
in the text box, for example `Section 5: archived lessons must still be visible to the teacher.`
Claude updates the plan. When you are satisfied, type `continue`. If a question blocks the work,
Claude will not continue until you answer it, and that is on purpose.

### 3.6 Step D: Claude writes the code

**You see:** files appear and change one by one, in the panel and in IntelliJ's Git → Local
Changes view. The order is deliberate: domain rule → use case → fake port → tests → the one
adapter the issue needs (for example a GraphQL mutation) → real adapter. Business logic goes into
the use case, never into the GraphQL resolver or a controller. That one rule is why the same
feature can later be offered over REST, or to an AI agent, without being rewritten.

**You do:** nothing. This can take a few minutes, and that is normal.

### 3.7 Step E: Claude runs the tests

**You see:** the actual commands and their actual output, not a summary. For example
`./gradlew :backend:test --tests ArchiveLessonTest` followed by `BUILD SUCCESSFUL` and the test
count, then the whole suite with `./gradlew :backend:test`. If something fails, Claude fixes it and
runs again. If a command cannot run on your computer (no JDK, no network), it says so instead of
pretending. A missing JDK is a fact to report, not something to paper over.

**You do:** read the output. "Tests pass" is only worth something when you can see the line that
says so. Green means the scenarios from section 6 pass.

### 3.8 Step F: Claude reports, and hands the work back to you

**You see:** a summary: files changed, test results, the definition-of-done list from section 17
of the plan with each line ticked or not, and anything still open.

**You do:** notice that nothing has been committed. That is deliberate. The plugin can write code,
but putting your name on it is yours to do. Review the diff in IntelliJ (Git → Local Changes,
double-click a file to see the change). When you are happy, commit and push as you always do
(Git → Commit, then Push), or type `commit and push` and Claude does it for you. Then open the
pull request on GitHub and paste the plan path (`docs/specs/42-archive-lesson.md`) into its
description, so the reviewer can read the plan next to the code.

### 3.9 The two other commands

Sometimes you want less than the whole journey.

| You want | Type | What happens |
| --- | --- | --- |
| Only the plan, no code, to agree on scope first | `/deepwater-developer:spec 42` | Steps A to C. Ends with the open questions and "ready to implement: yes/no" |
| A second opinion on a branch before opening a pull request | `/deepwater-developer:dod 42` | Compares your branch with the plan and the definition of done, runs the tests, lists what to fix. Changes nothing |

No issue number yet? Write the request in quotes instead:
`/deepwater-developer:feature "accept several correct answers for typed exercises"`. The plan is
then saved as `docs/specs/draft-<name>.md`, and you can turn it into an issue later.

Want it to run without stopping to ask? Type
`@agent-deepwater-developer:spec-driven-feature implement issue 42`. It works the same way in the
background, stops at the plan only if a question blocks it, and reports at the end. Good for a
small, well-described issue. For anything with judgement in it, the step-by-step command is the
better choice.

## 4. What a session looks like

So you know the rhythm before you start:

```text
you:    /deepwater-developer:feature 42

claude: Step A. Issue #42 "Teachers can archive a lesson". Outcome: an archived lesson is hidden
        from learners but kept for the teacher. Interface: GraphQL (the frontend already uses it).
        Open question: should learners who already started the lesson still see it?

you:    Yes, they keep access.

claude: Step B. Read EchoService.publish(), the lesson table, schema.graphqls, MvpEndToEndTest.
        EXTEND EchoService, EXTEND schema.graphqls, NEW ArchiveLesson use case, NEW test.
        Step C. Plan written to docs/specs/42-archive-lesson.md (16 sections filled,
        MCP and AG-UI not applicable). No blocking questions. Continue?

you:    continue

claude: Step D. Created ArchiveLesson.java, ArchiveLessonTest.java; changed EchoService.java,
        schema.graphqls, DeepwaterGraphQlController.java.
        Step E. ./gradlew :backend:test --tests ArchiveLessonTest   BUILD SUCCESSFUL, 3 tests
                ./gradlew :backend:test                              BUILD SUCCESSFUL, 31 tests
        Step F. Definition of done: 11 of 13 lines met. Not met: README update, pull request
        link. Nothing committed; review Git → Local Changes.
```

The issue, file names and numbers above are made up to show the shape. Yours will differ.

## 5. What the plugin will never do

These are promises written into the plugin's own instructions, and they are the reason you can
let it work without watching every line.

- **Commit, push, open a pull request or merge.** Git stays yours, so nothing reaches the team
  without a person having looked at it.
- **Invent requirements.** Anything unclear becomes a question in the plan rather than a guess in
  the code.
- **Put business logic into GraphQL resolvers, REST controllers, MCP tools or UI code.** Rules
  live in one place, so they are tested once and changed once.
- **Add a framework, a service, Kafka, CQRS or an AI call that the issue does not need now.**
  Every extra moving part is something the next person has to understand.
- **Claim a test passed without showing the output.** You see the evidence, not a summary of it.
- **Write secrets, tokens, personal data or production data into code, tests or docs.** Not even
  as an example.

## 6. When something goes wrong

Almost everything here comes down to one of two things: the path was wrong at install time, or a
tool is missing on your machine. Neither one damages anything.

| What you see | What to do |
| --- | --- |
| Typing `/deepwater-` shows nothing | Type `/plugin`. On the **Installed** tab, `deepwater-developer` should be there and enabled. If it is on the **Errors** tab, read the message; nearly always the path in step 4 was wrong, so redo steps 3 to 5 |
| `gh: command not found` or `not logged in` at step A | Install the GitHub CLI and run `gh auth login` in the Terminal tab, or paste the issue text: `/deepwater-developer:feature "<paste the issue title and text>"` |
| Step E says a command could not run | JDK 25 is missing or not on the path. Install it, or run `./gradlew :backend:test` yourself in the Terminal tab and paste the result into the text box |
| The plan file landed in the wrong folder | Open `deepwater-developer/deepwater/repository-mapping.md` and fix the specs path |
| Someone updated the plugin and you still get the old behaviour | Type `/plugin marketplace update deepwater`, then `/plugin install deepwater-developer@deepwater` again, or restart IntelliJ |
| You want it gone | `/plugin uninstall deepwater-developer@deepwater` |

If your problem is not in this table, say what you see in the Claude Code text box. It usually
knows what went wrong with its own plugin.

## 7. For maintainers: what is inside the folder

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
run `claude plugin validate ./deepwater-developer` in the Terminal tab.

If something in this manual was unclear to you, that is a bug in the manual. It is a Markdown file
like any other: fix it, or open an issue and say which sentence lost you.
