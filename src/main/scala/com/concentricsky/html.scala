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
import com.thoughtworks.binding.bindable.BindableSeq
import com.thoughtworks.binding.bindable.Bindable
import scalaz.Monad
object html {

  def mount(parent: Node, children: BindingSeq[Node]): Binding[Unit] = {
    new NodeBindingSeq.NodeSeqMountPoint(parent, children)
  }

  def render[Children](parent: Node, children: Children)(implicit bindableSeq: BindableSeq.Lt[Children, Node]) = {
    mount(parent, bindableSeq.toBindingSeq(children)).watch()
  }

  type NodeBindingSeq[+A] = BindingSeq[A] {
    def value: Seq[A]
  }
  object NodeBindingSeq {

    @inline
    @tailrec
    private def removeAll(parent: Node): Unit = {
      val firstChild = parent.firstChild
      if (firstChild != null) {
        parent.removeChild(firstChild)
        removeAll(parent)
      }
    }
    private[concentricsky] final class NodeSeqMountPoint(parent: Node, childrenBinding: BindingSeq[Node])
        extends MultiMountPoint[Node](childrenBinding) {

      override protected def set(children: Seq[Node]): Unit = {
        removeAll(parent)
        for (child <- children) {
          if (child.parentNode != null) {
            throw new IllegalStateException(raw"""Cannot insert ${child.toString} twice!""")
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
              throw new IllegalStateException(raw"""Cannot insert a ${newChild.toString} element twice!""")
            }
            parent.appendChild(newChild)
          }
        } else {
          for (newChild <- that) {
            if (newChild.parentNode != null) {
              throw new IllegalStateException(raw"""Cannot insert a ${newChild.toString} element twice!""")
            }
            parent.insertBefore(newChild, child)
          }
        }
      }

    }
    final class NodeListBuilder(val childNodes: js.Array[Node] = new js.Array(0),
                                val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
        extends ChildBuilder {
      def applyEnd: NodeBindingSeq[Node] = {
        if (childNodes.isEmpty) {
          Constants(childNodes: _*)
        } else {
          new NodeBindingSeq.Interpolated(childNodes, mountPoints)
        }
      }
    }

    type Constants[+A] = Binding.Constants[A]

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
      object ElementBuilder {
        implicit def toInterpolated[E <: Element](elementBuilder: ElementBuilder[E]) =
          new Interpolated.ElementBuilder[E](elementBuilder.element)
      }
      final class ElementBuilder[+E <: Element] private[concentricsky] (private[concentricsky] val element: E)
          extends AnyVal {

        @inline
        def applyNext(attribute: AttributeBuilder[E]) = {
          attribute.set(element)
          this
        }

        @inline
        def applyEnd = this
      }
      final class NodeBuilder[+Node] private[concentricsky] (private[concentricsky] val node: Node) extends AnyVal
      final class TextBuilder private[concentricsky] (private[concentricsky] val data: String) extends AnyVal

      final class AttributeBuilder[-E <: Element](private[concentricsky] val set: E => Unit) extends AnyVal

      final class MultipleAttributeBuilder[-E <: Element](attributeName: String,
                                                          stringBuilder: StringBuilder = new StringBuilder) {
        def applyNext(textBuilder: TextBuilder) = {
          stringBuilder ++= textBuilder.data
          this
        }
        def applyEnd = {
          val value = stringBuilder.toString()
          new AttributeBuilder[E](_.setAttribute(attributeName, value))
        }
      }

      trait ElementFunction[E <: Element] extends Curried {
        protected def tagName: String
        @inline def applyBegin = new ElementBuilder(document.createElement(tagName).asInstanceOf[E])
      }

      final class AttributeFunction(@inline protected val attributeName: String) extends AnyVal with AttributeOrProperty

    }
    type Constant[+A] = Binding.Constant[A]

    private[concentricsky] trait AttributeOrProperty extends Any with Curried {

      protected def attributeName: String

      @inline
      def applyBegin = new Constant.MultipleAttributeBuilder[Element](attributeName)

      @inline
      def apply() = new Constant.AttributeBuilder[Element](_.setAttribute(attributeName, ""))

      @inline
      def apply(textBuilder: Constant.TextBuilder) =
        new Constant.AttributeBuilder[Element](_.setAttribute(attributeName, textBuilder.data))

    }
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

      trait PropertyFunction extends AttributeOrProperty {
        @inline def apply[A <: Binding[Any]](value: A) = new Interpolated.AttributeBuilder[this.type, A](value)
      }

      final class AttributeBuilder[PropertyType, Value](val value: Value) extends AnyVal

      @implicitNotFound("Property ${P} of type ${V} is not found for ${E}")
      trait MountPointBuilder[-E <: Element, P, -V] {
        def toMountPoint(e: E, binding: Binding[V]): Binding[Unit]
      }

      final class ElementBuilder[+E <: Element](val element: E,
                                                val bindingSeqs: js.Array[Binding[BindingSeq[Node]]] = new js.Array(0),
                                                var childNodes: js.Array[Node] = new js.Array(0),
                                                val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
          extends ChildBuilder {

        private[concentricsky] def mergeTrailingNodes(): Unit = {
          bindingSeqs += Binding.Constant(Binding.Constants(childNodes: _*))
          childNodes = new js.Array(0)
        }
        private[concentricsky] def flush(): Unit = {
          mergeTrailingNodes()
          mountPoints += mount(element, Constants(bindingSeqs: _*).flatMapBinding(identity))
        }
        @inline
        def applyEnd = this

        def applyNext(child: Binding[String]) = {
          val textNode = document.createTextNode("")
          childNodes += textNode
          mountPoints += Binding.BindingInstances.map(child)(textNode.data_=)
          this
        }
        def applyNext[Child](child: Binding[Child])(implicit bindableSeq: BindableSeq.Lt[Child, Node]) = {
          mergeTrailingNodes()
          bindingSeqs += Binding.BindingInstances.map(child)(bindableSeq.toBindingSeq(_))
          this
        }

        @inline
        def applyNext(attributeBuilder: NodeBinding.Constant.AttributeBuilder[E]) = {
          attributeBuilder.set(element)
          this
        }

        @inline
        def applyNext[Property, Actual, Expected](
            attributeBuilder: NodeBinding.Interpolated.AttributeBuilder[Property, Actual])(
            implicit mountPointBuilder: MountPointBuilder[E, Property, Expected],
            bindable: Bindable.Lt[Actual, Expected]
        ) = {
          mountPoints += mountPointBuilder.toMountPoint(element, bindable.toBinding(attributeBuilder.value))
          this
        }

      }
    }
  }

  private[concentricsky] trait ChildBuilder {
    def childNodes: js.Array[Node]
    def mountPoints: js.Array[Binding[Unit]]
    @inline
    def applyNext(elementBuilder: NodeBinding.Constant.ElementBuilder[Element]): this.type = {
      childNodes += elementBuilder.element
      this
    }

    def applyNext(elementBuilder: NodeBinding.Interpolated.ElementBuilder[Element]): this.type = {
      if (elementBuilder.bindingSeqs.isEmpty && elementBuilder.mountPoints.isEmpty) {
        val childElement = elementBuilder.element
        elementBuilder.childNodes.foreach(childElement.appendChild)
        childNodes += childElement
      } else {
        elementBuilder.flush()
        childNodes += elementBuilder.element
        mountPoints.push(elementBuilder.mountPoints: _*)
      }
      this
    }

    @inline
    def applyNext(nodeBuilder: NodeBinding.Constant.NodeBuilder[Node]): this.type = {
      childNodes += nodeBuilder.node
      this
    }
    @inline
    def applyNext(textBuilder: NodeBinding.Constant.TextBuilder): this.type = {
      childNodes += document.createTextNode(textBuilder.data)
      this
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
    implicit def jsFunctionBindable[A, R]: Bindable.Aux[Binding[A => R], js.Function1[A, R]] =
      new Bindable[Binding[A => R]] {
        type Value = js.Function1[A, R]
        def toBinding(from: Binding[A => R]): Binding[js.Function1[A, R]] = {
          Binding.BindingInstances.map(from)(f => f: js.Function1[A, R])
        }
      }

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
      @inline def elements = ElementFunctions
      @inline def attributes = AttributeFunctions
      @inline def entities = EntityBuilders
      @inline def cdata(data: String) = new NodeBinding.Constant.NodeBuilder(document.createCDATASection(data))
      @inline def text(data: String) = new NodeBinding.Constant.TextBuilder(data)
      @inline def comment(data: String) = new NodeBinding.Constant.NodeBuilder(document.createComment(data))
      object processInstructions extends Dynamic {
        @inline
        def applyDynamic(target: String)(data: String) =
          new NodeBinding.Constant.NodeBuilder(document.createProcessingInstruction(target, data))
      }
      @inline def interpolation = Binding
      object literal extends Curried {
        def apply[Node](nodeBuilder: NodeBinding.Constant.NodeBuilder[Node]) = Constant(nodeBuilder.node)
        def apply[E <: Element](elementBuilder: NodeBinding.Constant.ElementBuilder[E]) =
          Constant(elementBuilder.element)
        def apply[E <: Element](elementBuilder: NodeBinding.Interpolated.ElementBuilder[E]): NodeBinding[E] = {
          if (elementBuilder.mountPoints.isEmpty && elementBuilder.bindingSeqs.isEmpty) {
            val element = elementBuilder.element
            elementBuilder.childNodes.foreach(element.appendChild)
            Constant(element)
          } else {
            elementBuilder.flush()
            new NodeBinding.Interpolated(elementBuilder.element, elementBuilder.mountPoints)
          }
        }
        def applyBegin = new NodeBindingSeq.NodeListBuilder
      }
    }

  }

  private[concentricsky] final class WhiteBoxMacros(context: whitebox.Context) extends nameBasedXml.Macros(context) {
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
  * val myDiv2 = <div style="color:red" title="my title" tabIndex={99999}><div>text</div><span tabIndex={99}></span><div></div>{myDiv.bind}</div>
  * myDiv2.watch()
  * myDiv2.value.outerHTML should be("""<div style="color:red" title="my title" tabindex="99999"><div>text</div><span tabindex="99"></span><div></div><div class="my-class" tabindex="42"></div></div>""")
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
  *   <table class="my-table" title="My Tooltip"><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead>{tbodyBinding.bind}</table>
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
  * @html val myInput = <input name={id.bind} id={id.bind} onclick={ _: Any => id.value = "newId" }/>
  * myInput.watch()
  * assert(myInput.value.outerHTML == """<input name="oldId" id="oldId">""")
  * myInput.value.onclick(null)
  * assert(myInput.value.outerHTML == """<input name="newId" id="newId">""")
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
  * @html def entity = <div class="&lt; &gt; &copy; &lambda; my-class">my text &lt; &gt; &copy; &lambda;</div>
  * val div = document.createElement("div")
  * html.render(div, entity)
  * assert(div.innerHTML == """<div class="< > © λ my-class">my text &lt; &gt; © λ</div>""")
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
