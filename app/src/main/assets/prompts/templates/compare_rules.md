{analytic_chain_rule}

---

MAIN: Return one main value with exactly {compare_main_sentences} sentence about the essence of the compared news.

---

MAIN QUALITY: Main must capture the core meaning as fully as possible in one concrete sentence.

---

MAIN LENGTH: Keep main short, concrete, and understandable without details.

---

DETAILS: Return up to {compare_max_bullets} atomic detail items.

---

DETAIL ROLE: Each detail is one concrete chain step: main claim, evidence, contrast, cause, context, result, consequence, conflict, or caveat.

---

SOURCE COVERAGE: Use facts from all relevant sources. Assign one or more source_ids to each detail.

---

SOURCE SPECIFIC DETAILS: Include concrete source-specific details when they add useful context, evidence, numbers, quotes, causes, or caveats.

---

NO WORDING-ONLY DETAILS: Do not create a detail only because sources use different wording, tone, or emphasis.

---

NO MAIN DUPLICATE: Details must not repeat the main sentence. Add only new facts, context, evidence, contrast, or caveats.

---

NO DUPLICATES: Do not repeat the same meaning in multiple items.

---

ALWAYS EXTRACT: Prefer a cautious partial summary with fewer details over fallback.
Use fallback only when titles and content contain no understandable news claim at all.

---

CHAIN SPLIT: Output main claim, evidence, contrast, and cause as separate facts.
Bad: "Putin's attitude changed from insulting to polite."
Good: separate facts for the change, the 2022 insult, the 2026 formal address, and the cause.

---

SOURCE IDS: Copy every source_id exactly from input. Never invent, modify, or translate source_ids.

---

DETAIL LENGTH: Each detail is at most {compare_max_words_per_point} words.

---

FALLBACK: Only if no understandable news claim exists in either titles or content, set main to null, details to [], and fallback to: {fallback}

---

NO FALLBACK AS DETAIL: Never write fallback text inside details.

---

NULL FALLBACK: Set fallback to null when not needed.

---

{compare_example}

---

{language_rule}
