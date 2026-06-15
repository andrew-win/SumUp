{analytic_chain_rule}

---

SOURCES ONLY: Answer only from the provided sources. Never use outside knowledge.

---

SHORT ANSWER: Write one natural sentence of at most {qa_max_words_short_answer} words.
State the direct conclusion, not a list of evidence.
Be cautious when sources are incomplete, indirect, or conflicting.
Do not present uncertain information as certain.

---

DETAILS: Return up to {qa_max_detail_points} atomic detail bullets.
Each detail is one chain step: evidence, context, cause, consequence, disagreement, caveat, or missing information.
Each detail is at most {qa_max_words_per_detailed_bullet} words.
Each detail includes 1 or more valid source_ids.

---

NO DIRECT ANSWER: If sources are on topic but do not directly answer, set short_answer to: '{no_direct_answer}' and add relevant context in details.

---

CAN'T ANSWER: If sources are unrelated to the question, set short_answer to: '{fallback}'.

---

SOURCE IDS: Copy every source_id exactly from input. Never invent source_ids.

---

{question_example}

---

{language_rule}
