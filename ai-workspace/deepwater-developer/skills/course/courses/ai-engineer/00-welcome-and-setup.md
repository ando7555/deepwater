# 00 Welcome and setup

**Time:** about 1 hour. **Prerequisites:** none.

## Why this matters

Most failed AI projects fail before the first prompt: no clear problem, no way to measure, keys
pasted in chats, demos built on data that must not leave the company. Ten minutes of setup now
prevents all four.

## Talk it through

Ask, one at a time, and listen:

1. "What would you like to build with AI in the next three months? A real thing at work, an idea,
   or no idea yet?" If no idea, propose the running example from the course README.
2. "When an AI feature gives a wrong answer in your product, what happens to the user?" (This is
   the question that decides how much evaluation and human review the feature needs. Come back to
   it in module 04.)
3. "Which model access do you have today?" Explain the three ways in: a provider key, a company
   gateway, or a local model with Ollama. All three work for the whole course.

## Core ideas

- An **AI feature** is ordinary software with one unreliable dependency: the model. Everything you
  know about timeouts, contracts, tests and reviews still applies, plus measurement.
- **Secrets:** an API key is money and data access. It lives in an environment variable or the
  IDE's run configuration (IntelliJ: Run → Edit Configurations → Environment variables), never in
  Git and never in a chat.
- **Data:** before any lab, decide what data may go to a provider. In this course: only made-up or
  public data.

## Lab

1. Create a folder or a branch for the course work in the learner's repository (ask which).
2. Check the tools: `java -version`, `git --version`, and the build tool the project uses.
3. Set up model access, whichever applies, **without showing the key in the chat**:
   - Provider key: create it in the provider console, set it as an environment variable in the
     run configuration and in the shell (for example `ANTHROPIC_API_KEY` or `OPENAI_API_KEY`), and
     check that it is set without printing it: `test -n "$ANTHROPIC_API_KEY" && echo set` (on
     Windows PowerShell: `if ($env:ANTHROPIC_API_KEY) { "set" }`).
   - Ollama: install it, pull one small model from <https://ollama.com/library>, run
     `ollama list` and `curl http://localhost:11434/v1/models`.
   - Nothing yet: fine, module 02 starts with a fake.
4. Add the course folder's local secrets file patterns (`.env`, `*.local.properties`) to
   `.gitignore` if the project uses them.
5. Write one paragraph in the progress file: the problem they want to solve and what a wrong
   answer would cost the user.

## Check your understanding

- "Where does your key live, and how would you find out if it leaked?" (Environment variable or
  run configuration; the provider console shows usage and lets you revoke; a key in a Git history
  stays there even after deleting the line.)
- "Why do we decide which data may go to the model before we write code?" (Because a single test
  run already sends it; the provider's retention terms then apply.)

## Done when

- [ ] Model access works or the learner chose "fake first", and no key appeared in the chat.
- [ ] Secret files are ignored by Git (the mentor read `.gitignore` or confirmed none are used).
- [ ] The learner's problem statement and "cost of a wrong answer" are in the progress file.
