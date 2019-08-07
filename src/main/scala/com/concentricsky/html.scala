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
import scala.scalajs.js
import js.JSConverters._
import scala.reflect.macros.blackbox
import scala.language.experimental.macros
import scala.language.implicitConversions
import com.concentricsky.html.NodeBinding.Constant.TextBuilder
import com.thoughtworks.binding.bindable.BindableSeq
object html {
  @inline
  @tailrec
  private def removeAll(parent: Node): Unit = {
    val firstChild = parent.firstChild
    if (firstChild != null) {
      parent.removeChild(firstChild)
      removeAll(parent)
    }
  }
  final class NodeSeqMountPoint(parent: Node, childrenBinding: BindingSeq[Node])
      extends MultiMountPoint[Node](childrenBinding) {

    override protected def set(children: Seq[Node]): Unit = {
      removeAll(parent)
      for (child <- children) {
        if (child.parentNode != null) {
          throw new IllegalStateException(raw"""Cannot insert ${child.nodeName} twice!""")
        }
        parent.appendChild(child)
      }
    }

    override protected def splice(from: Int, that: collection.GenSeq[Node], replaced: Int): Unit = {
      @inline
      @tailrec
      def removeChildren(child: Node, n: Int): Node = {
        if (n == 0) {
          child
        } else {
          val nextSibling = child.nextSibling
          parent.removeChild(child)
          removeChildren(nextSibling, n - 1)
        }
      }

      val child = removeChildren(parent.childNodes(from), replaced)
      if (child == null) {
        for (newChild <- that) {
          if (newChild.parentNode != null) {
            throw new IllegalStateException(raw"""Cannot insert a ${newChild.nodeName} element twice!""")
          }
          parent.appendChild(newChild)
        }
      } else {
        for (newChild <- that) {
          if (newChild.parentNode != null) {
            throw new IllegalStateException(raw"""Cannot insert a ${newChild.nodeName} element twice!""")
          }
          parent.insertBefore(newChild, child)
        }
      }
    }

  }

  sealed trait NodeBindingSeq[+A] extends BindingSeq[A] {
    def value: Seq[A]
  }
  object NodeBindingSeq {

    object Constants {
      object Builder {
        implicit def interpolated(builder: Builder): Interpolated.Builder = {
          new Interpolated.Builder(new Interpolated(builder.childNodes))
        }
      }
      final class Builder private[concentricsky] (
          private[concentricsky] val childNodes: js.Array[Node] = new js.Array(0))
          extends AnyVal {

        def apply(childBuilder: NodeBinding.Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          childNodes += childBuilder.element
          this
        }

        def build() = {
          new Constants(childNodes)
        }

      }
    }

    final class Constants[+A] private[concentricsky] (underlying: js.Array[A]) extends NodeBindingSeq[A] {

      @inline
      def value: Seq[A] = underlying

      @inline
      override protected def removePatchedListener(listener: PatchedListener[A]): Unit = {}

      @inline
      override protected def addPatchedListener(listener: PatchedListener[A]): Unit = {}

    }
    object Interpolated {
      final class Builder private[concentricsky] (private[concentricsky] val underlying: Interpolated) extends AnyVal {
        // TODO: comments, CDATA, ets
        def apply(childBuilder: NodeBinding.Interpolated.ChildBuilder[Element]) = {
          childBuilder.flush()
          underlying.nodes += childBuilder.attributeBuilder.element
          underlying.mountPoints.push(childBuilder.attributeBuilder.mountPoints: _*)
          this
        }
        def apply(childBuilder: NodeBinding.Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          underlying.nodes += childBuilder.element
          this
        }
        def build() = underlying
      }
    }
    final class Interpolated private[concentricsky] (private[concentricsky] val nodes: js.Array[Node] = new js.Array(0),
                                                     private[concentricsky] val mountPoints: js.Array[Binding[Unit]] =
                                                       new js.Array(0))
        extends NodeBindingSeq[Node] {
      def value: Seq[Node] = nodes
      private var referenceCount = 0
      protected def addPatchedListener(listener: PatchedListener[Node]): Unit = {
        if (referenceCount == 0) {
          mountPoints.foreach(_.watch())
        }
        referenceCount += 1
      }
      protected def removePatchedListener(listener: PatchedListener[Node]): Unit = {
        referenceCount -= 1
        if (referenceCount == 0) {
          mountPoints.foreach(_.unwatch())
        }
      }
    }

  }

  sealed trait NodeBinding[+A] extends Binding[A] {
    val value: A
  }
  object NodeBinding {

    object Constant {

      final class TextBuilder private[concentricsky] (private[concentricsky] val value: String) extends AnyVal

      final class SelfCloseBuilder[A] private[concentricsky] (private[concentricsky] val element: A) extends AnyVal {
        def build() = Constant(element)
      }

      object ChildBuilder {
        implicit def interpolated[A <: Element](builder: ChildBuilder[A]): Interpolated.ChildBuilder[A] = {
          new Interpolated.ChildBuilder[A](new Interpolated.AttributeBuilder[A](builder.element),
                                           trailingNodes = builder.childNodes)
        }
      }

      /** The builder to a [[Constant]] from child nodes.
        * @example This [[Constant.ChildBuilder]] can be converted to a [[Interpolated.ChildBuilder]] automatically,
        * when a [[com.thoughtworks.binding.Binding]] is appended.
        * {{{
        * import org.scalajs.dom.document
        * import org.scalajs.dom.html.Div
        * import com.concentricsky.html.NodeBinding.Interpolated
        * import com.concentricsky.html.NodeBinding.Constant
        * import com.concentricsky.html.NodeBinding.Constant.TextBuilder
        *
        * new Constant.ChildBuilder(document.createElement("div").asInstanceOf[Div])(
        *   new TextBuilder("my-title"))(
        *   com.thoughtworks.binding.Binding(document.createElement("span"))
        * ) should be(an[Interpolated.ChildBuilder[_]])
        * }}}
        */
      final class ChildBuilder[+A <: Element] private[concentricsky] (
          private[concentricsky] val element: A,
          private[concentricsky] val childNodes: js.Array[Node] = new js.Array(0)) {
        private[concentricsky] def appendChildren(): Unit = {
          childNodes.foreach(element.appendChild)
        }
        def build() = {
          appendChildren()
          Constant(element)
        }
        def apply(childBuilder: ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          childNodes += childBuilder.element
          this
        }
        def apply(childBuilder: TextBuilder) = {
          childNodes += document.createTextNode(childBuilder.value)
          this
        }
        // TODO: comment, CDATA, etc...
      }

      sealed trait AttributeValueBuilder[A] {
        def apply(value: TextBuilder): Constant.AttributeBuilder[A] = macro BlackBoxMacros.textAttribute
        def apply(value: Binding[Any]): Interpolated.AttributeBuilder[A] = macro BlackBoxMacros.interpolatedAttribute
      }
      object AttributeBuilder {
        implicit def interpolated[A <: Element](builder: AttributeBuilder[A]): Interpolated.AttributeBuilder[A] = {
          new Interpolated.AttributeBuilder(builder.element)
        }
      }

      /** The builder to a [[Constant]] from attributes.
        * @example This [[Constant.AttributeBuilder]] can be converted to a [[Interpolated.AttributeBuilder]] automatically,
        * when setting an attribute with a [[com.thoughtworks.binding.Binding]] value.
        * {{{
        * import org.scalajs.dom.document
        * import org.scalajs.dom.html.Div
        * import com.concentricsky.html.NodeBinding.Interpolated
        * import com.concentricsky.html.NodeBinding.Constant
        * import com.concentricsky.html.NodeBinding.Constant.TextBuilder
        * new Constant.AttributeBuilder(document.createElement("div").asInstanceOf[Div])
        *   .title(new TextBuilder("my-title"))
        *   .className(com.thoughtworks.binding.Binding("my-title"))
        *   .should(be(an[Interpolated.AttributeBuilder[_]]))
        * }}}
        */
      final class AttributeBuilder[A <: Element] private[concentricsky] (private[concentricsky] val element: A)
          extends AnyVal
          with Dynamic {
        @inline final def selfClose() = new SelfCloseBuilder(element)
        @inline final def nodeList = new ChildBuilder(element, new js.Array(0))

        @compileTimeOnly("This function call should be elimited by macros")
        def applyDynamic(name: String): AttributeValueBuilder[A] = ???

        @inline
        def textAttribute(name: String, value: TextBuilder) = {
          element.setAttribute(name, value.value)
          this
        }
      }

    }
    final case class Constant[+A] private[concentricsky] (value: A) extends NodeBinding[A] {
      @inline
      protected def removeChangedListener(listener: ChangedListener[A]): Unit = {
        // Do nothing because this Constant never changes
      }

      @inline
      protected def addChangedListener(listener: ChangedListener[A]): Unit = {
        // Do nothing because this Constant never changes
      }
    }

    final class Interpolated[+A] private[concentricsky] (
        val value: A,
        private[concentricsky] val mountPoints: js.Array[Binding[Unit]])
        extends NodeBinding[A] {
      private var referenceCount = 0
      protected def addChangedListener(listener: ChangedListener[A]): Unit = {
        if (referenceCount == 0) {
          mountPoints.foreach(_.watch())
        }
        referenceCount += 1
      }
      protected def removeChangedListener(listener: ChangedListener[A]): Unit = {
        referenceCount -= 1
        if (referenceCount == 0) {
          mountPoints.foreach(_.unwatch())
        }
      }
    }

    object Interpolated {
      final class ChildBuilder[+A <: Element] private[concentricsky] (
          private[concentricsky] val attributeBuilder: AttributeBuilder[A],
          private var bindingSeqs: js.Array[Binding[BindingSeq[Node]]] = new js.Array(0),
          private var trailingNodes: js.Array[Node] = new js.Array(0)) {
        // TODO: comments, CDATA, ets
        def apply(childBuilder: Constant.TextBuilder) = {
          trailingNodes += document.createTextNode(childBuilder.value)
          this
        }
        def apply(childBuilder: Interpolated.ChildBuilder[Element]) = {
          childBuilder.flush()
          trailingNodes += childBuilder.attributeBuilder.element
          attributeBuilder.mountPoints.push(childBuilder.attributeBuilder.mountPoints: _*)
          this
        }
        def apply(childBuilder: Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          trailingNodes += childBuilder.element
          this
        }
        def apply[Child](child: Binding[Child])(implicit bindableSeq: BindableSeq.Lt[Child, Node]) = {
          mergeTrailingNodes()
          bindingSeqs += Binding.BindingInstances.map(child)(bindableSeq.toBindingSeq(_))
          this
        }
        private def mergeTrailingNodes(): Unit = {
          bindingSeqs += Binding.Constant(new NodeBindingSeq.Constants(trailingNodes))
          trailingNodes = new js.Array(0)
        }
        private[concentricsky] def flush(): Unit = {
          mergeTrailingNodes()
          attributeBuilder.mountPoints += new NodeSeqMountPoint(attributeBuilder.element,
                                                                Constants(bindingSeqs: _*).flatMapBinding(identity))
        }
        def build(): NodeBinding.Interpolated[A] = {
          flush()
          new NodeBinding.Interpolated(attributeBuilder.element, attributeBuilder.mountPoints)
        }
      }

      sealed trait AttributeValueBuilder[+A] {
        def apply(value: Constant.TextBuilder): AttributeBuilder[A] = macro BlackBoxMacros.textAttribute
        def apply(value: Binding[Any]): AttributeBuilder[A] = macro BlackBoxMacros.interpolatedAttribute
      }

      final class SelfCloseBuilder[+A <: Element] private[concentricsky] (
          private val attributeBuilder: AttributeBuilder[A])
          extends AnyVal {
        def build(): NodeBinding.Interpolated[A] = {
          new NodeBinding.Interpolated[A](attributeBuilder.element, attributeBuilder.mountPoints)
        }
      }

      final class AttributeBuilder[+A <: Element] private[concentricsky] (
          private[concentricsky] val element: A,
          private[concentricsky] val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
          extends Dynamic {

        @inline final def selfClose(): SelfCloseBuilder[A] = new SelfCloseBuilder(this)
        @inline final def nodeList: ChildBuilder[A] = new ChildBuilder[A](this)
        @compileTimeOnly("This function call should be elimited by macros")
        def applyDynamic(name: String): AttributeValueBuilder[A] = ???

        @inline
        def textAttribute[B](name: String, value: TextBuilder): Interpolated.AttributeBuilder[A] = {
          element.setAttribute(name, value.value)
          this
        }

        @inline
        final def interplatedAttribute[B](mountPoint: A => Binding[Unit]): this.type = {
          mountPoints += mountPoint(element)
          this
        }
      }

    }

  }

  object autoImports {

    object `http://www.w3.org/1999/xhtml` {
      @inline def div() = new NodeBinding.Constant.AttributeBuilder(document.createElement("div").asInstanceOf[Div])
      @inline def text(value: String) = new NodeBinding.Constant.TextBuilder(value)
      @inline def nodeList = new NodeBindingSeq.Constants.Builder
      @inline def interpolation = Binding
    }

  }
  private[concentricsky] class BlackBoxMacros(val c: blackbox.Context) {
    import c.universe._

    def textAttribute(value: Tree): Tree = {
      val q"$builder.applyDynamic($attributeName).apply($_)" = c.macroApplication
      q"""$builder.textAttribute($attributeName, $value)"""
    }
    def interpolatedAttribute(value: Tree): Tree = {
      val q"$builder.applyDynamic(${Literal(Constant(attributeName: String))}).apply($_)" = c.macroApplication
      val termName = TermName(attributeName)
      val functionName = TermName(c.freshName("assignAttribute"))
      val assigneeName = TermName(c.freshName("assignee"))
      val newValueName = TermName(c.freshName("newValue"))
      q"""$builder.interplatedAttribute{ $assigneeName: ${TypeTree()} =>
        _root_.com.thoughtworks.binding.Binding.apply[_root_.scala.Unit]{
          $assigneeName.$termName = $value.bind
        }
      }"""
    }

  }

  private[concentricsky] class WhiteBoxMacros(context: whitebox.Context) extends nameBasedXml.Macros(context) {
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
  *
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
  * val myDiv2 = <div title="xx" tabIndex={99999}><div>text</div><div tabIndex={99}></div><div></div>{myDiv.bind}</div>
  * myDiv2.watch()
  * myDiv2.value.nodeName should be("DIV")
  * myDiv2.value.title should be("xx")
  * myDiv2.value.tabIndex should be(99999)
  * myDiv2.value.firstChild.nodeName should be("DIV")
  * myDiv2.value.lastChild should be(myDiv.value)
  * myDiv2.value.innerHTML should be("""<div>text</div><div tabindex="99"></div><div></div><div tabindex="42" class="my-class"></div>""")
  * }}}
  * @example Element list of XHTML literals
  *
  * {{{
  * @html
  * val myDivs = <div></div><div title={"my title"}></div><div class="my-class"></div>
  * myDivs.watch()
  * import org.scalajs.dom.html.Div
  * inside(myDivs.value) {
  *   case Seq(div1, div2: Div, div3: Div) =>
  *     div1.nodeName should be("DIV")
  *     div2.title should be("my title")
  *     div3.className should be("my-class")
  * }
  * 
  * }}}
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class html extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro html.WhiteBoxMacros.macroTransform
}
