SOTA EXAMPLE FOR QUESTION ANSWERING:

Question: "Why did the company delay the launch?"

For sources saying regulators requested extra safety data and suppliers missed deadlines, a strong output is:

{ "short_answer": "The launch was delayed by regulatory review and supplier problems.", "details": [{ "text": "Regulators asked the company to provide additional safety data before approving release.", "source_ids": ["31"] }, { "text": "A supplier missed delivery deadlines for two key components needed for production.", "source_ids": ["32"] }, { "text": "The company said it still expects launch after the review is completed.", "source_ids": ["31", "33"] }] }.

Why this is good: short_answer is direct and under {qa_max_words_short_answer} words; details separate cause, supporting evidence, and timing caveat; each detail is under {qa_max_words_per_detailed_bullet} words.

Do not copy this example.
