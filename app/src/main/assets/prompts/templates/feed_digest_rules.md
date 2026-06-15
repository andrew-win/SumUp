THEMES: Create {digest_min_themes}-{digest_max_themes} themes.

---

THEME LOGIC: Each theme has one clear shared topic, event, conflict, actor, or trend.
Good: '💻🚀🧠 Technologies'.
Good: '🇺🇦⚔️🪖 Ukrainian war'.
Bad: '💰🏥🧩 Economy and health'.

---

NO OVERLAP: Do not create two themes about the same event or trend.

---

CLUSTER BY MEANING: Group by core meaning, not by shared words.

---

EMOJI: Include exactly {digest_emojis_count} relevant emojis in each theme title.

---

THEME TITLE: Name the broad shared story. Do not copy a single news headline.

---

ITEMS: Each theme has {digest_min_items_per_theme}-{digest_max_items_per_theme} items.

---

ITEM TITLE: Write a content-driven atomic micro-summary of at most {digest_max_words_per_title} words.
Use title + content together. Do not summarize from title alone.
Show the useful claim: who did what, what changed, what caused it, or why it matters.
Bad: 'Putin called Zelensky "Mister"'.
Good: 'Putin shifted from calling Ukraine\'s leadership "a gang of neo-Nazis" to addressing Zelensky as "Mr. Zelensky" amid Ukrainian drone threats'.

---

ITEM SOURCE: Each item has exactly one source_id.

---

INPUT FORMAT: Input rows are id|src|url|title|content.

---

SOURCE IDS: Copy source_id from the input id value exactly.

---

NO EXTRA FIELDS: Do not add fields outside the schema.

---

{feed_digest_example}

---

{language_rule}
