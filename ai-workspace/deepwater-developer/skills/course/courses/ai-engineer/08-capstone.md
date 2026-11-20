# 08 Capstone: ship an AI feature

**Time:** 6 hours or more. **Prerequisites:** 02, 03, 04, 06, 07 (and 05 or its documented skip).
**Earns the certificate.**

## Why this matters

Everything so far was practice. The capstone is one real AI feature, shipped the way a team ships
software: ticket, specification, smallest slice, evaluation, tests, review, pull request. This is
the evidence you can show anyone.

## Talk it through

- "Which feature will you ship? Is it small enough to finish in two or three sessions?" Help them
  cut it down until it is.
- "Who reviews your pull request, and what would convince them?"

## How to run it

The capstone uses the plugin's delivery commands; the mentor stays beside the learner.

1. **Ticket.** Write the issue in the learner's tracker (or as text): problem, outcome, who uses it,
   what a wrong answer costs, out of scope. For the running example: "Teachers get AI-drafted
   exercises for a topic and level, review and edit them, and publish only what they approve."
2. **Specification.** `/deepwater-developer:spec <ticket>`. The learner answers the open decisions.
   Section 9 of the plan (AI behaviour) must name: the port and default adapter, model id with
   source and date, prompt file and version, output schema, eval set and the score that is "good
   enough", limits (timeout, retries, tokens, cost), fallback, and which data may leave the system.
3. **Build.** `/deepwater-developer:feature <ticket>`, or by hand with the mentor reviewing. Reuse
   the port, prompt, validation, eval set and production guards from modules 02 to 07.
4. **Evaluate.** Run the eval set; the result must meet the bar written in the specification.
5. **Definition of done.** `/deepwater-developer:dod <ticket>`. Fix what it finds.
6. **Pull request.** The learner opens it, with the eval result and the test output in the
   description, and asks a colleague for review.
7. **Certificate.** `/deepwater-developer:course certificate`.

## Check your understanding (the final conversation)

The mentor asks the learner to explain, in their own words, to an imagined new teammate:

- how the feature works end to end and where the model sits in it;
- how they know it is good enough, with the numbers;
- what happens when the provider fails, gets expensive, or is attacked through the input;
- what they would change first with one more week.

## Done when

- [ ] Specification with a complete AI section exists in the repository.
- [ ] Eval result meets the bar in the specification (the mentor ran it and recorded the numbers).
- [ ] Tests pass (the mentor ran them) and `/deepwater-developer:dod` shows no blocking finding.
- [ ] A pull request link exists.
- [ ] The final conversation is recorded in one paragraph in the progress file.
