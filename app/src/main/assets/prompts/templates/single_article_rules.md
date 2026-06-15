{analytic_chain_rule}

---

MAIN: Return one main value with exactly {single_main_sentences} sentence about the essence of the article.

---

MAIN QUALITY: Main must capture the core meaning as fully as possible in one concrete sentence.

---

MAIN LENGTH: Keep main short, concrete, and understandable without details.

---

DETAILS: Return 1-{single_max_points} detail items.

---

DETAIL ROLE: Each detail is one chain step: evidence, context, cause, result, consequence, conflict, or caveat.

---

DETAIL LENGTH: Each detail is at most {single_max_words_per_point} words.

---

NO MAIN DUPLICATE: Details must not repeat the main sentence. Add only new facts, context, evidence, or caveats.

---

SOURCE IDS: Every detail includes source_ids with the article source_id.

---

NO HEADLINE REPEAT: Do not restate the headline unless you add concrete context, cause, or result.

---

{single_article_example}

---

{language_rule}
