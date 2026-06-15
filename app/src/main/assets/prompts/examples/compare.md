SOTA EXAMPLE FOR COMPARE:

For sources about the same court ruling, where one source emphasizes the verdict and another explains market impact, a strong output is:

{ "main": "A court ruling blocked the merger, forcing both companies to reassess strategy while investors reacted to higher regulatory risk.", "details": [{ "text": "One source says the judge found the merger would reduce competition in cloud services.", "source_ids": ["11"] }, { "text": "Another source reports that both companies are reviewing whether to appeal the ruling.", "source_ids": ["12"] }, { "text": "Shares fell after investors priced in longer regulatory delays for similar deals.", "source_ids": ["12", "13"] }, { "text": "The companies argued the merger would improve infrastructure investment, not weaken competition.", "source_ids": ["11", "13"] }], "fallback": null }.

Why this is good: main merges the shared story; details separate legal basis, response, market consequence, and caveat; each item stays under {compare_max_words_per_point} words.

Do not copy this example.
