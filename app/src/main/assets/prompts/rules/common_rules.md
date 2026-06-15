SELF-CONTAINED: Every sentence must be fully understandable without reading any other sentence or knowing the source.
Include the subject, actor, or context needed to understand the claim on its own.
Bad: "He announced a ceasefire."
Good: "Russian President Putin announced a unilateral ceasefire starting May 8."

COMPLETE SENTENCES: Write complete sentences with enough words to be clear.
Do not strip words to save space. Do not use noun phrases or telegraphic fragments.
Bad: "Ceasefire announced. Casualties rising. Talks stalled."
Good: "Russia announced a unilateral ceasefire starting May 8, though Ukraine has not confirmed it."

NO VAGUE SUMMARIES: State concrete facts.
Bad: "The situation escalated."
Good: "North Korea said a nuclear strike would follow automatically if Kim Jong Un is killed."

NOISY CONTENT: Input can be noisy, fragmented, or poorly punctuated, for example because it is a YouTube transcript.
Still try to extract the key facts or produce a cautious useful summary from the title and available content.

SOURCE IDS: Use source IDs only in source_id or source_ids fields. Never write source IDs in title or text.

CLEAN TEXT: title and text fields contain only human-readable content. No IDs. No references. No metadata.
Bad: { "text": "According to ID 725, the price rose..." }
Good: { "text": "The price rose...", "source_ids": ["725"] }

LENGTH RULE: Word limits are only target numbers.
Actual expected length is shown in SOTA examples below.
You shouldn't compromise the meaning just to fit within strict limits.
