# Start here

**This page is for everyone.** You do not need to know Claude Code, plugins or AI. Read it top to
bottom once. It takes about 15 minutes, including the setup.

---

## What is this, in three sentences

`deepwater-developer` is an add-on (a *plugin*) for **Claude Code**, the AI assistant for
programmers. It turns Claude into a patient mentor that **teaches you to become an AI engineer**,
step by step, inside your own project. It also helps you **build features the careful way**: plan
first, then code, then tests, then an honest report.

You stay in control. It never sends your code anywhere new, never asks for your passwords or keys,
and never saves to Git unless you ask.

---

## What you need (check these first)

| You need | How to check | If you do not have it |
| --- | --- | --- |
| **Claude Code** | Open a terminal (see the word list below) and type `claude --version`. You should see a number, like `2.1.310 (Claude Code)`. | Install it from <https://claude.com/claude-code>. Then type `claude` once and sign in with your Claude account. |
| **The plugin folder** | You have a folder called `deepwater-developer` on your computer. | Download it: open <https://github.com/ando7555/deepwater>, press the green **Code** button, then **Download ZIP**. Unzip it. The folder is inside. |
| **Java** (only for the coding labs) | Type `java -version` in a terminal. | In IntelliJ: File → Project Structure → SDKs → **+** → Download JDK. |

You do **not** need an AI account or an API key to start. The course begins without one.

---

## Install it (once per computer, about 3 minutes)

Do these steps in order. After each step, compare with **You should see**.

**1. Open Claude Code.**
- In IntelliJ: click the **Claude Code** icon on the right side of the window.
  If you cannot find it: View → Tool Windows → Claude Code.
- Without IntelliJ: open a terminal and type `claude`, then press Enter.

*You should see:* a box where you can type, at the bottom.

**2. Copy the full path of the plugin folder.**
- In IntelliJ: right-click the `deepwater-developer` folder → **Copy Path/Reference…** →
  **Absolute Path**.
- Windows Explorer: hold **Shift**, right-click the folder → **Copy as path** (remove the quotes
  when you paste).
- Mac Finder: right-click the folder, hold **Option** → **Copy "deepwater-developer" as Pathname**.

*You should see:* nothing yet. The path is now in your clipboard. It looks like
`C:\Users\anna\Downloads\deepwater\deepwater-developer` or `/Users/anna/deepwater/deepwater-developer`.

**3. Register the folder.** In the Claude Code box, type this, then a space, then paste the path,
then press Enter:

```text
/plugin marketplace add PASTE-THE-PATH-HERE
```

*You should see:* `Successfully added marketplace: deepwater`.
*If you see "not found":* the path is wrong. Do step 2 again.

**4. Install the plugin.** Type and press Enter:

```text
/plugin install deepwater-developer@deepwater
```

When it asks where to install, choose **User** (this means: for me, in all my projects).

*You should see:* `Successfully installed plugin`.

**5. Check it works.** Type `/deepwater-` and **stop typing**.

*You should see:* a small list with `/deepwater-developer:help`, `:course`, `:init`, `:feature`,
`:spec` and `:dod`. If the list does not appear, close and open Claude Code once, then try again.

That's it. You never need to install it again on this computer.

---

## Use it: pick what you want

### "I'm lost" or "I'm not sure what to do"

```text
/deepwater-developer:help
```

Tell it in your own words what you want, for example *"I'm new, where do I start?"* or *"how do I
add my API key?"*. It answers in simple words and tells you the exact next thing to type.

### "I want to learn AI engineering"

```text
/deepwater-developer:course
```

The mentor asks you a few easy questions (your name, what you build, how much time you have),
then suggests a plan. Each session takes 45 to 90 minutes. To continue next time, type the same
command. It remembers where you stopped.

### "I have a task or a ticket to build"

First time in a project, type this once. It looks at your project and writes a small settings
file. Read what it shows you and answer `yes`.

```text
/deepwater-developer:init
```

Then give it the task. Use the ticket number, or describe the task in quotes:

```text
/deepwater-developer:feature 42
/deepwater-developer:feature "teachers can hide a lesson from students"
```

It shows you a plan and **waits for you**. Read the plan, answer its questions, then type
`continue`. It writes the code, runs the tests, and shows you the real results. Then you review
the changes and save them to Git yourself.

---

## Words you might not know

| Word | What it means |
| --- | --- |
| **Terminal** | A window where you type commands. In IntelliJ: the **Terminal** tab at the bottom. On Windows: *PowerShell*. On Mac: the *Terminal* app. |
| **Claude Code** | Claude, the AI assistant, working inside your project: it can read files, run tests and write code when you allow it. |
| **Plugin** | An add-on that gives Claude Code new commands and knowledge. |
| **Command** | Something you type that starts with `/`, like `/deepwater-developer:course`. |
| **Path** | The full address of a folder on your computer. |
| **Ticket / issue** | A written task, usually in GitHub or GitLab, with a number like `#42`. |
| **API key** | A secret password that lets your code use an AI service. Treat it like a bank card PIN. |
| **Environment variable** | A named value your computer gives to programs, for example `OPENAI_API_KEY`. This is where API keys belong, never inside code or a chat. |
| **Model / provider** | The AI itself (the *model*) and the company that runs it (the *provider*): for example Claude by Anthropic, GPT by OpenAI, or a free model on your own computer with Ollama. |
| **Commit / push** | Saving your changes in Git (commit) and sending them to GitHub or GitLab (push). The plugin never does this unless you ask. |

---

## How to set an API key safely (only when the course asks)

Never paste a key into the chat. If you did, go to the provider's website, delete that key and
make a new one.

- **IntelliJ:** Run → Edit Configurations… → choose your app → **Environment variables** → add
  `NAME=value` (for example `ANTHROPIC_API_KEY=…`). The key stays on your computer.
- **Windows (PowerShell), for this window only:** `$env:ANTHROPIC_API_KEY = "your-key"`
- **Mac or Linux, for this window only:** `export ANTHROPIC_API_KEY="your-key"`

To check it is set without showing it: Windows `if ($env:ANTHROPIC_API_KEY) { "set" }`, Mac or
Linux `test -n "$ANTHROPIC_API_KEY" && echo set`.

---

## If something goes wrong

| What you see | What to do |
| --- | --- |
| `claude: command not found` | Claude Code is not installed, or the terminal was open before you installed it. Install it, then open a new terminal. |
| "not found" after `/plugin marketplace add` | The path is wrong or has quotes. Copy it again (step 2) and paste without quotes. |
| `/deepwater-` shows no list | Type `/plugin`, open the **Errors** tab and read the message. Usually step 3 used a wrong path. |
| It asks for permission to read a file or run a command | That is normal. Claude Code asks before it acts. Read what it wants to do and choose **Yes** if it makes sense. |
| "gh: command not found" when you give a ticket number | Paste the ticket text in quotes instead of the number. |
| You want to remove it | `/plugin uninstall deepwater-developer@deepwater` |

Still stuck? Type `/deepwater-developer:help` and describe what you see, or copy the error message
into the box. If something on this page confused you, that is our mistake: tell us which sentence.

---

**Next:** the full manual with every detail is [README.md](README.md). Using another AI assistant
(Codex, OpenCode, Cursor)? See [AGENTS.md](AGENTS.md).
