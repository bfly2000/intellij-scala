import scala.collection.mutable.LinkedList
import scala.Nil
import scala.{List => scalaList}
import scala.collection.mutable.ArrayBuffer

package org.jetbrains.plugins.scala.structure {

import com.intellij.navigation.NavigationItem
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi._

import javax.swing._;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.plugins.scala.lang.psi.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:50:40
 */

class ScalaStructureViewElement (element : PsiElement) requires ScalaStructureViewElement extends StructureViewTreeElement {
  private val myElement : PsiElement = element

  override def getValue() : PsiElement = myElement

  override def navigate(requestFocus : Boolean) : Unit = (myElement.asInstanceOf[NavigationItem]).navigate(requestFocus)

  override def canNavigate() : Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigate()

  override def canNavigateToSource() : Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigateToSource()

  override def getChildren() : Array[TreeElement] = {
    var childrenElements: ArrayBuffer[ScalaPsiElement] = new ArrayBuffer[ScalaPsiElement]();
    var stmtStructureViewElements : ArrayBuffer[ScalaStructureViewElement] = new ArrayBuffer[ScalaStructureViewElement]();
    val result : ArrayBuffer[TreeElement] = new ArrayBuffer[TreeElement]()

    //todo: add inherited methods
    myElement match {
      case file : ScalaFile => childrenElements ++= file.getPackaging; childrenElements ++= file.childrenOfType[ScTmplDef](ScalaElementTypes.TMPL_DEF_BIT_SET)
      case packaging : ScPackaging if (packaging.getTmplDefs != null) => childrenElements ++= packaging.getTmplDefs

      case topDef : ScTmplDef => {
        if (topDef.getTmplDefs != null) childrenElements ++= topDef.getTmplDefs

        val templateStmts = topDef.getTemplateStatements

        if (templateStmts != null) stmtStructureViewElements ++= {
          val bigIter = for (val stmt <- templateStmts; stmt != null) yield elementAsDisjunctStructureViewElements(stmt)

          bigIter.flatMap(x => x)
//          templateStmts
        }
      }
      case _ => {}
    }

    result ++= (for ( val i <- Array.range(0, childrenElements.length))
                   yield (new ScalaStructureViewElement(childrenElements.apply(i))).asInstanceOf[TreeElement])

    result ++= stmtStructureViewElements

    val ar : Array[TreeElement] = new Array(result.length)
    result.copyToArray (ar, 0)
    ar
  }

  override def getPresentation() : ItemPresentation = {
        new ItemPresentation() {
             def getPresentableText() : String = {
               myElement match {
                 case file : ScalaFile => file.getVirtualFile.getName
                 case packaging : ScPackaging => packaging.getFullPackageName
                 case topDef : ScTmplDef => topDef.getName

                 case templateStmt : ScTemplateStatement => {
                    val name = templateStmt.getShortName

                    val typeParamClause = templateStmt match {
                      case function : ScFunction => function.getTypeParamClause
                      case typeDefinition : ScTypeDefinition => typeDefinition.getTypeParamClause
                      case _ => null
                    }

                    val paramTypesString = templateStmt match {
                      case function : ScFunction => {
                        val allParamClauses = function.paramClauses

                        def paramTypeAsString (param : ScParam) =
                          if (param.paramType != null ) param.paramType().getText
                          else ""

                        def paramClauseAsString (paramClause : ScParamClause) : String = {
                            if (paramClause != null)
                              if (paramClause.params != null)
                                paramClause.params.map[String](paramTypeAsString).mkString("(", ",", ")")
                              else ""
                            else ""
                          }

                        if (allParamClauses != null) {
                          val map = allParamClauses.map[String](paramClauseAsString)
                          map.mkString("", "", "")
                        } else ""
                      }

                      case _ => null
                    }

                    val exprPresentage : ScalaPsiElement = templateStmt match {
                      case functionDef : ScFunctionDefinition => null
                      case definition : ScDefinition => definition.getExpr
                      case _ => null
                    }

                    val stmtType = templateStmt.getType

                    var result = name
                    if (typeParamClause != null) result = result + typeParamClause.getText
                    if (paramTypesString != null) result = result + paramTypesString
                    if (stmtType != null) result = result + ":" + stmtType.getText
//                    if (exprPresentage != null) result = result + " = " + exprPresentage.getText

                    result
                 }

                 case _ => ""
               }
            }

             override def getTextAttributesKey() : TextAttributesKey = null

             override def getLocationString() : String  = null

             override def getIcon(open : Boolean) : Icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
        }
    }


    private def elementAsDisjunctStructureViewElements (element : ScTemplateStatement) : Iterable[ScalaStructureViewElement] = {
      val psiElement = element
      if (!psiElement.isManyDeclarations) return Array(new ScalaStructureViewElement(psiElement))

      val theNames = psiElement.names

      for (val name <- theNames) yield {
        new ScalaStructureViewElement(psiElement) {
          override def getPresentation() : ItemPresentation = {
            new ItemPresentation() {
              override def getPresentableText() : String = {
                   name.getText
                }

             override def getTextAttributesKey() : TextAttributesKey = null
             override def getLocationString() : String  = null
             override def getIcon(open : Boolean) : Icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
            }
          }
        }
      }
    }

 }

}