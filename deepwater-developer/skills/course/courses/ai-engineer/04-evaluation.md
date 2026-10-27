# 04 Evaluate before you trust

**Time:** about 5 hours. **Prerequisites:** 03. **Earns Level 2: AI practitioner** with 03.

## Why this matters

This is the skill that separates AI engineers from people who write prompts. Without an evaluation
set every change is a guess: a new prompt "looks better", a cheaper model "seems fine", and three
weeks later teachers stop trusting the drafts. An eval set is a test suite for behaviour you
cannot assert exactly.

## Talk it through

- "How did you decide last module that your prompt was good?"
- "What would make a drafted exercise *bad* for a teacher? List five ways."
- Return to the question from module 00: "what does a wrong answer cost your user?" The answer
  sets how strict the evaluation must be.

## Core ideas

- **Eval set:** 20 to 50 realistic inputs to start, chosen to cover normal cases, edge cases and
  known failures. Store them as files in the repository, next to the prompt. Never use personal
  data.
- **Graders, cheapest first:** code checks (valid JSON, schema, counts, forbidden words, the
  expected answer appears in the options) catch most failures for free. Rubric checks by a human
  for what code cannot judge. A model as judge only with a written rubric, a fixed judge model and
  spot checks by a human, because judges have their own biases.
- **Baseline first.** Measure the current version before changing anything; a number only means
  something next to another number. A non-AI baseline (a template, a fixed list) is often a
  surprisingly strong competitor.
- **One change at a time:** prompt, model, provider or effort. Compare score, cost per completed
  task and latency. Look at the failures, not just the average.
- **Repeat runs:** answers vary, so run each case more than once when results are close.
- **Keep a held-out set** you do not look at while tuning, so you do not fool yourself.

## Lab

1. Write 20 cases for the exercise drafter (topics, levels, counts, two tricky ones such as an
   empty topic or an injected instruction). Store them as JSON or YAML in the repository.
2. Write the code graders as a JUnit test or a small runner: schema valid, count correct, level
   respected, expected answer present, no duplicate prompts.
3. Write a 1-to-5 rubric for usefulness to a teacher. The learner grades ten outputs by hand.
4. Run the baseline (prompt v1, current model) and record: pass rate per grader, average rubric
   score, cost per completed draft, average latency.
5. Make one change (a better prompt, or a different provider through module 02's configuration)
   and run again. Decide with the numbers, and write the decision down.

## Check your understanding

- "Your new prompt scores 92% instead of 88% on 20 cases. Ship it?" (Maybe: check variance by
  re-running, look at which cases changed, check cost and latency, confirm on the held-out set.)
- "Why not let a model grade everything?" (Cost, bias toward its own style, and it can be wrong the
  same way the generator is; code checks and humans anchor it.)

## Done when

- [ ] An eval set of at least 20 cases is in the repository.
- [ ] Code graders run with one command; the mentor ran them and recorded the result.
- [ ] Baseline and one comparison with numbers (quality, cost per completed task, latency) and a
      written decision are in the progress file.
