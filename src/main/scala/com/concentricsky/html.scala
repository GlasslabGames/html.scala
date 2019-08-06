package com.concentricsky
import scala.annotation._
import scala.language.dynamics
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import com.thoughtworks.binding._, Binding._
import org.scalajs.dom.Node
import org.scalajs.dom.Element
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.html.Div
import org.scalajs.dom.html.Div
import scala.scalajs.js
import js.JSConverters._
import scalatags.JsDom
import scalatags.jsdom
import scalatags.JsDom.TypedTag
import scalatags.generic.Namespace

object html {
  sealed trait NodeBinding[A] extends Binding[A] with NodeBinding.Interpolated.Child[Node] {
    val value: A
  }
  object NodeBinding {
    trait Builder extends Any with Dynamic {
      type Self
      protected def self: Self

      def selfClose(): Self = self
      def childNodes: Self = self
    }

    object Constant {

      trait Builder[A] extends Any with NodeBinding.Builder {

        protected def node: A
        def apply[B](child: Constant.Builder[B]) = {
          // TODO:
          self
        }
        def apply[B](child: Interpolated.Builder[B]): Interpolated.Builder[A] = {
          ??? // TODO:
        }
        def apply[B](child: Binding[B]): Interpolated.Builder[A] = {
          ??? // TODO:
        }

        def render() = Constant(node)
      }

    }
    final case class Constant[A](value: A) extends NodeBinding[A] {
      @inline
      protected def removeChangedListener(listener: ChangedListener[A]): Unit = {
        // Do nothing because this Constant never changes
      }

      @inline
      protected def addChangedListener(listener: ChangedListener[A]): Unit = {
        // Do nothing because this Constant never changes
      }
    }

    object Interpolated {

      trait Builder[A] extends NodeBinding.Interpolated[A] with NodeBinding.Builder {
        type Self = this.type
        def self: this.type = this
        
        def apply[B](child: Constant.Builder[B]) = {
          // TODO:
          this
        }
        def apply[B](child: Interpolated.Builder[B]) = {
          // TODO:
          this
        }
        def apply[B](child: Binding[B]) = {
          // TODO:
          this
        }

        def render() = this
      }

      sealed trait Child[-Parent]
      object Child {

        trait Property[-Parent, Value] extends Child[Parent] {
          val binding: Binding[Value]
          def setProperty(parent: Parent, value: Value): Unit
          def getProperty(parent: Parent): Value
        }
      }
    }

    trait Interpolated[A] extends NodeBinding[A] {
      val value: A
      protected val children: js.Array[Interpolated.Child[A]]
      private var referenceCount = 0
      protected def addChangedListener(listener: ChangedListener[A]): Unit = {
        if (referenceCount == 0) {
          // TODO:
          for (child <- children) {
            child match {
              case p: NodeBinding.Interpolated.Child.Property[A, _] =>
                // TODO
              case _ =>
                // TODO
              }
          }
        }
        referenceCount += 1
      }
      protected def removeChangedListener(listener: ChangedListener[A]): Unit = {
        referenceCount -= 1
        if (referenceCount == 0) {
          // TODO:
          //mountPoints.foreach(_.unwatch())
        }
      }
    }
  }

  object autoImports {

    object `http://www.w3.org/1999/xhtml` {

      case class text(value: String) extends AnyVal
      object div {

        final class Interpolated(val value: Div, protected val children: js.Array[NodeBinding.Interpolated.Child[Div]])
            extends NodeBinding.Interpolated.Builder[Div] {

          @inline
          def applyDynamic(name: String)(attributeValue: text): this.type = {
            value.setAttribute(name, attributeValue.value)
            this
          }

          @inline
          def tabIndex(value: Binding[Int]) = {
            children.push(new TabIndexProperty(value))
          }

          @inline
          def tabIndex(attributeValue: text) = {
            value.setAttribute("tabIndex", attributeValue.value)
            this
          }
        }

      }
      class TabIndexProperty(val binding: Binding[Int])
          extends NodeBinding.Interpolated.Child.Property[HTMLElement, Int] {
        def getProperty(parent: HTMLElement): Int = parent.tabIndex
        def setProperty(parent: HTMLElement, value: Int): Unit = parent.tabIndex = value
      }
      case class div(node: Div = document.createElement("div").asInstanceOf[Div]) extends AnyVal with NodeBinding.Constant.Builder[Div] {
        type Self = div
        def self = this

        @inline
        def applyDynamic(name: String)(attributeValue: text): div = {
          node.setAttribute(name, attributeValue.value)
          this
        }

        @inline
        def tabIndex(value: Binding[Int]) = {
          new div.Interpolated(node, js.Array(new TabIndexProperty(value)))
        }

        @inline
        def tabIndex(attributeValue: text) = {
          node.setAttribute("tabIndex", attributeValue.value)
          this
        }

      }

      @inline def interpolation = Binding
    }

  }

  class Macros(context: whitebox.Context) extends nameBasedXml.Macros(context) {
    import c.universe._

    override protected def transformBody(tree: Tree): Tree = q"""
      import _root_.com.concentricsky.html.autoImports.{
        != => _,
        ## => _,
        == => _,
        eq => _,
        equals => _,
        getClass => _,
        hashCode => _,
        ne => _,
        notify => _,
        notifyAll => _,
        synchronized => _,
        toString => _,
        wait => _,
        _
      }
      ${new NameBasedXmlTransformer(q"`http://www.w3.org/1999/xhtml`").transform(tree)}
    """
  }
}

/** An annotation to convert XHTML literals to data-bindable DOM nodes.
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  * @example XHTML literals with text attributes
  * {{{
  * @html
  * val myDiv = <div class="my-class" tabIndex="42"/>
  * myDiv.value.nodeName should be("DIV")
  * myDiv.value.className should be("my-class")
  * myDiv.value.tabIndex should be(42)
  * }}}
  * 
  * Nested XHTML literals with interpolation:
  * 
  * {{{
  * @html
  * val myDiv2 = <div title="xx" tabIndex={99}><div></div><div tabIndex={99}></div><div></div>{myDiv.bind}</div>
  * myDiv2.watch()
  * myDiv2.value.nodeName should be("DIV")
  * myDiv2.value.title should be("xx")
  * // FIXME: 
  * // myDiv2.value.tabIndex should be(99)
  * }}}
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class html extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro html.Macros.macroTransform
}
