package org.jetbrains.plugins.scala.lang.resolve

import psi.api.statements.params.ScTypeParam
import psi.api.statements.ScFunction
import psi.api.toplevel.ScTyped
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.openapi.util.Key
import psi.types._

import _root_.scala.collection.Set
import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.mutable.ArrayBuffer

class ResolveProcessor(override val kinds: Set[ResolveTargets], val name: String) extends BaseProcessor(kinds)
{
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      candidatesSet += new ScalaResolveResult(named, getSubst(state))
      return false //todo: for locals it is ok to terminate the walkup, later need more elaborate check
    }
    return true
  }

  protected def nameAndKindMatch (named: PsiNamedElement, state: ResolveState) = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) named.getName else nameSet
    elName == name && kindMatches(named)
  }

  protected def getSubst(state: ResolveState) = {
    val subst = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  override def getHint[T](hintClass: Class[T]): T =
    if (hintClass == classOf[NameHint] && name != "") ScalaNameHint.asInstanceOf[T]
    else super.getHint(hintClass)

  object ScalaNameHint extends NameHint {
    def getName(state : ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}

class MethodResolveProcessor(override val name : String, args : Seq[ScType],
                             expected : Option[ScType]) extends ResolveProcessor(StdKinds.methodRef, name) {
  override def execute(element: PsiElement, state: ResolveState) : Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val s = getSubst(state)
      element match {
        case m : PsiMethod => {
          candidatesSet += new ScalaResolveResult(named, s.incl(inferMethodTypesArgs(m, s)))
          true
        }
        case _ => candidatesSet += new ScalaResolveResult(named, s); true
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult] : Array[T] = {
    val applicable = candidatesSet.filter {c =>
      val t = getType(c.element)
      val s = c.substitutor
      t match {
        case ScFunctionType(ret, params) => {
          args.equalsWith(params) {(a,p) => Compatibility.compatible(s.subst(p), a)} && (expected match {
            case None => true
            case Some(t) => Compatibility.compatible(s.subst(t), ret)
          })
        }
        case _ => false
      }
    }
    if (applicable.isEmpty) candidatesSet.toArray else {
      val buff = new ArrayBuffer[ScalaResolveResult]
      def existsBetter(r : ScalaResolveResult) : Boolean = {
        for (r1 <- applicable if r != r1) {
          if (isMoreSpecific(r1.element, r.element)) return false
        }
        true
      }
      for (r <- applicable if !existsBetter(r)) buff += r
      buff.toArray
    }
  }

  def inferMethodTypesArgs(m : PsiMethod, classSubst : ScSubstitutor) = ScSubstitutor.empty //todo

  def isMoreSpecific(e1 : PsiNamedElement, e2 : PsiNamedElement) = {
    val t1 = getType(e1)
    val t2 = getType(e2)
    e1 match {
      case _ : PsiMethod => {
        t1 match {
          case ScFunctionType(ret1, params1) => t2 match {
            case ScFunctionType(ret2, params2) => Compatibility.compatible(ret1, ret2) &&
                    params1.equalsWith(params2) {(p1, p2) => Compatibility.compatible(p2, p1)}
          }
        }
      }
      case _ => e2 match {
        case _ : PsiMethod => true
        case _ => Compatibility.compatible(t1, t2)
      }
    }
  }



  private def getType(e : PsiNamedElement) = e match {
    case fun : ScFunction => new ScFunctionType(fun.calcType, fun.parameters.map(_.calcType))
    case m : PsiMethod => new ScFunctionType(
    m.getReturnType match { case null => Unit; case rt => ScType.create(rt, m.getProject) },
    m.getParameterList.getParameters.map{p => ScType.create(p.getType, m.getProject)}
    )
    case typed : ScTyped => typed.calcType
    case _ => Nothing
  }
}

import ResolveTargets._

object StdKinds {
  val stableQualRef = HashSet.empty[ResolveTargets] + PACKAGE + OBJECT + VAL
  val stableQualOrClass = stableQualRef + CLASS
  val stableImportSelector = HashSet.empty[ResolveTargets] + OBJECT + VAL + VAR + METHOD + PACKAGE + CLASS
  val stableClass = HashSet.empty[ResolveTargets] + CLASS

  val refExprLastRef = HashSet.empty[ResolveTargets] + OBJECT + VAL + VAR + METHOD
  val refExprQualRef = refExprLastRef + PACKAGE

  val methodRef = HashSet.empty[ResolveTargets] + VAL + VAR + METHOD
}

object ResolverEnv {
  val nameKey: Key[String] = Key.create("ResolverEnv.nameKey")
}