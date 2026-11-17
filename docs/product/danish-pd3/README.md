# Danish PD3 practice exam

Owner ask, 2026-11-16: the Danish course "should become similar to Polish": exam-style parts, an examiner that marks
them, and a readiness estimate. Not merged until the owner approves.

## Which exam, and why

Prøve i Dansk 3 (PD3), the test that ends Danskuddannelse 3, at about CEFR B2. It is the highest of the general Danish
tests and the one asked for Danish citizenship, so it is the natural target for someone who wants to work in Danish.
Prøve i Dansk 2 (about B1) is the lower test; sources disagree on whether PD2 or PD3 is needed for permanent residence.
Which level a given employer wants is not verified; Harbor shows what each job advert says about Danish. The official sites (uvm.dk, nyidanmark.dk) were blocked from the build machine, so this was
checked against language-school pages and the legal text:

- retsinformation.dk/eli/lta/2021/419 (bekendtgørelse om prøver i dansk)
- ucplusdansk.dk, studieskolen.dk and the University of Copenhagen PD3 workshop page (structure of the test)

PD3 has a written part (reading comprehension in two parts, and written presentation) and an oral part (listening
comprehension, and oral communication: a topic the learner knows and a topic given on the day). The practice exam
follows that shape. Exact task types, timings and the official pass rule should be checked with a sprogcenter before
any claim is made in marketing.

## What the learner sees

In Echo, Danish now has a second switch: `Course | PD3 exam practice`. Polish stays B1 exam practice only.

- Four parts: Listening (Lytteforståelse, 5 questions), Reading (Læseforståelse, a short job advert and a longer
  article, 7 questions), Writing (Skriftlig fremstilling: a mail to a landlord, 100-150 words, and a debate piece,
  180-250 words) and Speaking (Mundtlig kommunikation: one familiar topic and two discussion topics).
- Items are Deepwater's own, in `backend/src/main/resources/exam/danish-pd3.json`, labelled "not official".
- Listening uses the browser's Danish voice (da-DK); speaking uses Danish speech recognition where the browser has it.
- Same pass rule as Polish: every part needs `EXAM_PASS_PERCENT` (default 60). Level estimate against B2:
  B2+, B2, B1 (close), A2-B1.

## How it works

The exam engine is shared. Each exam is a JSON file with `language`, `level`, `examName` and `shortName`; the API is
`/api/v1/exam/{examId}` (`polish-b1`, `danish-pd3`). The AI examiner prompt names the language, exam and level; the
fixed rubric uses Danish letters (æ, ø, å), Danish linking words and Danish common words. Attempts are stored per exam.
The assistant and MCP server get a `danish_pd3_results` tool.

Open: real AI marking still needs `ANTHROPIC_API_KEY` on Railway; until then writing and speaking get the rough rubric
mark, which is labelled as such.
