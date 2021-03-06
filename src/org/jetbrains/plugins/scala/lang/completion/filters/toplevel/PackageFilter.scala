package org.jetbrains.plugins.scala
package lang
package completion
package filters.toplevel

import psi.api.ScalaFile
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser._

/**
* @author Alexander Podkhalyuzin
* Date: 21.05.2008
*/

class PackageFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    var leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      leaf.getContainingFile match {
        case scalaScriptFile: ScalaFile if scalaScriptFile.isScriptFile() => leaf = leaf.getParent
        case _ => 
      }
    }
    
    if (leaf != null) {
      val parent = leaf.getParent
      if (parent.isInstanceOf[ScalaFile] || parent.isInstanceOf[ScPackaging]) {
        if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1) return false
        else {
          var node = leaf.getPrevSibling
          if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
          node match {
            case x: PsiErrorElement => {
              val s = ErrMsg("wrong.top.statment.declaration")
              x.getErrorDescription match {
                case `s` => return  true
                case _ => return  false
              }
            }
            case _ => return  true
          }
        }
      }
    }
    
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "'package' keyword filter"
}