package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.refactoring.rename.RenameHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.openapi.actionSystem.{LangDataKeys, PlatformDataKeys, DataContext}
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.template._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.PairProcessor
import lang.psi.api.expr.xml.ScXmlPairedTag
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.util.ArrayList
import com.intellij.openapi.util.TextRange

/**
 * User: Dmitry Naydanov
 * Date: 4/8/12
 */

class XmlRenameHandler extends RenameHandler {
  def isAvailableOnDataContext(dataContext: DataContext): Boolean = {
    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    if (editor == null || !editor.getSettings.isVariableInplaceRenameEnabled) return false

    val file = LangDataKeys.PSI_FILE.getData(dataContext)
    if (file == null) return false
    val element = file.findElementAt(editor.getCaretModel.getOffset)

    if (element == null) return false

    element.getParent match {
      case _: ScXmlPairedTag => true
      case _ => false
    }
  }

  def isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    if (!isRenaming(dataContext)) return
    val element = file.findElementAt(editor.getCaretModel.getOffset)

    if (element != null) invoke(project, Array(element), dataContext)
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    import scala.collection.JavaConversions._

    if (!isRenaming(dataContext) || elements == null || elements.length != 1) return

    val element = if (elements(0) == null || !elements(0).getParent.isInstanceOf[ScXmlPairedTag]) return else
      elements(0).getParent.asInstanceOf[ScXmlPairedTag]
    if (element.getMatchedTag == null || element.getTagNameElement == null || element.getMatchedTag.getTagNameElement == null) return

    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    val elementStartName = element.getTagName
    val rangeHighlighters = new ArrayList[RangeHighlighter]()
    val matchedRange = element.getMatchedTag.getTagNameElement.getTextRange

    def highlightMatched() {
      val colorsManager = EditorColorsManager.getInstance()
      val attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES)

      HighlightManager.getInstance(editor.getProject).addOccurrenceHighlight(editor, matchedRange.getStartOffset,
        matchedRange.getEndOffset, attributes, 0, rangeHighlighters, null)

      rangeHighlighters.foreach {a =>
        a.setGreedyToLeft(true)
        a.setGreedyToRight(true)
      }
    }

    def rename() {
      CommandProcessor.getInstance().executeCommand(project, new Runnable {
        def run() {
          extensions.inWriteAction {
            val offset = editor.getCaretModel.getOffset
            val template = buildTemplate()
            editor.getCaretModel.moveToOffset(element.getParent.getTextOffset)

            TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
              override def templateFinished(template: Template, brokenOff: Boolean) {
                templateCancelled(template)
              }

              override def templateCancelled(template: Template) {
                val highlightManager = HighlightManager.getInstance(project)
                rangeHighlighters.foreach{a => highlightManager.removeSegmentHighlighter(editor, a)}
              }
            },
              new PairProcessor[String, String] {
                def process(s: String, t: String): Boolean = !(t.length == 0 || t.charAt(t.length - 1) == ' ')
              })

            highlightMatched()
            editor.getCaretModel.moveToOffset(offset)
          }
        }
      }, RefactoringBundle.message("rename.title"), null)


    }

    def buildTemplate(): Template =  {
      val builder = new TemplateBuilderImpl(element.getParent)

      builder.replaceElement(element.getTagNameElement, "first", new EmptyExpression {
        override def calculateQuickResult(context: ExpressionContext): Result = new TextResult(Option(element.getTagName).getOrElse(elementStartName))
        override def calculateResult(context: ExpressionContext): Result = calculateQuickResult(context)
      }, true)
      builder.replaceElement(element.getMatchedTag.getTagNameElement, "second", "first", false)

      builder.buildInlineTemplate()
    }

    rename()
  }


}
