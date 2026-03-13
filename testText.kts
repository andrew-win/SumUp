import org.jsoup.Jsoup

val html = """<div class="tgme_widget_message"><div class="tgme_widget_message_text">Line 1<br>Line 2<br><br>Line 3</div></div>"""
val doc = Jsoup.parse(html)
val textElement = doc.selectFirst(".tgme_widget_message_text")
textElement!!.select("br").append("\\n")
val rawText = textElement.wholeText().replace("\\n", "\n")
println("RAW TEXT:")
println(rawText.replace("\n", "\\n"))
