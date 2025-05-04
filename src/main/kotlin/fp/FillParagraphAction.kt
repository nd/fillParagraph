package fp

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FillParagraphAction: AnAction("Fill") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val doc = editor.document
        val offset = editor.caretModel.offset
        val p = getParagraph(doc, offset) ?: return
        val lines = fillParagraph(p)

        project.service<FpService>().cs.launch(Dispatchers.EDT) {
            writeCommandAction(project, "Fill") {
                doc.replaceString(p.startOffset, p.endOffset, lines.joinToString("\n"))
            }
        }
    }
}


@Service(Service.Level.PROJECT)
class FpService(
    val project: Project,
    val cs: CoroutineScope
)

data class Paragraph(
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<String>,
)


fun getParagraph(doc: Document, offset: Int): Paragraph? {
    val lines = mutableListOf<String>()

    // up
    val currentLine = doc.getLineNumber(offset)
    var paragraphStart = Int.MAX_VALUE
    var paragraphEnd = 0
    var line = currentLine
    while (line >= 0) {
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.text.substring(lineStart, lineEnd)
        if (lineText.isBlank()) {
            break
        }
        lines.add(lineText)
        paragraphStart = lineStart
        if (paragraphEnd == 0) {
            paragraphEnd = lineEnd
        }
        line--
    }
    lines.reverse()

    // down
    line = currentLine + 1
    while (line < doc.lineCount) {
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.text.substring(lineStart, lineEnd)
        if (lineText.isBlank()) {
            break
        }
        lines.add(lineText)
        paragraphEnd = lineEnd
        line++
    }

    return Paragraph(paragraphStart, paragraphEnd, lines)
}

fun String.skipSpaces(fromIdx: Int = 0): Int {
    for (i in fromIdx until length) {
        if (this[i] != ' ' && this[i] != '\t') {
            return i
        }
    }
    return length
}


val whiteSpaces = charArrayOf(' ', '\t')

// returns new lines composing a refilled paragraph
fun fillParagraph(p: Paragraph): List<String> {
    val paragraphWidth = 70
    val result = arrayListOf<String>()
    val currentLine = StringBuilder()
    var wordStart: Int
    var wordEnd: Int
    for (line in p.lines) {
        wordStart = line.skipSpaces()
        while (wordStart < line.length) {
            wordEnd = line.indexOfAny(whiteSpaces, wordStart)
            if (wordEnd == -1) {
                wordEnd = line.length
            }
            if (currentLine.isNotEmpty() && currentLine.length + (wordEnd - wordStart) + 1 > paragraphWidth) {
                // if current line is empty and the word is long add it anyway
                result.add(currentLine.toString())
                currentLine.setLength(0)
            }
            else if (currentLine.isNotEmpty()) {
                currentLine.append(' ')
            }
            currentLine.append(line.substring(wordStart, wordEnd))
            wordStart = line.skipSpaces(wordEnd)
        }
    }
    if (currentLine.isNotEmpty()) {
        result.add(currentLine.toString())
    }
    return result
}