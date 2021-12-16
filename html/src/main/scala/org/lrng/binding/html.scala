package org.lrng
package binding

import com.thoughtworks.binding.Binding._
import com.thoughtworks.binding._
import com.thoughtworks.binding.bindable.{Bindable, BindableSeq}
import org.lrng.binding.dynamicanyref.{AnyRefApplyDynamic, AnyRefSelectDynamic}
import org.scalajs.dom.HTMLElement
import org.scalajs.dom.{Element, Node, document}

import scala.annotation._
import scala.language.experimental.macros
import scala.language.{dynamics, implicitConversions}
import scala.reflect.macros.whitebox
import scala.scalajs.js
object html {

  def mount(parent: Node, children: BindingSeq[Node]): Binding[Unit] = {
    new NodeBindingSeq.NodeSeqMountPoint(parent, children)
  }

  def render[Children](parent: Node, children: Children)(implicit bindableSeq: BindableSeq.Lt[Children, Node]) = {
    mount(parent, bindableSeq.toBindingSeq(children)).watch()
  }

  /** The factory class to create [[ElementBuilder]] */
  trait ElementFactory[E <: Element] extends Curried {
    protected def tagName: String
    @inline def applyBegin = new NodeBinding.Constant.ElementBuilder(document.createElement(tagName).asInstanceOf[E])
  }

  object AttributeFactory {

    private object Typed {
      final class AttributeMountPointBuilder[E <: Element, A <: Typed](
          implicit attributeSetter: NodeBinding.Constant.AttributeSetter[E, A])
          extends NodeBinding.Interpolated.MountPointBuilder[E, A, String] {
        def mountProperty(e: E, binding: Binding[String]): Binding[Unit] = {
          Binding.BindingInstances.map(binding)(attributeSetter.setAttribute(e, _))
        }
      }

      final class OptionAttributeMountPointBuilder[E <: Element, A <: Typed](
          implicit attributeSetter: NodeBinding.Constant.AttributeSetter[E, A],
          attributeRemover: NodeBinding.Constant.AttributeRemover[E, A])
          extends NodeBinding.Interpolated.MountPointBuilder[E, A, Option[String]] {
        def mountProperty(e: E, binding: Binding[Option[String]]): Binding[Unit] = {
          Binding.BindingInstances.map(binding)(optValue =>
            optValue match {
              case Some(value) => attributeSetter.setAttribute(e, value)
              case _           => attributeRemover.removeAttribute(e)
          })
        }
      }
    }

    trait Typed extends Curried {

      @inline
      implicit def attributeMountPointBuilder[E <: Element](
          implicit attributeSetter: NodeBinding.Constant.AttributeSetter[E, this.type])
        : NodeBinding.Interpolated.MountPointBuilder[E, this.type, String] =
        new Typed.AttributeMountPointBuilder[E, this.type]

      @inline
      implicit def optionalAttributeMountPointBuilder[E <: Element](
          implicit attributeSetter: NodeBinding.Constant.AttributeSetter[E, this.type],
          attributeRemover: NodeBinding.Constant.AttributeRemover[E, this.type]
      ): NodeBinding.Interpolated.MountPointBuilder[E, this.type, Option[String]] =
        new Typed.OptionAttributeMountPointBuilder[E, this.type]

      @inline
      def applyBegin = new NodeBinding.Constant.MultipleAttributeBuilder.Typed[this.type](this)

      @inline
      def apply() = new NodeBinding.Constant.AttributeBuilder.Typed[this.type]("")

      @inline
      def apply(textBuilder: NodeBinding.Constant.TextBuilder) =
        new NodeBinding.Constant.AttributeBuilder.Typed[this.type](textBuilder.data)

      @inline def apply[A <: Binding[Any]](value: A) = new NodeBinding.Interpolated.PropertyBuilder[this.type, A](value)
    }

    final class Untyped(private val attributeName: String) extends AnyVal with Curried {
      @inline
      protected def setAttribute(element: Element, value: String) = {
        element.setAttribute(attributeName, value)
      }
      @inline
      def applyBegin = new NodeBinding.Constant.MultipleAttributeBuilder.Untyped(this)

      @inline
      def apply() = new NodeBinding.Constant.AttributeBuilder.Untyped(setAttribute(_, ""))

      @inline
      def apply(textBuilder: NodeBinding.Constant.TextBuilder) =
        new NodeBinding.Constant.AttributeBuilder.Untyped(setAttribute(_, textBuilder.data))

      protected type Self = Untyped
      def apply(binding: Binding[String]) =
        new NodeBinding.Interpolated.AttributeBuilder({ element =>
          Binding.BindingInstances.map(binding)(element.setAttribute(attributeName, _))
        })

      def apply(optionalBinding: Binding[Option[String]])(implicit dummyImplicit: DummyImplicit =
                                                            DummyImplicit.dummyImplicit) =
        new NodeBinding.Interpolated.AttributeBuilder({ element =>
          Binding.BindingInstances.map(optionalBinding) {
            case Some(value) => element.setAttribute(attributeName, value)
            case _           => element.removeAttribute(attributeName)
          }
        })

//      def apply(optionalBinding: Binding[Option[String]])(implicit dummyImplicit: DummyImplicit =
//                                                            DummyImplicit.dummyImplicit): Unit = new NodeBinding.Interpolated.AttributeBuilder({ element =>
//        Binding.BindingInstances.map(optionalBinding)(optValue => optValue match {
//          case Some(value) => attributeSetter.setAttribute(e, value)
//          case _           => attributeRemover.removeAttribute(e)element.setAttribute(attributeName, _))
//      })
    }

  }

  type NodeBindingSeq[+A] = BindingSeq[A] {
    def value: collection.Seq[A]
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
    private[lrng] final class NodeSeqMountPoint(parent: Node, childrenBinding: BindingSeq[Node])
        extends MultiMountPoint[Node](childrenBinding) {

      protected def set(children: Seq[Node]): Unit = {
        set(children: Iterable[Node])
      }

      protected def set(children: Iterable[Node]): Unit = {
        removeAll(parent)
        for (child <- children) {
          if (child.parentNode != null) {
            throw new IllegalStateException(raw"""Cannot insert ${child.toString} twice!""")
          }
          parent.appendChild(child)
        }
      }

      protected def splice(from: Int, that: Iterable[Node], replaced: Int): Unit = {
        spliceGenIterable(from, that, replaced)
      }

      protected def splice(from: Int, that: collection.GenSeq[Node], replaced: Int): Unit = {
        spliceGenIterable(from, that, replaced)
      }

      private def spliceGenIterable(from: Int, that: collection.GenIterable[Node], replaced: Int): Unit = {
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
    final class Builder(val childNodes: js.Array[Node] = new js.Array(0),
                        val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
        extends ChildBuilder {
      def applyEnd: NodeBindingSeq[Node] = {
        if (childNodes.isEmpty) {
          Constants(scalajs.runtime.toScalaVarArgs(childNodes): _*)
        } else {
          new NodeBindingSeq.Interpolated(childNodes, mountPoints)
        }
      }
    }

    type Constants[+A] = Binding.Constants[A]

    final class Interpolated private[lrng] (private[lrng] val nodes: js.Array[Node] = new js.Array(0),
                                            private[lrng] val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
        extends BindingSeq[Node] {
      type All[+A] = collection.Seq[A]
      def value: collection.Seq[Node] = nodes
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

      @implicitNotFound("${AttributeObject} is not an attribute of ${E}")
      final class AttributeSetter[-E <: Element, AttributeObject](val setAttribute: (E, String) => Unit) extends AnyVal

      @implicitNotFound("${AttributeObject} is not an attribute of ${E}")
      final class AttributeRemover[-E <: Element, AttributeObject](val removeAttribute: (E) => Unit) extends AnyVal

      final class ElementBuilder[+E <: Element] private[lrng] (private[lrng] val element: E) extends AnyVal {

        @inline
        def applyNext[AttributeObject](child: AttributeBuilder.Untyped) = {
          child.set(element)
          this
        }
        @inline
        def applyNext[AttributeObject](child: AttributeBuilder.Typed[AttributeObject])(
            implicit attributeSetter: AttributeSetter[E, AttributeObject]) = {
          attributeSetter.setAttribute(element, child.value)
          this
        }

        def applyNext(child: Binding[String]) = {
          toInterpolated.applyNext(child)
        }

        def applyNext[Child](child: Binding[Child])(implicit bindableSeq: BindableSeq.Lt[Child, Node]) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyNext(child: NodeBinding.Interpolated.AttributeBuilder)(implicit dummyImplicit: DummyImplicit =
                                                                          DummyImplicit.dummyImplicit) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyNext[Property, Expected](child: NodeBinding.Interpolated.PropertyBuilder[Property, Binding[Expected]])(
            implicit mountPointBuilder: Interpolated.MountPointBuilder[E, Property, Expected]
        ) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyNext(child: NodeBinding.Constant.ElementBuilder[Element]) = {
          toInterpolated.applyNext(child)
        }

        def applyNext(child: NodeBinding.Interpolated.ElementBuilder[Element]) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyNext(child: NodeBinding.Constant.NodeBuilder[Node]) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyNext(child: NodeBinding.Constant.TextBuilder) = {
          toInterpolated.applyNext(child)
        }

        @inline
        def applyEnd = this
        private def toInterpolated = new NodeBinding.Interpolated.ElementBuilder[E](element)

      }
      final class NodeBuilder[+Node] private[lrng] (private[lrng] val node: Node) extends AnyVal
      final class TextBuilder private[lrng] (private[lrng] val data: String) extends AnyVal
      object AttributeBuilder {
        final class Untyped(private[lrng] val set: Element => Unit) extends AnyVal
        final class Typed[A](private[lrng] val value: String) extends AnyVal

      }
      object MultipleAttributeBuilder {

        final class Typed[A <: AttributeFactory.Typed](private[html] val attributeFactory: A,
                                                       stringBuilder: StringBuilder = new StringBuilder) {
          def applyNext(textBuilder: TextBuilder) = {
            stringBuilder ++= textBuilder.data
            this
          }
          def applyEnd = {
            new AttributeBuilder.Typed[A](stringBuilder.toString())
          }
        }
        final class Untyped(attributeFactory: AttributeFactory.Untyped,
                            stringBuilder: StringBuilder = new StringBuilder) {
          def applyNext(textBuilder: TextBuilder) = {
            stringBuilder ++= textBuilder.data
            this
          }
          def applyEnd: AttributeBuilder.Untyped = {
            attributeFactory(new TextBuilder(stringBuilder.toString()))
          }
        }
      }

    }

    type Constant[+A] = Binding.Constant[A]

    final class Interpolated[+A] private[lrng] (val value: A, private[lrng] val mountPoints: js.Array[Binding[Unit]])
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

      final class AttributeBuilder(val mountPoint: Element => Binding[Unit]) extends AnyVal

      final class PropertyBuilder[PropertyType, Value](val value: Value) extends AnyVal

      @implicitNotFound("${AttributeObject} of type ${V} is not a property of ${E}")
      trait MountPointBuilder[-E <: Element, AttributeObject, -V] {
        def mountProperty(e: E, binding: Binding[V]): Binding[Unit]
      }

      object MountPointBuilder {
        implicit def convertedMountPointBuilder[E <: Element, A, V0, V1](
            implicit mountPointBuilder: MountPointBuilder[E, A, V1],
            bindable: Bindable.Lt[Binding[V0], V1]): MountPointBuilder[E, A, V0] = new MountPointBuilder[E, A, V0] {
          def mountProperty(e: E, binding: Binding[V0]): Binding[Unit] = {
            mountPointBuilder.mountProperty(e, bindable.toBinding(binding))
          }
        }
      }

      final class ElementBuilder[+E <: Element](val element: E,
                                                val bindingSeqs: js.Array[Binding[BindingSeq[Node]]] = new js.Array(0),
                                                var childNodes: js.Array[Node] = new js.Array(0),
                                                val mountPoints: js.Array[Binding[Unit]] = new js.Array(0))
          extends ChildBuilder {

        private[lrng] def mergeTrailingNodes(): Unit = {
          if (childNodes.nonEmpty) {
            bindingSeqs += Binding.Constant(Binding.Constants(scalajs.runtime.toScalaVarArgs(childNodes): _*))
            childNodes = new js.Array(0)
          }
        }
        private[lrng] def flush(): Unit = {
          mergeTrailingNodes()
          if (bindingSeqs.nonEmpty) {
            mountPoints += mount(element, Constants(scalajs.runtime.toScalaVarArgs(bindingSeqs): _*).flatMapBinding(identity))
          }
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
        def applyNext[AttributeObject](attributeBuilder: NodeBinding.Constant.AttributeBuilder.Untyped) = {
          attributeBuilder.set(element)
          this
        }

        @inline
        def applyNext[AttributeObject](attributeBuilder: NodeBinding.Constant.AttributeBuilder.Typed[AttributeObject])(
            implicit attributeSetter: Constant.AttributeSetter[E, AttributeObject]) = {
          attributeSetter.setAttribute(element, attributeBuilder.value)
          this
        }

        @inline
        def applyNext(attributeBuilder: NodeBinding.Interpolated.AttributeBuilder)(
            implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) = {
          mountPoints += attributeBuilder.mountPoint(element)
          this
        }

        @inline
        def applyNext[Property, Expected](
            attributeBuilder: NodeBinding.Interpolated.PropertyBuilder[Property, Binding[Expected]])(
            implicit mountPointBuilder: MountPointBuilder[E, Property, Expected]
        ) = {
          mountPoints += mountPointBuilder.mountProperty(element, attributeBuilder.value)
          this
        }

      }
    }
  }

  private[lrng] trait ChildBuilder {
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
        mountPoints.push(scalajs.runtime.toScalaVarArgs(elementBuilder.mountPoints): _*)
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

  private[lrng] object elementTypes {

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLTimeElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDataElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLPictureElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLOutputElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLMeterElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDetailsElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLDialogElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLTemplateElement <: HTMLElement

    /** @todo Remove this type alias once scala-js-dom added the definition for this type */
    type HTMLSlotElement <: HTMLElement

  }

  object autoImports extends LowPriorityAutoImports {

    object data {
      @inline def entities = EntityBuilders
      @inline def interpolation = Binding
      object values extends AnyRefSelectDynamic[NodeBinding.Constant.TextBuilder] {
        @inline def selectDynamic(data: String) = new NodeBinding.Constant.TextBuilder(data)
        @inline def selectDynamic: NodeBinding.Constant.TextBuilder = selectDynamic("selectDynamic")
      }

      object attributes extends AnyRefApplyDynamic {
        object applyDynamic {
          @inline def apply(attributeName: String): AttributeFactory.Untyped =
            new AttributeFactory.Untyped(attributeName)
          @inline implicit def asUntyped(a: => applyDynamic.type): AttributeFactory.Untyped =
            new AttributeFactory.Untyped("applyDynamic")
        }
      }
    }

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

    object xml {
      @inline def noPrefix[Uri](uri: Uri) = uri

      object prefixes extends Dynamic {
        @inline def getClass[Uri](uri: Uri) = uri
        @inline def !=[Uri](uri: Uri) = uri
        @inline def ##[Uri](uri: Uri) = uri
        @inline def +[Uri](uri: Uri) = uri
        @inline def ->[Uri](uri: Uri) = uri
        @inline def ==[Uri](uri: Uri) = uri
        @inline def asInstanceOf[Uri](uri: Uri) = uri
        @inline def ensuring[Uri](uri: Uri) = uri
        @inline def eq[Uri](uri: Uri) = uri
        @inline def equals[Uri](uri: Uri) = uri
        @inline def formatted[Uri](uri: Uri) = uri
        @inline def hashCode[Uri](uri: Uri) = uri
        @inline def isInstanceOf[Uri](uri: Uri) = uri
        @inline def ne[Uri](uri: Uri) = uri
        @inline def notify[Uri](uri: Uri) = uri
        @inline def notifyAll[Uri](uri: Uri) = uri
        @inline def synchronized[Uri](uri: Uri)(implicit dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) =
          uri
        @inline def toString[Uri](uri: Uri) = uri
        @inline def wait[Uri](uri: Uri) = uri
        @inline def →[Uri](uri: Uri) = uri
        object applyDynamic {
          @inline def applyDynamic[Uri](uri: Uri) = uri
        }
        case class applyDynamic(prefix: String) extends AnyVal {
          @inline def apply[Uri](uri: Uri) = uri
        }
      }
      object uris {
        @inline
        def `http://www.w3.org/1999/xhtml` = xml
      }
      @inline def elements = ElementFactories
      @inline def attributes = AttributeFactories
      @inline def entities = EntityBuilders
      @inline def cdata(data: String) = new NodeBinding.Constant.NodeBuilder(document.createCDATASection(data))
      object values extends AnyRefSelectDynamic[NodeBinding.Constant.TextBuilder] {
        @inline def selectDynamic: NodeBinding.Constant.TextBuilder = selectDynamic("selectDynamic")
        @inline def selectDynamic(data: String) = new NodeBinding.Constant.TextBuilder(data)
      }

      /**
        * @note All methods in [[AnyRef]] are overloaded, in case of name clash
        * {{{
        * import _root_.org.lrng.binding.html.autoImports.xml.texts
        * import _root_.org.lrng.binding.html.NodeBinding.Constant.TextBuilder
        * texts.selectDynamic should be(a[TextBuilder])
        * texts.getClass should be(a[TextBuilder])
        * texts.!= should be(a[TextBuilder])
        * texts.## should be(a[TextBuilder])
        * texts.+ should be(a[TextBuilder])
        * texts.-> should be(a[TextBuilder])
        * texts.== should be(a[TextBuilder])
        * texts.asInstanceOf should be(a[TextBuilder])
        * texts.ensuring should be(a[TextBuilder])
        * texts.eq should be(a[TextBuilder])
        * texts.equals should be(a[TextBuilder])
        * texts.formatted should be(a[TextBuilder])
        * texts.hashCode should be(a[TextBuilder])
        * texts.isInstanceOf should be(a[TextBuilder])
        * texts.ne should be(a[TextBuilder])
        * texts.notify should be(a[TextBuilder])
        * texts.notifyAll should be(a[TextBuilder])
        * texts.synchronized should be(a[TextBuilder])
        * texts.toString should be(a[TextBuilder])
        * texts.wait should be(a[TextBuilder])
        * texts.→ should be(a[TextBuilder])
        * }}}
        */
      object texts extends AnyRefSelectDynamic[NodeBinding.Constant.TextBuilder] {
        @inline def selectDynamic: NodeBinding.Constant.TextBuilder = selectDynamic("selectDynamic")
        @inline def selectDynamic(data: String) = new NodeBinding.Constant.TextBuilder(data)
      }
      @inline def comment(data: String) = new NodeBinding.Constant.NodeBuilder(document.createComment(data))

      object processInstructions extends Dynamic {
        @inline def getClass(data: String) = applyDynamic("getClass")(data)
        @inline def !=(data: String) = applyDynamic("!=")(data)
        @inline def ##(data: String) = applyDynamic("##")(data)
        @inline def +(data: String) = applyDynamic("+")(data)
        @inline def ->(data: String) = applyDynamic("->")(data)
        @inline def ==(data: String) = applyDynamic("==")(data)
        @inline def asInstanceOf(data: String) = applyDynamic("asInstanceOf")(data)
        @inline def ensuring(data: String) = applyDynamic("ensuring")(data)
        @inline def eq(data: String) = applyDynamic("eq")(data)
        @inline def equals(data: String) = applyDynamic("equals")(data)
        @inline def formatted(data: String) = applyDynamic("formatted")(data)
        @inline def hashCode(data: String) = applyDynamic("hashCode")(data)
        @inline def isInstanceOf(data: String) = applyDynamic("isInstanceOf")(data)
        @inline def ne(data: String) = applyDynamic("ne")(data)
        @inline def notify(data: String) = applyDynamic("notify")(data)
        @inline def notifyAll(data: String) = applyDynamic("notifyAll")(data)
        @inline def synchronized(data: String) = applyDynamic("synchronized")(data)
        @inline def toString(data: String) = applyDynamic("toString")(data)
        @inline def wait(data: String) = applyDynamic("wait")(data)
        @inline def →(data: String) = applyDynamic("→")(data)
        @inline def applyDynamic(target: String)(data: String) =
          new NodeBinding.Constant.NodeBuilder(document.createProcessingInstruction(target, data))
      }
      @inline def interpolation = Binding
      object literal extends Curried {
        @inline def apply[Node](nodeBuilder: NodeBinding.Constant.NodeBuilder[Node]) = Constant(nodeBuilder.node)
        @inline def apply[E <: Element](elementBuilder: NodeBinding.Constant.ElementBuilder[E]) =
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
        @inline def applyBegin = new NodeBindingSeq.Builder
      }
    }

  }

  private[lrng] final class WhiteBoxMacros(context: whitebox.Context) extends nameBasedXml.Macros(context) {
    import c.universe._
    override protected def transformBody(tree: Tree): Tree = q"""
      import _root_.org.lrng.binding.html.autoImports.{
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
      ${new NameBasedXmlTransformer().transform(tree)}
    """
  }
}

/** An annotation to convert XHTML literals to data-bindable DOM nodes.
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  *
  * @example XHTML literals with text attributes
  * {{{
  * @html
  * val mySpan = <span class="my-class" tabindex="42"></span>
  * mySpan.value.nodeName should be("SPAN")
  * mySpan.value.className should be("my-class")
  * mySpan.value.tabIndex should be(42)
  * }}}
  *
  * Nested XHTML literals with interpolation:
  *
  * {{{
  * @html
  * val mySpan2 = <span style="color: red;" tabIndex={99999} title="my title" class={"my-class"}><span>text</span><i style={"color: blue;"} tabIndex={99}></i><span innerHTML={"html"}></span><span></span>{mySpan.bind}</span>
  * mySpan2.watch()
  * mySpan2.value.outerHTML should be("""<span style="color: red;" tabindex="99999" title="my title" class="my-class"><span>text</span><i style="color: blue;" tabindex="99"></i><span>html</span><span></span><span class="my-class" tabindex="42"></span></span>""")
  * }}}
  *
  * @example Special character in attribute names
  * {{{
  * @html
  * val myMeta = <meta http-equiv="refresh" content="30"/>
  * myMeta.watch()
  * myMeta.value.outerHTML should be("""<meta http-equiv="refresh" content="30">""")
  * }}}
  *
  * @example Opaque type aliases of HTMLInputElement
  * {{{
  * @html
  * val myMeta = <input name="myInput" type="radio"/>
  * myMeta.watch()
  * myMeta.value.outerHTML should be("""<input name="myInput" type="radio">""")
  * }}}
  *
  * @example Element list of XHTML literals
  *
  * {{{
  * @html
  * val mySpans = <span></span><span title={"my title"} class=""></span><span class="my-class"></span>
  * mySpans.watch()
  * import org.scalajs.dom.html.Span
  * inside(mySpans.value) {
  *   case collection.Seq(span1: Span, span2: Span, span3: Span) =>
  *     span1.nodeName should be("SPAN")
  *     span1.hasAttribute("class") should be(false)
  *     span1.className should be("")
  *     span2.title should be("my title")
  *     span2.hasAttribute("class") should be(true)
  *     span2.className should be("")
  *     span3.className should be("my-class")
  * }
  * }}}
  *
  * @example Text interpolation in an element
  * {{{
  * @html val monadicSpan = <span>{"text"}</span>
  * monadicSpan.watch()
  * assert(monadicSpan.value.outerHTML == "<span>text</span>")
  * }}}
  *
  * @example Changing text
  * {{{
  * import com.thoughtworks.binding.Binding.Var
  * val v0 = Var("original text")
  * @html val monadicSpan = <span> <i> {v0.bind} </i> </span>
  * monadicSpan.watch()
  * assert(monadicSpan.value.outerHTML == "<span> <i> original text </i> </span>")
  * v0.value = "changed"
  * assert(monadicSpan.value.outerHTML == "<span> <i> changed </i> </span>")
  * }}}
  *
  * @example `for` / `yield` expressions in XHTML interpolation
  * {{{
  * import com.thoughtworks.binding.Binding.Vars
  * val v0 = Vars("original text 0","original text 1")
  * @html val monadicSpan = <span> <i> { for (s <- v0) yield <b>{s}</b> } </i> </span>
  * monadicSpan.watch()
  * val span = monadicSpan.value
  * assert(monadicSpan.value.outerHTML == "<span> <i> <b>original text 0</b><b>original text 1</b> </i> </span>")
  * v0.value.prepend("prepended")
  * assert(span eq monadicSpan.value)
  * assert(monadicSpan.value.outerHTML == "<span> <i> <b>prepended</b><b>original text 0</b><b>original text 1</b> </i> </span>")
  * v0.value.remove(1)
  * assert(span eq monadicSpan.value)
  * assert(monadicSpan.value.outerHTML == "<span> <i> <b>prepended</b><b>original text 1</b> </i> </span>")
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
  * @example Dynamc attributes
  * {{{
  * @html
  * val myBr = <br data:toString="+&copy;" data:applyDynamic="value"/>
  * myBr.watch()
  * myBr.value.outerHTML should be("""<br tostring="+©" applydynamic="value">""")
  * }}}
  *
  * @example Changing attribute values
  * {{{
  * import com.thoughtworks.binding.Binding.Var
  * val id = Var("oldId")
  * @html val myInput = <input data:applyDynamic={id.bind} data:custom={id.bind} name={id.bind} id={id.bind} onclick={ _: Any => id.value = "newId" }/>
  * myInput.watch()
  * assert(myInput.value.outerHTML == """<input applydynamic="oldId" custom="oldId" name="oldId" id="oldId">""")
  * myInput.value.onclick(null)
  * assert(myInput.value.outerHTML == """<input applydynamic="newId" custom="newId" name="newId" id="newId">""")
  * }}}
  *
  * @example A child node must not be inserted more than once
  * {{{
  * an[IllegalStateException] should be thrownBy {
  *   @html
  *   val child = <hr/>
  *   @html
  *   val parent = <span><i>{child.bind}</i><i>{child.bind}</i></span>
  *   parent.watch()
  * }
  * }}}
  *
  *
  * @example Seq in DOM
  * {{{
  * import org.scalajs.dom.document
  * @html def mySelect = {
  *   <select>{Seq(<option>data1</option>, <option>data2</option>)}</select>
  * }
  * val span = document.createElement("span")
  * _root_.org.lrng.binding.html.render(span, mySelect)
  * span.firstChild.childNodes.length should be(2)
  * }}}
  *
  * @example XHTML comments
  * {{{
  * import org.scalajs.dom.document
  * @html def comment = <span><!--my comment--></span>
  * val span = document.createElement("span")
  * _root_.org.lrng.binding.html.render(span, comment)
  * assert(span.innerHTML == "<span><!--my comment--></span>")
  * }}}
  *
  * @example Escape
  * {{{
  * import org.scalajs.dom.document
  * @html def escaped = <span>&#32;$minus</span>
  * val span = document.createElement("span")
  * _root_.org.lrng.binding.html.render(span, escaped)
  * assert(span.innerHTML == "<span> $minus</span>")
  * }}}
  * @example Entity references
  * {{{
  * import org.scalajs.dom.document
  * @html def entity = <span class="&lt; &gt; &copy; &lambda; my-class">my text &lt; &gt; &copy; &lambda;</span>
  * val span = document.createElement("span")
  * _root_.org.lrng.binding.html.render(span, entity)
  * assert(span.innerHTML == """<span class="< > © λ my-class">my text &lt; &gt; © λ</span>""")
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
  *
  * @example XML namespaces
  * {{{
  * import scala.language.dynamics
  * import org.scalajs.dom.document
  * import org.scalajs.dom._
  * import com.thoughtworks.binding.Binding
  * import com.thoughtworks.binding.Binding.BindingInstances.monadSyntax._
  * object svg {
  *   object texts extends Dynamic {
  *     @inline def selectDynamic(data: String) = new _root_.org.lrng.binding.html.NodeBinding.Constant.TextBuilder(data)
  *   }
  *   object elements {
  *     object svg extends Curried {
  *       @inline def applyBegin = new _root_.org.lrng.binding.html.NodeBinding.Constant.ElementBuilder(document.createElementNS("http://www.w3.org/2000/svg", "svg").asInstanceOf[SVGSVGElement])
  *     }
  *     object text extends Curried {
  *       @inline def applyBegin = new _root_.org.lrng.binding.html.NodeBinding.Constant.ElementBuilder(document.createElementNS("http://www.w3.org/2000/svg", "text").asInstanceOf[SVGTextElement])
  *     }
  *   }
  *   @inline def interpolation = Binding
  *   object values {
  *     sealed trait FontStyle
  *     case object normal extends FontStyle
  *     case object italic extends FontStyle
  *     case object oblique extends FontStyle
  *   }
  *   object attributes {
  *     def font$minusstyle(value: values.FontStyle) = {
  *       new _root_.org.lrng.binding.html.NodeBinding.Constant.AttributeBuilder.Untyped(_.setAttribute("font-style", value.toString))
  *     }
  *     def font$minusstyle(binding: Binding[values.FontStyle]) = {
  *       new _root_.org.lrng.binding.html.NodeBinding.Interpolated.AttributeBuilder( element =>
  *         binding.map { value =>
  *           element.setAttribute("font-style", value.toString)
  *         }
  *       )
  *     }
  *   }
  * }
  * implicit final class SvgUriOps(uriFactory: _root_.org.lrng.binding.html.autoImports.xml.uris.type) {
  *   @inline def http$colon$div$divwww$u002Ew3$u002Eorg$div2000$divsvg = svg
  * }
  * @html
  * val mySvg1 = <svg xmlns="http://www.w3.org/2000/svg"><text font-style="normal">my text</text></svg>
  * mySvg1.watch()
  * mySvg1.value.outerHTML should be("""<svg><text font-style="normal">my text</text></svg>""")
  *
  * import svg.values.normal
  * @html
  * val mySvg2 = <svg:svg xmlns:svg="http://www.w3.org/2000/svg"><svg:text font-style={normal}>my text</svg:text></svg:svg>
  * mySvg2.watch()
  * mySvg2.value.outerHTML should be("""<svg><text font-style="normal">my text</text></svg>""")
  * }}}
  *
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class html extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro _root_.org.lrng.binding.html.WhiteBoxMacros.macroTransform
}
