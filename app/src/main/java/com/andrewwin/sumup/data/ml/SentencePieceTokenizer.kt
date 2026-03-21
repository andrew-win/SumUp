package com.andrewwin.sumup.data.ml

class SentencePieceTokenizer(modelBytes: ByteArray) {
    @Suppress("unused")
    private val serializedModelSize = modelBytes.size
    private var vocabulary: Set<String> = emptySet()

    fun setVocabulary(vocabulary: Set<String>) {
        this.vocabulary = vocabulary
    }

    fun encode(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val normalized = buildString(text.length + 8) {
            append('▁')
            var previousWasSpace = false
            for (char in text.trim().lowercase()) {
                if (char.isWhitespace()) {
                    if (!previousWasSpace) {
                        append('▁')
                        previousWasSpace = true
                    }
                } else {
                    append(char)
                    previousWasSpace = false
                }
            }
        }

        val pieces = ArrayList<String>(normalized.length)
        var index = 0
        while (index < normalized.length) {
            var end = normalized.length
            var matched: String? = null
            while (end > index) {
                val candidate = normalized.substring(index, end)
                if (candidate in vocabulary) {
                    matched = candidate
                    break
                }
                end--
            }

            if (matched != null) {
                pieces += matched
                index += matched.length
            } else {
                pieces += normalized[index].toString()
                index++
            }
        }
        return pieces
    }
}
