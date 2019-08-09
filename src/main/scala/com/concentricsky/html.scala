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
import com.thoughtworks.binding.bindable.Bindable
import scalaz.Monad
object html {

  def mount(parent: Node, children: BindingSeq[Node]): Binding[Unit] = {
    new NodeSeqMountPoint(parent, children)
  }

  def render[Children](parent: Node, children: Children)(implicit bindableSeq: BindableSeq.Lt[Children, Node]) = {
    mount(parent, bindableSeq.toBindingSeq(children)).watch()
  }

  @inline
  @tailrec
  private def removeAll(parent: Node): Unit = {
    val firstChild = parent.firstChild
    if (firstChild != null) {
      parent.removeChild(firstChild)
      removeAll(parent)
    }
  }
  private final class NodeSeqMountPoint(parent: Node, childrenBinding: BindingSeq[Node])
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

  type NodeBindingSeq[+A] = BindingSeq[A] {
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

        def withChild(childBuilder: NodeBinding.Constant.AttributeBuilder[Element]) = {
          childNodes += childBuilder.element
          this
        }

        def withChild(childBuilder: NodeBinding.Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          childNodes += childBuilder.element
          this
        }

        def build() = {
          Binding.Constants(childNodes: _*)
        }

      }
    }

    type Constants[+A] = Binding.Constants[A]

    object Interpolated {
      final class Builder private[concentricsky] (private[concentricsky] val underlying: Interpolated) extends AnyVal {
        def withChild(childBuilder: NodeBinding.Interpolated.ChildBuilder[Element]) = {
          childBuilder.flush()
          underlying.nodes += childBuilder.attributeBuilder.element
          underlying.mountPoints.push(childBuilder.attributeBuilder.mountPoints: _*)
          this
        }
        def withChild(childBuilder: NodeBinding.Interpolated.AttributeBuilder[Element]) = {
          underlying.nodes += childBuilder.element
          underlying.mountPoints.push(childBuilder.mountPoints: _*)
          this
        }
        def withChild(childBuilder: NodeBinding.Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          underlying.nodes += childBuilder.element
          this
        }
        @inline
        def withChild(childBuilder: NodeBinding.Constant.AttributeBuilder[Element]) = {
          underlying.nodes += childBuilder.element
          this
        }
        def build() = underlying
      }
    }
    final class Interpolated private[concentricsky] (private[concentricsky] val nodes: js.Array[Node] = new js.Array(0),
                                                     private[concentricsky] val mountPoints: js.Array[Binding[Unit]] =
                                                       new js.Array(0))
        extends BindingSeq[Node] {
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

  type NodeBinding[+A] = Binding[A] {
    val value: A
  }
  object NodeBinding {

    object Constant {
      final class Builder[+A] private[concentricsky] (private[concentricsky] val node: A) extends AnyVal {
        def build() = Binding.Constant(node)
      }

      final class TextBuilder private[concentricsky] (private[concentricsky] val data: String) extends AnyVal

      final class SelfCloseBuilder[+A] private[concentricsky] (private[concentricsky] val element: A) extends AnyVal {
        def build() = Binding.Constant(element)
      }

      object ChildBuilder {
        implicit def interpolated[A <: Element](builder: ChildBuilder[A]): Interpolated.ChildBuilder[A] = {
          new Interpolated.ChildBuilder[A](new Interpolated.AttributeBuilder[A](builder.element),
                                           trailingNodes = builder.childNodes)
        }
      }

      /** The builder to a [[Constant]] from child nodes.
        * @example This [[Constant.ChildBuilder]] can be converted to a [[Interpolated.ChildBuilder]] automatically,
        * when a [[Binding]] is appended.
        * {{{
        * import org.scalajs.dom.document
        * import org.scalajs.dom.html.Div
        * import com.thoughtworks.binding.Binding
        * import com.concentricsky.html.NodeBinding.Interpolated
        * import com.concentricsky.html.NodeBinding.Constant
        * import com.concentricsky.html.NodeBinding.Constant.TextBuilder
        *
        * {
        *   new Constant.ChildBuilder(document.createElement("div").asInstanceOf[Div])
        *    .withChild(new TextBuilder("my-title"))
        *    .withChild(Binding(document.createElement("span")))
        * } should be(an[Interpolated.ChildBuilder[_]])
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
          Binding.Constant(element)
        }
        def withChild(childBuilder: ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          childNodes += childBuilder.element
          this
        }
        def withChild(childBuilder: AttributeBuilder[Element]) = {
          childNodes += childBuilder.element
          this
        }
        def withChild(childBuilder: TextBuilder) = {
          childNodes += document.createTextNode(childBuilder.data)
          this
        }
        def withChild(childBuilder: Builder[Node]) = {
          childNodes += childBuilder.node
          this
        }
      }

      sealed trait AttributeNameBuilder[+A] extends Dynamic {
        @compileTimeOnly("This function call should be elimited by macros")
        def applyDynamic(name: String): AttributeValueBuilder = ???
        sealed trait AttributeValueBuilder {
          def apply(value: String): Constant.AttributeBuilder[A] = macro BlackBoxMacros.textAttribute
          def apply(value: Binding[Any]): Interpolated.AttributeBuilder[A] = macro BlackBoxMacros.interpolatedAttribute
        }
      }

      object AttributeBuilder {
        @inline
        implicit def interpolated[A <: Element](builder: AttributeBuilder[A]): Interpolated.AttributeBuilder[A] = {
          new Interpolated.AttributeBuilder(builder.element)
        }
      }

      /** The builder to a [[Constant]] from attributes.
        * @example This [[Constant.AttributeBuilder]] can be converted to a [[Interpolated.AttributeBuilder]] automatically,
        * when setting an attribute with a [[Binding]] value.
        * {{{
        * import org.scalajs.dom.document
        * import org.scalajs.dom.html.Div
        * import com.concentricsky.html.NodeBinding.Interpolated
        * import com.concentricsky.html.NodeBinding.Constant
        * import com.concentricsky.html.NodeBinding.Constant.TextBuilder
        * new Constant.AttributeBuilder(document.createElement("div").asInstanceOf[Div])
        *   .withAttribute.title("my-title")
        *   .withAttribute.className(com.thoughtworks.binding.Binding("my-title"))
        *   .should(be(an[Interpolated.AttributeBuilder[_]]))
        * }}}
        */
      final class AttributeBuilder[+A <: Element] private[concentricsky] (private[concentricsky] val element: A)
          extends AnyVal
          with Dynamic {
        @inline final def withoutNodeList = new SelfCloseBuilder(element)
        @inline final def withNodeList = new ChildBuilder(element, new js.Array(0))

        @compileTimeOnly("This function call should be elimited by macros")
        def withAttribute: AttributeNameBuilder[A] = ???

        @inline
        def textAttribute(name: String, value: String) = {
          element.setAttribute(name, value)
          this
        }
      }

    }
    type Constant[+A] = Binding.Constant[A]

    final class Interpolated[+A] private[concentricsky] (
        val value: A,
        private[concentricsky] val mountPoints: js.Array[Binding[Unit]])
        extends Binding[A] {
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
      private[Interpolated] trait LowPriorityChildBuilder[+A <: Element] { this: ChildBuilder[A] =>

        def withChild[Child, B](child: Binding[Child])(implicit bindableSeq: BindableSeq.Aux[Child, B],
                                                       bindable: Bindable.Lt[B, Node]) = {
          mergeTrailingNodes()
          bindingSeqs += Binding.BindingInstances.map(child) { a =>
            bindableSeq.toBindingSeq(a).mapBinding(bindable.toBinding(_))
          }
          this
        }
      }
      final class ChildBuilder[+A <: Element] private[concentricsky] (
          private[concentricsky] val attributeBuilder: AttributeBuilder[A],
          private[concentricsky] var bindingSeqs: js.Array[Binding[BindingSeq[Node]]] = new js.Array(0),
          private[concentricsky] var trailingNodes: js.Array[Node] = new js.Array(0))
          extends LowPriorityChildBuilder[A] {
        def withChild(childBuilder: Constant.TextBuilder) = {
          trailingNodes += document.createTextNode(childBuilder.data)
          this
        }
        def withChild(childBuilder: Constant.AttributeBuilder[Element]) = {
          trailingNodes += childBuilder.element
          this
        }
        def withChild(childBuilder: Constant.Builder[Node]) = {
          trailingNodes += childBuilder.node
          this
        }
        def withChild(childBuilder: Interpolated.AttributeBuilder[Element]) = {
          trailingNodes += childBuilder.element
          attributeBuilder.mountPoints.push(childBuilder.mountPoints: _*)
          this
        }
        def withChild(childBuilder: Interpolated.ChildBuilder[Element]) = {
          childBuilder.flush()
          trailingNodes += childBuilder.attributeBuilder.element
          attributeBuilder.mountPoints.push(childBuilder.attributeBuilder.mountPoints: _*)
          this
        }
        def withChild(childBuilder: Constant.ChildBuilder[Element]) = {
          childBuilder.appendChildren()
          trailingNodes += childBuilder.element
          this
        }
        def withChild(child: Binding[String]) = {
          val textNode = document.createTextNode("")
          trailingNodes += textNode
          attributeBuilder.mountPoints += Binding.BindingInstances.map(child) {
            textNode.data = _
          }
          this
        }
        def withChild[Child](child: Binding[Child])(implicit bindableSeq: BindableSeq.Lt[Child, Node]) = {
          mergeTrailingNodes()
          bindingSeqs += Binding.BindingInstances.map(child)(bindableSeq.toBindingSeq(_))
          this
        }
        private[concentricsky] def mergeTrailingNodes(): Unit = {
          bindingSeqs += Binding.Constant(Binding.Constants(trailingNodes: _*))
          trailingNodes = new js.Array(0)
        }
        private[concentricsky] def flush(): Unit = {
          mergeTrailingNodes()
          attributeBuilder.mountPoints += new NodeSeqMountPoint(attributeBuilder.element,
                                                                Constants(bindingSeqs: _*).flatMapBinding(identity))
        }
        def build() = {
          flush()
          new NodeBinding.Interpolated(attributeBuilder.element, attributeBuilder.mountPoints)
        }
      }
      sealed trait AttributeNameBuilder[+A] extends Dynamic {
        @compileTimeOnly("This function call should be elimited by macros")
        def applyDynamic(name: String): AttributeValueBuilder = ???
        sealed trait AttributeValueBuilder {
          def apply(value: String): AttributeBuilder[A] = macro BlackBoxMacros.textAttribute
          def apply(value: Binding[Any]): AttributeBuilder[A] = macro BlackBoxMacros.interpolatedAttribute
        }
      }

      final class SelfCloseBuilder[+A <: Element] private[concentricsky] (
          private val attributeBuilder: AttributeBuilder[A])
          extends AnyVal {
        def build() = {
          new NodeBinding.Interpolated[A](attributeBuilder.element, attributeBuilder.mountPoints)
        }
      }

      final class AttributeBuilder[+A <: Element] private[concentricsky] (
          private[concentricsky] val element: A,
          private[concentricsky] val mountPoints: js.Array[Binding[Unit]] = new js.Array(0)) {

        @inline final def withoutNodeList: SelfCloseBuilder[A] = new SelfCloseBuilder(this)
        @inline final def withNodeList: ChildBuilder[A] = new ChildBuilder[A](this)

        @compileTimeOnly("This function call should be elimited by macros")
        def withAttribute: AttributeNameBuilder[A] = ???

        @inline
        def textAttribute[B](name: String, value: String): Interpolated.AttributeBuilder[A] = {
          element.setAttribute(name, value)
          this
        }

        @inline
        final def interpolatedAttribute[B](mountPoint: A => Binding[Unit]): this.type = {
          mountPoints += mountPoint(element)
          this
        }
      }

    }

  }

  private[html] trait LowPriorityAutoImports {
    // TODO: move to https://github.com/ThoughtWorksInc/bindable.scala
    implicit def bindableBindableSeq[From, BindableValue, Value0](
        implicit
        bindableSeq: BindableSeq.Aux[From, BindableValue],
        bindable: Bindable.Aux[BindableValue, Value0]): BindableSeq.Aux[From, Value0] =
      new BindableSeq[From] {
        type Value = Value0
        def toBindingSeq(from: From): BindingSeq[Value] = {
          bindableSeq.toBindingSeq(from).mapBinding(bindable.toBinding(_))
        }
      }
  }

  private[concentricsky] object elementTypes {

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLTimeElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDataElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLPictureElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLOutputElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLMeterElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDetailsElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDialogElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLTemplateElement = HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLSlotElement = HTMLElement

  }
  object autoImports extends LowPriorityAutoImports {

    // TODO: move to https://github.com/ThoughtWorksInc/bindable.scala
    implicit def bindingBindingSeqBindableSeq[BindableValue, Value0](
        implicit bindable: Bindable.Aux[BindableValue, Value0]): BindableSeq.Aux[BindingSeq[BindableValue], Value0] =
      new BindableSeq[BindingSeq[BindableValue]] {
        type Value = Value0
        def toBindingSeq(from: BindingSeq[BindableValue]): BindingSeq[Value] = {
          from.mapBinding(bindable.toBinding(_))
        }
      }

    object `http://www.w3.org/1999/xhtml` {
      @inline def elements = ElementBuilders
      @inline def entities = EntityBuilders
      @inline def cdata(data: String) = new NodeBinding.Constant.Builder(document.createCDATASection(data))
      @inline def text(data: String) = new NodeBinding.Constant.TextBuilder(data)
      @inline def comment(data: String) = new NodeBinding.Constant.Builder(document.createComment(data))
      object processInstructions extends Dynamic {
        @inline
        def applyDynamic(target: String)(data: String) =
          new NodeBinding.Constant.Builder(document.createProcessingInstruction(target, data))
      }
      @inline def withNodeList = new NodeBindingSeq.Constants.Builder
      @inline def interpolation = Binding
    }

  }
  private[concentricsky] class BlackBoxMacros(val c: blackbox.Context) {
    import c.universe._

    def textAttribute(value: Tree): Tree = {
      val q"$builder.withAttribute.applyDynamic($attributeName).apply($_)" = c.macroApplication
      q"""$builder.textAttribute($attributeName, $value)"""
    }
    def interpolatedAttribute(value: Tree): Tree = {
      val q"$builder.withAttribute.applyDynamic(${Literal(Constant(attributeName: String))}).apply($_)" =
        c.macroApplication
      val termName = TermName(attributeName)
      val functionName = TermName(c.freshName("assignAttribute"))
      val assigneeName = TermName(c.freshName("assignee"))
      val newValueName = TermName(c.freshName("newValue"))
      q"""$builder.interpolatedAttribute{ $assigneeName: ${TypeTree()} =>
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
  * val myDiv = <div class="my-class" tabIndex="42"></div>
  * myDiv.value.nodeName should be("DIV")
  * myDiv.value.className should be("my-class")
  * myDiv.value.tabIndex should be(42)
  * }}}
  *
  * Nested XHTML literals with interpolation:
  *
  * {{{
  * @html
  * val myDiv2 = <div title="my title" tabIndex={99999}><div>text</div><span tabIndex={99}></span><div></div>{myDiv.bind}</div>
  * myDiv2.watch()
  * myDiv2.value.outerHTML should be("""<div tabindex="99999" title="my title"><div>text</div><span tabindex="99"></span><div></div><div tabindex="42" class="my-class"></div></div>""")
  * }}}
  * @example Element list of XHTML literals
  *
  * {{{
  * @html
  * val myDivs = <div></div><div title={"my title"} class=""></div><div class="my-class"></div>
  * myDivs.watch()
  * import org.scalajs.dom.html.Div
  * inside(myDivs.value) {
  *   case Seq(div1: Div, div2: Div, div3: Div) =>
  *     div1.nodeName should be("DIV")
  *     div1.hasAttribute("class") should be(false)
  *     div1.className should be("")
  *     div2.title should be("my title")
  *     div2.hasAttribute("class") should be(true)
  *     div2.className should be("")
  *     div3.className should be("my-class")
  * }
  * }}}
  *
  * @example Text interpolation in an element
  * {{{
  * @html val monadicDiv = <div>{"text"}</div>
  * monadicDiv.watch()
  * assert(monadicDiv.value.outerHTML == "<div>text</div>")
  * }}}
  *
  * @example Changing text
  * {{{
  * import com.thoughtworks.binding.Binding.Var
  * val v0 = Var("original text")
  * @html val monadicDiv = <div> <span> {v0.bind} </span> </div>
  * monadicDiv.watch()
  * assert(monadicDiv.value.outerHTML == "<div> <span> original text </span> </div>")
  * v0.value = "changed"
  * assert(monadicDiv.value.outerHTML == "<div> <span> changed </span> </div>")
  * }}}
  *
  * @example `for` / `yield` expressions in XHTML interpolation
  * {{{
  * import com.thoughtworks.binding.Binding.Vars
  * val v0 = Vars("original text 0","original text 1")
  * @html val monadicDiv = <div> <span> { for (s <- v0) yield <b>{s}</b> } </span> </div>
  * monadicDiv.watch()
  * val div = monadicDiv.value
  * assert(monadicDiv.value.outerHTML == "<div> <span> <b>original text 0</b><b>original text 1</b> </span> </div>")
  * v0.value.prepend("prepended")
  * assert(div eq monadicDiv.value)
  * assert(monadicDiv.value.outerHTML == "<div> <span> <b>prepended</b><b>original text 0</b><b>original text 1</b> </span> </div>")
  * v0.value.remove(1)
  * assert(div eq monadicDiv.value)
  * assert(monadicDiv.value.outerHTML == "<div> <span> <b>prepended</b><b>original text 1</b> </span> </div>")
  * }}}
  *
  * @example `for` / `yield` / `if` expressions in XHTML interpolation
  * {{{
  * import com.thoughtworks.binding.Binding
  * import com.thoughtworks.binding.Binding.Var
  * import com.thoughtworks.binding.Binding.Vars
  * final case class User(firstName: Var[String], lastName: Var[String], age: Var[Int])
  * val filterPattern = Var("")
  * val users = Vars(
  *   User(Var("Steve"), Var("Jobs"), Var(10)),
  *   User(Var("Tim"), Var("Cook"), Var(12)),
  *   User(Var("Jeff"), Var("Lauren"), Var(13))
  * )
  * def shouldShow(user: User): Binding[Boolean] = Binding {
  *   val pattern = filterPattern.bind
  *   if (pattern == "") {
  *     true
  *   } else if (user.firstName.bind.toLowerCase.contains(pattern)) {
  *     true
  *   } else if (user.lastName.bind.toLowerCase.contains(pattern)) {
  *     true
  *   } else {
  *     false
  *   }
  * }
  *
  * @html
  * def tbodyBinding = {
  *   <tbody>{
  *     for {
  *       user <- users
  *       if shouldShow(user).bind
  *     } yield <tr><td>{user.firstName.bind}</td><td>{user.lastName.bind}</td><td>{user.age.bind.toString}</td></tr>
  *   }</tbody>
  * }
  *
  * @html
  * val tableBinding = {
  *   <table title="My Tooltip" class="my-table"><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead>{tbodyBinding.bind}</table>
  * }
  * tableBinding.watch()
  * assert(tableBinding.value.outerHTML == """<table class="my-table" title="My Tooltip"><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr><tr><td>Jeff</td><td>Lauren</td><td>13</td></tr></tbody></table>""")
  * filterPattern.value = "o"
  * assert(tableBinding.value.outerHTML == """<table class="my-table" title="My Tooltip"><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr></tbody></table>""")
  * }}}
  *
  * @example Changing attribute values
  * {{{
  * import com.thoughtworks.binding.Binding.Var
  * val id = Var("oldId")
  * @html val hr = <hr id={id.bind}/>
  * hr.watch()
  * assert(hr.value.outerHTML == """<hr id="oldId">""")
  * id.value = "newId"
  * assert(hr.value.outerHTML == """<hr id="newId">""")
  * }}}
  *
  * @example A child node must not be inserted more than once
  * {{{
  * an[IllegalStateException] should be thrownBy {
  *   @html
  *   val child = <hr/>
  *   @html
  *   val parent = <p><span>{child.bind}</span><span>{child.bind}</span></p>
  *   parent.watch()
  * }
  * }}}
  *
  *
  * @example Seq in DOM
  * {{{
  * import org.scalajs.dom.document
  * @html def myUl = {
  *   <ul>{Seq(<li>data1</li>, <li>data2</li>)}</ul>
  * }
  * val div = document.createElement("div")
  * html.render(div, myUl)
  * div.firstChild.childNodes.length should be(2)
  * }}}
  *
  * @example XHTML comments
  * {{{
  * import org.scalajs.dom.document
  * @html def comment = <div><!--my comment--></div>
  * val div = document.createElement("div")
  * html.render(div, comment)
  * assert(div.innerHTML == "<div><!--my comment--></div>")
  * }}}
  *
  * @example Escape
  * {{{
  * import org.scalajs.dom.document
  * @html def escaped = <div>&#32;</div>
  * val div = document.createElement("div")
  * html.render(div, escaped)
  * assert(div.innerHTML == "<div> </div>")
  * }}}
  * @example Entity references
  * {{{
  * import org.scalajs.dom.document
  * @html def entity = <div>&amp;&lt;&copy;&lambda;</div>
  * val div = document.createElement("div")
  * html.render(div, entity)
  * assert(div.innerHTML == "<div>&amp;&lt;©λ</div>")
  * }}}
  * @example Process instructions
  * {{{
  * @html val myXmlStylesheet = <?my-instruction my data?>
  * myXmlStylesheet.watch()
  * myXmlStylesheet.value.target should be("my-instruction")
  * myXmlStylesheet.value.data should be("my data")
  * }}}
  * @example CDATA sections are not supported in HTML documents
  * {{{
  * import scala.scalajs.js
  * a[js.JavaScriptException] should be thrownBy {
  *   @html val myCData = <![CDATA[my data]]>
  *   myCData.watch()
  * }
  * }}}
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class html extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro html.WhiteBoxMacros.macroTransform
}
