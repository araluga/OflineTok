package com.oflinetok.app.ai

import org.json.JSONObject
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * CLIP's byte-level BPE tokenizer, matching Hugging Face's `tokenizer.json` for
 * openai/clip-vit-base-patch32: NFC, collapse whitespace, lowercase, split into words,
 * byte-to-unicode mapping, BPE with an end-of-word "</w>" marker, wrapped in start/end tokens.
 */
class ClipTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
) {
    private val ranks: Map<Pair<String, String>, Int> = merges.withIndex().associate { (i, m) -> m to i }
    private val cache = HashMap<String, List<String>>()

    fun encode(text: String): IntArray {
        val normalized = WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC))
            .replaceAll(" ")
            .lowercase()
        val ids = ArrayList<Int>()
        ids += START
        val words = WORD.matcher(normalized)
        while (words.find()) {
            val word = words.group()
            if (word == "<|startoftext|>" || word == "<|endoftext|>") continue
            val mapped = buildString { word.toByteArray(Charsets.UTF_8).forEach { append(BYTE_TO_CHAR[it.toInt() and 0xff]) } }
            for (piece in bpe(mapped)) ids += vocab[piece] ?: END
        }
        if (ids.size > MAX_LENGTH - 1) ids.subList(MAX_LENGTH - 1, ids.size).clear()
        ids += END
        return ids.toIntArray()
    }

    private fun bpe(word: String): List<String> = cache.getOrPut(word) {
        val symbols = word.map { it.toString() }.toMutableList()
        symbols[symbols.lastIndex] = symbols.last() + "</w>"
        while (symbols.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until symbols.size - 1) {
                val rank = ranks[symbols[i] to symbols[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = i
                }
            }
            if (best < 0) break
            val first = symbols[best]
            val second = symbols[best + 1]
            // Merge every occurrence of this pair, left to right, like the reference implementation.
            val merged = ArrayList<String>(symbols.size)
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == first && symbols[i + 1] == second) {
                    merged += first + second
                    i += 2
                } else {
                    merged += symbols[i]
                    i++
                }
            }
            symbols.clear()
            symbols.addAll(merged)
        }
        symbols
    }

    companion object {
        const val START = 49406
        const val END = 49407
        const val MAX_LENGTH = 77

        private val WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS)
        private val WORD = Pattern.compile(
            "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",
            Pattern.UNICODE_CHARACTER_CLASS,
        )

        /** GPT-2's reversible byte → printable unicode character table. */
        private val BYTE_TO_CHAR: CharArray = run {
            val printable = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toSet()
            var extra = 0
            CharArray(256) { b ->
                if (b in printable) b.toChar() else (256 + extra++).toChar()
            }
        }

        /** Builds the tokenizer from a Hugging Face `tokenizer.json`. */
        fun fromJson(json: String): ClipTokenizer {
            val model = JSONObject(json).getJSONObject("model")
            val vocabJson = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabJson.length() * 2)
            for (key in vocabJson.keys()) vocab[key] = vocabJson.getInt(key)
            val mergesJson = model.getJSONArray("merges")
            val merges = (0 until mergesJson.length()).map { i ->
                // Newer files store merges as ["a", "b"], older ones as "a b".
                val m = mergesJson.get(i)
                if (m is org.json.JSONArray) {
                    m.getString(0) to m.getString(1)
                } else {
                    val s = m.toString()
                    val space = s.indexOf(' ')
                    s.substring(0, space) to s.substring(space + 1)
                }
            }
            return ClipTokenizer(vocab, merges)
        }
    }
}
