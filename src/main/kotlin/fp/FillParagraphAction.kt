package fp

import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class FillParagraphAction: AnAction("XFillParagraph") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val doc = editor.document
        val offset = editor.caretModel.offset

        val file = PsiDocumentManager.getInstance(project).getPsiFile(doc)
        val lang = PsiUtilBase.getLanguageInEditor(editor.caretModel.primaryCaret, project) ?: return
        val commenter = LanguageCommenters.INSTANCE.forLanguage(lang)
        val lineCommentPrefix = commenter?.lineCommentPrefix ?: ""

        val p = getParagraph(doc, offset, lineCommentPrefix) ?: return
        val lines = fillParagraph(p)

        val refilledText = lines.joinToString("\n") {
            line -> p.paragraphPrefix + line
        }

        project.service<FpService>().cs.launch(Dispatchers.EDT) {
            writeCommandAction(project, "XFillParagraph") {
                doc.replaceString(p.startOffset, p.endOffset, refilledText)
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
    val paragraphPrefix: String? = null // to be inserted before each line of refilled paragraph
)


fun getParagraph(doc: Document, offset: Int, lineCommentPrefix: String): Paragraph? {
    val lines = mutableListOf<String>()

    // up
    val currentLine = doc.getLineNumber(offset)
    var paragraphStart = Int.MAX_VALUE
    var paragraphEnd = 0
    var line = currentLine
    var paragraphPrefix: String? = null // will be inserted before each line of refilled paragraph
    while (line >= 0) {
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.text.substring(lineStart, lineEnd)

        val commentStart = lineText.indexOf(lineCommentPrefix)
        if (commentStart == -1 || lineText.take(commentStart).isNotBlank()) {
            // not a line with a comment
            break
        }

        val textStart = commentStart + lineCommentPrefix.length
        if (paragraphPrefix == null) {
            paragraphPrefix = lineText.take(textStart)
        }

        val text = lineText.substring(textStart)
        if (text.isBlank()) {
            break
        }

        lines.add(text)
        paragraphStart = lineStart
        if (paragraphEnd == 0) {
            paragraphEnd = lineEnd
        }
        line--
    }

    if (lines.isEmpty()) {
        // no paragraph at the current line
        return null
    }

    lines.reverse()

    // down
    line = currentLine + 1
    while (line < doc.lineCount) {
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.text.substring(lineStart, lineEnd)

        val commentStart = lineText.indexOf(lineCommentPrefix)
        if (commentStart == -1 || lineText.substring(0, commentStart).isNotBlank()) {
            // not a line with a comment
            break
        }

        val textStart = commentStart + lineCommentPrefix.length
        val text = lineText.substring(textStart)
        if (text.isBlank()) {
            break
        }

        lines.add(text)

        paragraphEnd = lineEnd
        line++
    }

    return Paragraph(paragraphStart, paragraphEnd, lines, paragraphPrefix)
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

    val indent = p.lines.firstOrNull()?.let { line0 ->
        line0.substring(0, line0.skipSpaces())
    } ?: ""
    currentLine.append(indent)

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
                currentLine.append(indent)
            }
            else if (currentLine.isNotBlank()) {
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
