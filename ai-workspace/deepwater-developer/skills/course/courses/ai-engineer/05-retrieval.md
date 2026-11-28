# 05 Retrieval with provenance

**Time:** about 4 hours. **Prerequisites:** 04. **Optional** when the learner's product has no
documents or knowledge to ground answers in; record "optional, not taken" in that case.

## Why this matters

Models do not know your company's documents, and they invent when asked. Retrieval puts the right
facts into the context and lets every answer say where it came from. Done badly, it adds a vector
database and nothing else; done well, it starts with a simple search and an evaluation.

## Talk it through

- "Which facts would the model need to answer well in your product? Where do they live?"
- "How would a teacher check where a drafted explanation came from?"

## Core ideas

- **RAG (retrieval-augmented generation):** find relevant passages, put them in the prompt with
  their source ids, ask the model to answer only from them and cite the ids, then check the cited
  ids exist.
- **Start simple:** keyword search (database full-text search, or Lucene) is a strong baseline.
  Add embeddings and a vector store only when the eval set shows keyword search misses relevant
  passages.
- **Chunking:** split documents into passages that make sense alone (a section, a rule with its
  example). Keep the source document, version and position with every chunk: that is provenance.
- **Measure retrieval separately:** for each eval case, which passages *should* be found? Measure
  whether they were, before you measure the final answer.
- **Rights and privacy:** only index content you may use, and remember the passages go to the
  provider.

## Lab

1. Take a small set of real, shareable documents (for Echo: lesson explanations and grammar notes
   with source references). Chunk them with provenance.
2. Implement keyword retrieval behind a `PassageRetriever` port. Write five retrieval eval cases
   with the expected passage ids and measure recall.
3. Change the exercise drafter to include the top passages with ids, and require each explanation
   to cite one. Validate the citations in code.
4. Re-run module 04's eval set and compare with the version without retrieval.

## Check your understanding

- "Retrieval found the right passage but the answer is still wrong. Where do you look?" (The
  prompt, how passages are presented, or the model; not the retriever.)
- "When would you add embeddings?" (When measured recall with keyword search is too low on cases
  where wording differs from the query.)

## Done when

- [ ] Chunks carry provenance; retrieval is behind a port with its own eval cases and a measured
      recall.
- [ ] Answers cite passage ids that the code validates; the mentor ran the tests.
- [ ] The eval comparison with and without retrieval is recorded with a decision.
