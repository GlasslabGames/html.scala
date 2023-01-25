package com.yang_bo

import com.thoughtworks.binding.*
import com.thoughtworks.binding.bindable.Bindable
import com.thoughtworks.binding.bindable.BindableSeq
import com.thoughtworks.dsl.Dsl
import com.thoughtworks.dsl.keywords
import com.thoughtworks.dsl.macros.Reset
import com.yang_bo.html.Definitions
import org.scalajs.dom.DocumentFragment
import org.scalajs.dom.Element
import org.scalajs.dom.Node
import org.scalajs.dom.ParentNode
import org.scalajs.dom.Text
import org.scalajs.dom.document

import scala.annotation.tailrec
import scala.collection.View
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type
import scala.quoted.Varargs
import scala.scalajs.js
import scala.util.chaining.given

import Binding.BindingSeq
import bindable.*

/** Includes the `@html` annotation to create reactive web applications
  *
  * @example
  *   An html interpolation that includes a single root node should be a subtype
  *   of `Binding.Stable[Node]`.
  *
  * {{{
  *   import com.yang_bo.html.*
  *   import com.thoughtworks.binding.Binding
  *   import com.thoughtworks.binding.Binding.Var
  *   import org.scalajs.dom.HTMLSpanElement
  *
  *   val color = Var("brown")
  *   def animal = "dog"
  *   val span: Binding.Stable[HTMLSpanElement] =
  *     html"""<span>The quick ${color.bind} fox jumps&nbsp;over the lazy ${animal}</span>"""
  * }}}
  *
  * It can be then [[render]]ed into a parent node.
  *
  * {{{
  *   import org.scalajs.dom.document
  *   render(document.body, span)
  *   document.body.innerHTML should be(
  *     """<span>The quick brown fox jumps&nbsp;over the lazy dog</span>"""
  *   )
  * }}}
  *
  * If the html reference a data binding expression via
  * [[com.thoughtworks.binding.Binding.bind]], it will automatically change
  * whenever the upstream data changes.
  *
  * {{{
  *   color.value = "red"
  *   document.body.innerHTML should be(
  *     """<span>The quick red fox jumps&nbsp;over the lazy dog</span>"""
  *   )
  * }}}
  * @example
  *   An html interpolation that includes multiple single root nodes should be a
  *   `BindingSeq[Node]`.
  *   {{{
  *   import com.yang_bo.html.*
  *   import com.thoughtworks.binding.Binding.BindingSeq
  *   import org.scalajs.dom.Node
  *   import org.scalajs.dom.document
  *
  *   def myId = "my-id"
  *   val nodes: BindingSeq[Node] =
  *     html"""<span>1&nbsp;${"foo"}2${"bar"}3</span><textarea id=$myId class="my-class">${"baz"}</textarea>"""
  *   render(document.body, nodes)
  *   document.body.innerHTML should be(
  *     """<span>1&nbsp;foo2bar3</span><textarea class="my-class" id="my-id">baz</textarea>"""
  *   )
  *   }}}
  * @example
  *   The variable in an html interpolation could be a
  *   [[com.thoughtworks.binding.Binding.BindingSeq]] of
  *   [[org.scalajs.dom.Node]].
  *   {{{
  *   import com.yang_bo.html.*
  *   import com.thoughtworks.binding.Binding.Constants
  *   import org.scalajs.dom.Node
  *   import org.scalajs.dom.document
  *
  *   val texts = Constants("foo", "bar")
  *   val node =
  *     html"""<ol>${
  *       for (text <- texts) yield html"<li>$text</li>".bind
  *     }</li>"""
  *   render(document.body, node)
  *   document.body.innerHTML should be(
  *     "<ol><li>foo</li><li>bar</li></ol>"
  *   )
  *   }}}
  */
package object html {
  // For document purpose only
}
package html {

  private[html] object Macros:
    import org.w3c.dom.Attr
    import org.w3c.dom.Node
    import org.w3c.dom.Element
    import org.w3c.dom.NodeList
    import org.w3c.dom.Comment
    import org.w3c.dom.NamedNodeMap
    import org.w3c.dom.Text
    import org.w3c.dom.CDATASection
    import org.w3c.dom.ProcessingInstruction
    import net.sourceforge.htmlunit.xerces.xni.QName
    import InterpolationParser.AttributeArgumentsUserDataKey
    import InterpolationParser.ElementArgumentUserDataKey

    def transformCDATASection(cdataSection: CDATASection)(using Quotes) =
      '{
        org.scalajs.dom.document.createCDATASection(${
          Expr(cdataSection.getWholeText)
        })
      }
    def transformProcessingInstruction(
        processingInstruction: ProcessingInstruction
    )(using Quotes) =
      '{
        org.scalajs.dom.document.createProcessingInstruction(
          ${
            Expr(processingInstruction.getTarget)
          },
          ${
            Expr(processingInstruction.getData)
          }
        )
      }
    def transformText(text: Text)(using Quotes) =
      '{
        org.scalajs.dom.document.createTextNode(${ Expr(text.getWholeText) })
      }
    def transformNode(
        node: Node
    )(using IndexedSeq[Expr[Any]], Quotes): Expr[Any] =
      import scala.quoted.quotes.reflect.*
      node match
        case element: Element =>
          transformElement(element)
        case comment: Comment =>
          transformComment(comment)
        case cdataSection: CDATASection =>
          transformCDATASection(cdataSection)
        case text: Text =>
          transformText(text)
        case processingInstruction: ProcessingInstruction =>
          transformProcessingInstruction(processingInstruction)
        case _ =>
          report.error("Unsupported node: " + node.toString)
          '{ ??? }
    def mountProperty[E <: org.scalajs.dom.Element, K, V](using Quotes)(
        element: Element,
        elementExpr: Expr[E],
        propertySymbol: scala.quoted.quotes.reflect.Symbol,
        attributeValueExpr: Expr[V],
        reifiedExpr: Expr[K]
    )(using
        Type[E],
        Type[K],
        Type[V]
    ): Expr[Binding[Unit]] =
      import scala.quoted.quotes.reflect.*
      TypeRepr.of[E].memberType(propertySymbol).asType match
        case '[propertyType] =>
          type InterpolationConverterType =
            InterpolationConverter[K, Binding[propertyType]]
          Implicits.search(TypeRepr.of[InterpolationConverterType]) match
            case success: ImplicitSearchSuccess =>
              '{
                ${ success.tree.asExprOf[InterpolationConverterType] }
                  .apply($reifiedExpr)
                  .map { propertyValue =>
                    ${
                      Assign(
                        Select(
                          elementExpr.asTerm,
                          propertySymbol
                        ),
                        ('{ propertyValue }).asTerm
                      ).asExprOf[Unit]
                    }
                  }
              }
            case failure: ImplicitSearchFailure =>
              report.error(
                s"Cannot produce a bindable expression for the property ${propertySymbol.name}. Expect ${Type
                    .show[propertyType]}, actual ${Type.show[V]}\nCannot find an instance of ${Type
                    .show[InterpolationConverterType]}\n${failure.explanation} ",
                attributeValueExpr.asTerm.pos
              )
              '{ ??? }

    def mountAttribute[E <: org.scalajs.dom.Element, K, V](
        element: Element,
        elementExpr: Expr[E],
        qName: QName,
        attributeValueExpr: Expr[V],
        reifiedExpr: Expr[K]
    )(using
        Type[E],
        Type[K],
        Type[V],
        Quotes
    ): Expr[Binding[Unit]] =
      import scala.quoted.quotes.reflect.*
      type InterpolationConverterType =
        InterpolationConverter[keywords.Typed[K, V], Binding[
          String
        ]]
      Implicits.search(TypeRepr.of[InterpolationConverterType]) match
        case success: ImplicitSearchSuccess =>
          '{
            ${ success.tree.asExprOf[InterpolationConverterType] }
              .apply(keywords.Typed($reifiedExpr))
              .map { stringAttributeValue =>
                ${
                  qName.uri match
                    case null =>
                      '{
                        $elementExpr.setAttribute(
                          ${ Expr(qName.localpart) },
                          stringAttributeValue
                        )
                      }
                    case uri =>
                      '{
                        $elementExpr.setAttributeNS(
                          ${ Expr(uri) },
                          ${ Expr(qName.localpart) },
                          stringAttributeValue
                        )
                      }
                }
              }
          }
        case failure: ImplicitSearchFailure =>
          report.error(
            s"Cannot produce a bindable string for the attribute ${qName.toString} \n${failure.explanation}",
            attributeValueExpr.asTerm.pos
          )
          '{ ??? }

    def mountElementAttributesAndChildNodes[E <: org.scalajs.dom.Element](
        element: Element,
        elementExpr: Expr[E]
    )(using argExprs: IndexedSeq[Expr[Any]])(using
        Type[E],
        Quotes
    ): Expr[Binding.Stable[org.scalajs.dom.Element]] =
      import scala.quoted.quotes.reflect.*
      val attributes = element.getAttributes
      val setStaticAttributeExprs =
        for i <- (0 until attributes.getLength).view yield
          val attr = attributes.item(i).asInstanceOf[Attr]
          attr.getNamespaceURI match {
            case null =>
              if !Definitions.isValidAttribute[E](
                  attr.getName
                )
              then
                report.warning(
                  s"${attr.getName} is not a valid attribute for <${element.getTagName}>"
                )

              '{
                $elementExpr.setAttribute(
                  ${ Expr(attr.getName) },
                  ${ Expr(attr.getValue) }
                )
              }
            case namespaceUri =>
              '{
                $elementExpr.setAttributeNS(
                  ${ Expr(namespaceUri) },
                  ${ Expr(attr.getLocalName) },
                  ${ Expr(attr.getValue) }
                )
              }
          }
      val transformedChildNodes = transformNodeList(element.getChildNodes)
      val childNodesEventLoop = '{
        mountChildNodes($elementExpr, $transformedChildNodes)
      }

      val transformedAttributeEventLoops =
        element.getUserData(AttributeArgumentsUserDataKey) match
          case null =>
            Nil
          case attributeBindings =>
            for case (
                qName: QName,
                argExprs(anyAttributeValueExpr)
              ) <- attributeBindings
                .asInstanceOf[Iterable[_]]
            yield anyAttributeValueExpr.asTerm.tpe.asType match
              case '[attributeType] =>
                val attributeValueExpr =
                  anyAttributeValueExpr.asExprOf[attributeType]
                val anyReifiedExpr =
                  Reset.Macros.reify[false, false, attributeType](
                    attributeValueExpr
                  )
                anyReifiedExpr.asTerm.tpe.asType match
                  case '[keywordType] =>
                    val reifiedExpr = anyReifiedExpr.asExprOf[keywordType]
                    qName.uri match
                      case null =>
                        val propertySymbol =
                          TypeRepr
                            .of[E]
                            .classSymbol
                            .get
                            .fieldMember(qName.localpart)
                        if propertySymbol.isValDef &&
                          propertySymbol.flags.is(Flags.Mutable)
                        then
                          mountProperty(
                            element,
                            elementExpr,
                            propertySymbol,
                            attributeValueExpr,
                            reifiedExpr
                          )
                        else
                          if !Definitions.isValidAttribute[E](
                              qName.rawname
                            )
                          then
                            report.warning(
                              s"${qName.rawname} is neither a valid property nor a valid attribute for <${element.getTagName}>"
                            )
                          mountAttribute(
                            element,
                            elementExpr,
                            qName,
                            attributeValueExpr,
                            reifiedExpr
                          )
                      case uri =>
                        mountAttribute(
                          element,
                          elementExpr,
                          qName,
                          attributeValueExpr,
                          reifiedExpr
                        )

      Expr.block(
        List.from(setStaticAttributeExprs),
        '{
          nodeBinding(
            $elementExpr,
            scalajs.runtime.toJSVarArgs(
              ${
                Varargs(
                  Seq.from[Expr[Binding[Unit]]](
                    View.Appended(
                      transformedAttributeEventLoops,
                      childNodesEventLoop
                    )
                  )
                )
              }
            )
          )
        }
      )

    def transformElement(element: Element)(using IndexedSeq[Expr[Any]])(using
        Quotes
    ): Expr[Binding.Stable[org.scalajs.dom.Element]] =
      element.getNamespaceURI match
        case null =>
          Definitions.findTypeByTagName(
            element.getTagName.toLowerCase
          ) match
            case '[elementType] =>
              '{
                val htmlElement = org.scalajs.dom.document
                  .createElement(${
                    Expr(element.getTagName)
                  })
                  .asInstanceOf[elementType & org.scalajs.dom.Element]
                ${ mountElementAttributesAndChildNodes(element, 'htmlElement) }
              }
        case namespaceUri =>
          '{
            val elementInNamespace = org.scalajs.dom.document.createElementNS(
              ${ Expr(namespaceUri) },
              ${ Expr(element.getLocalName) }
            )
            ${
              mountElementAttributesAndChildNodes(element, 'elementInNamespace)
            }
          }

    def transformComment(
        comment: Comment
    )(using argExprs: IndexedSeq[Expr[Any]])(using
        Quotes
    ): Expr[Any] =
      import scala.quoted.quotes.reflect.*
      comment
        .getUserData(ElementArgumentUserDataKey)
        .asInstanceOf[Null | Int] match
        case null =>
          '{
            org.scalajs.dom.document.createComment(${ Expr(comment.getData) })
          }
        case i: Int =>
          val expr = argExprs(i)
          expr.asTerm.tpe.asType match
            case '[t] =>
              Reset.Macros.reify[false, false, t](expr.asExprOf[t])

    def transformNodeList(nodeList: NodeList)(using IndexedSeq[Expr[Any]])(using
        Quotes
    ): Expr[BindingSeq[org.scalajs.dom.Node]] =
      import scala.quoted.quotes.reflect.report
      import scala.quoted.quotes.reflect.asTerm
      import scala.quoted.quotes.reflect.TypeRepr
      import scala.quoted.quotes.reflect.Implicits
      import scala.quoted.quotes.reflect.ImplicitSearchFailure
      import scala.quoted.quotes.reflect.ImplicitSearchSuccess
      '{
        Binding
          .Constants(${
            Expr.ofSeq(
              (
                for i <- 0 until nodeList.getLength
                yield
                  val child = nodeList.item(i)
                  val transformedTerm = transformNode(child).asTerm
                  transformedTerm.tpe.asType match
                    case '[from] =>
                      // TODO: Use summonInline instead?
                      Implicits.search(
                        TypeRepr
                          .of[
                            InterpolationConverter[from, Binding[
                              org.scalajs.dom.Node
                            ]]
                          ]
                      ) match
                        case success: ImplicitSearchSuccess =>
                          '{
                            ${
                              success.tree
                                .asExprOf[
                                  InterpolationConverter[
                                    from,
                                    Binding[
                                      org.scalajs.dom.Node
                                    ]
                                  ]
                                ]
                            }.apply(${
                              transformedTerm.asExprOf[from]
                            })
                          }
                        case failure: ImplicitSearchFailure =>
                          report.error(
                            s"Require a HTML DOM expression, got ${TypeRepr.of[from].show}\n${failure.explanation}",
                            transformedTerm.pos
                          )
                          '{ ??? }
              ).toSeq
            )
          }: _*)
          .mapBinding(identity)
      }

    def html(
        stringContext: Expr[StringContext],
        args: Expr[Seq[Any]]
    )(using
        Quotes
    ): Expr[Any] =
      import scala.quoted.quotes.reflect.Printer
      import scala.quoted.quotes.reflect.report
      import scala.quoted.quotes.reflect.asTerm
      import scala.quoted.quotes.reflect.TypeRepr
      import scala.quoted.quotes.reflect.Flags
      import scala.quoted.quotes.reflect.Typed
      val Varargs(argExprs) = args: @unchecked
      val '{ StringContext($partsExpr: _*) } = stringContext: @unchecked
      val Expr(partList) = partsExpr: @unchecked
      val parts = partList.toIndexedSeq
      val fragment = InterpolationParser.parseHtmlParts(
        parts,
        { (message, arg) => report.error(message, argExprs(arg).asTerm.pos) }
      )

      // report.info(
      //   "arg:" + fragment.getFirstChild.getChildNodes
      //     .item(1)
      //     .getUserData(ElementArgumentUserDataKey)
      // )
      // report.warning(
      //   fragment.getOwnerDocument.getImplementation
      //     .getFeature("LS", "3.0")
      //     .asInstanceOf[DOMImplementationLS]
      //     .createLSSerializer()
      //     .writeToString(fragment)
      // )
      val rootNodes = fragment.getChildNodes
      if rootNodes.getLength == 1 then
        transformNode(rootNodes.item(0))(using argExprs.toIndexedSeq)
      else transformNodeList(rootNodes)(using argExprs.toIndexedSeq)
    end html
  end Macros

  @inline
  @tailrec
  private def removeAll(parent: Node): Unit = {
    val firstChild = parent.firstChild
    if (firstChild != null) {
      parent.removeChild(firstChild)
      removeAll(parent)
    }
  }

  private[html] final class NodeSeqMountPoint(
      parent: Node,
      childrenBinding: BindingSeq[Node]
  ) extends Binding.MultiMountPoint[Node](childrenBinding) {

    protected def set(children: Iterable[Node]): Unit = {
      removeAll(parent)
      for (child <- children) {
        if (child.parentNode != null) {
          throw new IllegalStateException(
            raw"""Cannot insert ${child.toString} twice!"""
          )
        }
        parent.appendChild(child)
      }
    }

    protected def splice(
        from: Int,
        that: Iterable[Node],
        replaced: Int
    ): Unit = {
      spliceGenIterable(from, that, replaced)
    }

    private def spliceGenIterable(
        from: Int,
        that: collection.GenIterable[Node],
        replaced: Int
    ): Unit = {
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
            throw new IllegalStateException(
              raw"""Cannot insert a ${newChild.toString} element twice!"""
            )
          }
          parent.appendChild(newChild)
        }
      } else {
        for (newChild <- that) {
          if (newChild.parentNode != null) {
            throw new IllegalStateException(
              raw"""Cannot insert a ${newChild.toString} element twice!"""
            )
          }
          parent.insertBefore(newChild, child)
        }
      }
    }
  }

  def mountChildNodes(
      parent: Node,
      childrenBinding: Binding.BindingSeq[Node]
  ): Binding[Unit] = new NodeSeqMountPoint(parent, childrenBinding)

  // type Binding.Stable[+A] = Binding.Stable[A]

  // object Binding.Stable:

  private def fragmentBinding(
      bindingSeq: BindingSeq[Node]
  ): Binding.Stable[DocumentFragment] =
    val fragment = document.createDocumentFragment()
    nodeBinding(
      fragment,
      js.Array(mountChildNodes(fragment, bindingSeq))
    )
  end fragmentBinding
  private[html] opaque type InterpolationConverter[-From, +To] <: From => To =
    From => To

  private[html] object InterpolationConverter:

    given [Keyword, BindingValue, Value](using
        run: Dsl.Run[Keyword, Binding[BindingValue], Value]
    ): InterpolationConverter[Keyword, Binding[BindingValue]] = run(_)

    given [BindingKeyword, BindingValue, Value](using
        run: Dsl.Run[BindingKeyword, Binding[BindingValue], Value],
        bindableSeq: BindableSeq.Lt[Binding[BindingValue], Node]
    ): InterpolationConverter[BindingKeyword, Binding.Stable[
      DocumentFragment
    ]] = { keyword =>
      fragmentBinding(bindableSeq.toBindingSeq((run(keyword))))
    }

    given [Keyword, Value](using
        run: Dsl.Run[keywords.FlatMap[Keyword, keywords.Pure[Text]], Binding[
          Text
        ], Value]
    ): InterpolationConverter[keywords.Typed[Keyword, String], Binding[
      Text
    ]] = { typed =>
      run(
        keywords.FlatMap(
          keywords.Typed[Keyword, String].flip(typed),
          keywords.Pure[Text].liftCo(document.createTextNode)
        )
      )
    }

    given [FunctionKeyword, Value, A, R](using
        run: Dsl.Run[keywords.FlatMap[FunctionKeyword, keywords.Pure[
          js.Function1[A, R]
        ]], Binding[
          js.Function1[A, R]
        ], Value],
        dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit
    ): InterpolationConverter[keywords.Typed[FunctionKeyword, A => R], Binding[
      js.Function1[A, R]
    ]] = { typed =>
      run(
        keywords.FlatMap(
          keywords.Typed[FunctionKeyword, A => R].flip(typed),
          { (f: A => R) =>
            keywords.Pure(f: js.Function1[A, R])
          }
        )
      )
    }

    given [From, Value](using
        Bindable.Lt[From, Value]
    ): InterpolationConverter[From, Binding[Value]] = _.toBinding

    given [FromText](using
        Bindable.Lt[FromText, String]
    ): InterpolationConverter[FromText, Binding[Text]] = { from =>
      from.toBinding.map(document.createTextNode)
    }

    given [FromFunction, A, R](using
        Bindable.Lt[FromFunction, A => R]
    ): InterpolationConverter[FromFunction, Binding[js.Function1[A, R]]] = {
      from =>
        from.toBinding.map(identity)
    }

    given [FromSeq](using
        bindableSeq: BindableSeq.Lt[FromSeq, Node]
    ): InterpolationConverter[FromSeq, Binding.Stable[DocumentFragment]] = {
      from =>
        fragmentBinding(bindableSeq.toBindingSeq(from))
    }
  end InterpolationConverter

  private[html] def nodeBinding[A](
      value0: A,
      mountPoints: scalajs.js.Array[Binding[Unit]]
  ): Binding.Stable[A] = new Binding.Stable[A]:
    val value = value0

    private var referenceCount = 0
    protected def addChangedListener(
        listener: Binding.ChangedListener[A]
    ): Unit = {
      if referenceCount == 0 then mountPoints.foreach(_.watch())
      end if
      referenceCount += 1
    }
    protected def removeChangedListener(
        listener: Binding.ChangedListener[A]
    ): Unit =
      referenceCount -= 1
      if referenceCount == 0 then mountPoints.foreach(_.unwatch())
      end if
    end removeChangedListener
  end nodeBinding

  extension (inline stringContext: StringContext)
    transparent inline def html(
        inline args: Any*
    ): Any =
      ${
        Macros.html('stringContext, 'args)
      }

  def render[A](parent: ParentNode & Node, childNodes: A)(using
      bindableSeq: BindableSeq.Lt[A, Node]
  ): Unit =
    mountChildNodes(parent, bindableSeq.toBindingSeq(childNodes)).watch()
}
