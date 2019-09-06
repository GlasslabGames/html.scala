package org.lrng.binding

import scala.language.dynamics
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
object dynamicanyref {

  private[dynamicanyref] final class WhiteBoxMacros(val c: whitebox.Context) {
    import c.universe._
    def applyDynamic(arguments: Tree*): Tree = {
      val q"$self.${TermName(methodName)}(..$_)" = c.macroApplication
      q"$self.applyDynamic($methodName)(..$arguments)"
    }
  }

  trait AnyRefApplyDynamic extends Dynamic {
    def getClass(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def !=(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def ##(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def +(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def ->(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def ==(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def asInstanceOf(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def ensuring(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def eq(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def equals(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def formatted(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def hashCode(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def isInstanceOf(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def ne(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def notify(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def notifyAll(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def synchronized(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def toString(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def wait(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
    def →(arguments: Any*): Any = macro WhiteBoxMacros.applyDynamic
  }

  trait AnyRefSelectDynamic[Field] extends Dynamic {
    def selectDynamic(fieldName: String): Field
    @inline def getClass(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("getClass")
    @inline def != = selectDynamic("!=")
    @inline def ##(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) = selectDynamic("##")
    @inline def + = selectDynamic("+")
    @inline def -> = selectDynamic("->")
    @inline def == = selectDynamic("==")
    @inline def asInstanceOf(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("asInstanceOf")
    @inline def ensuring = selectDynamic("ensuring")
    @inline def eq = selectDynamic("eq")
    @inline def equals = selectDynamic("equals")
    @inline def formatted = selectDynamic("formatted")
    @inline def hashCode(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("hashCode")
    @inline def isInstanceOf(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("isInstanceOf")
    @inline def ne = selectDynamic("ne")
    @inline def notify(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) = selectDynamic("notify")
    @inline def notifyAll(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("notifyAll")
    @inline def synchronized = selectDynamic("synchronized")
    @inline def toString(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
      selectDynamic("toString")
    @inline def wait(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) = selectDynamic("wait")
    @inline def → = selectDynamic("→")
  }

}
