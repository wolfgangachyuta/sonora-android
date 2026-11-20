package com.sonora.player

object PlainTextFormats {
    fun markdownToHtml(source: String): String {
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val rendered = source.lineSequence().joinToString("\n") { line ->
            val clean = escape(line)
            when {
                clean.startsWith("### ") -> "<h3>${clean.drop(4)}</h3>"
                clean.startsWith("## ") -> "<h2>${clean.drop(3)}</h2>"
                clean.startsWith("# ") -> "<h1>${clean.drop(2)}</h1>"
                clean.startsWith("> ") -> "<blockquote>${clean.drop(2)}</blockquote>"
                clean.startsWith("- ") || clean.startsWith("* ") -> "<p>• ${clean.drop(2)}</p>"
                clean.isBlank() -> "<br/>"
                else -> "<p>$clean</p>"
            }
        }.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("\\[([^]]+)]\\((https?://[^)]+)\\)"), "<a href=\"$2\">$1</a>")
        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{font:18px/1.7 serif;padding:24px;background:#f7f6f1;color:#252722}img{max-width:100%}blockquote{border-left:3px solid #66816b;padding-left:14px;color:#666}</style></head><body>$rendered</body></html>"
    }

    fun rtfToHtml(source: String): String {
        val text = source
            .replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
            .replace(Regex("""\\par[d]?"""), "\n")
            .replace(Regex("""\\line"""), "\n")
            .replace(Regex("""\\tab"""), "    ")
            .replace(Regex("""\\u(-?\d+)\??""")) { match ->
                match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(if (it < 0) it + 65536 else it)) } ?: ""
            }
            .replace(Regex("""\\[a-zA-Z]+-?\d* ?"""), "")
            .replace(Regex("""\\[^a-zA-Z]"""), "")
            .replace("{", "").replace("}", "").trim()
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{font:18px/1.7 serif;padding:24px;background:#f7f6f1;color:#252722;white-space:pre-wrap}</style></head><body>$escaped</body></html>"
    }
}
