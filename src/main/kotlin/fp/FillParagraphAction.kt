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
) {

}

data class Paragraph(
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<String>,
)


fun getParagraph(doc: Document, offset: Int): Paragraph? {
    val line = doc.getLineNumber(offset)
    val lineStart = doc.getLineStartOffset(line)
    val lineEnd = doc.getLineEndOffset(line)
    return Paragraph(lineStart, lineEnd, listOf(doc.text.substring(lineStart, lineEnd)))
}


fun fillParagraph(p: Paragraph): List<String> {
    val result = arrayListOf<String>()
    for (line in p.lines) {
        result.add(line + " hui")
    }
    return result
}