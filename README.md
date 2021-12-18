# html.scala

[![Scala CI](https://github.com/Atry/html.scala/actions/workflows/scala.yml/badge.svg)](https://github.com/Atry/html.scala/actions/workflows/scala.yml)
<a href="https://search.maven.org/search?q=g:com.yang-bo%20a:html_*"><img src="https://img.shields.io/maven-central/v/com.yang-bo/html_sjs0.6_2.13.svg?label=libraryDependencies+%2B=+%22com.yang-bo%22+%25%25%25+%22html%22+%25"/></a>
[![Scaladoc](https://javadoc.io/badge/com.yang-bo/html_sjs0.6_2.13.svg?label=Scaladoc)](https://javadoc.io/page/com.yang-bo/html_sjs0.6_2.13/latest/org/lrng/binding/html.html)


This **html.scala** library provides the `@html` for creating reactive HTML templates. It is a reimplementation of `@dom` annotation in [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala), delicately minimizing the generated code size. The typing rules are also improved, preventing red marks in IDEs.

## Installation

Add the following settings in the `build.sbt` for your Scala.js project.
```
// Enable macro annotations by setting scalac flags for Scala 2.13
scalacOptions ++= {
  import Ordering.Implicits._
  if (VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L)) {
    Seq("-Ymacro-annotations")
  } else {
    Nil
  }
}

// Enable macro annotations by adding compiler plugins for Scala 2.12
libraryDependencies ++= {
  import Ordering.Implicits._
  if (VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L)) {
    Nil
  } else {
    Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
  }
}

libraryDependencies += "com.yang-bo" %%% "html" % "latest.release"
```

## Getting started

The `@html` annotation enables XHTML literals, which return some subtypes of `NodeBinding[Node]`s or `NodeBindingSeq[Node]`, according to the tag name of the XHTML literals.

``` scala
import com.thoughtworks.binding.Binding, Binding._
import org.lrng.binding.html, html.NodeBinding
import org.scalajs.dom.raw._

@html def tagPicker(tags: Vars[String]): NodeBindingSeq[HTMLDivElement] = {
  val inputBinding: NodeBinding[HTMLInputElement] = <input type="text"/>
  val addHandler = { event: Event =>
    val input: HTMLInputElement = inputBinding.value
    if (input.value != "" && !tags.value.contains(input.value)) {
      tags.value += input.value
      input.value = ""
    }
  }
  <div>{
    for (tag <- tags) yield <q>
      { tag }
      <button onclick={ event: Event => tags.value -= tag }>x</button>
    </q>
  }</div>
  <div>{ inputBinding.bind } <button onclick={ addHandler }>Add</button></div>
}
```

Those XHTML literals supports XML interpolation, which are data binding expressions in brackets. Data binding expressions are arbitrary Scala code, which might contains some `.bind` calls to other `Binding` (including `Var`, `NodeBinding`, etc) dependencies. Whenever the value of a dependency is changes, the value of dependent will be changed accordingly. In addition, data binding expressions can contain some `for` comprehension on `BindingSeq`s (including `Vars`, `NodeBindingSeq`, etc) to create new `BindingSeq`s.

Then, the `NodeBinding` or `NodeBindingSeq` created from `@html` annotated function can be used in another `@html` annotated function:

``` scala
@html def root: NodeBinding[HTMLDivElement] = {
  val tags = Vars("initial-tag-1", "initial-tag-2")
  <div>
    { tagPicker(tags) }
    <hr/>
    <h3>All tags:</h3>
    <ol>{ for (tag <- tags) yield <li>{ tag }</li> }</ol>
  </div>
}
```

Or, you can render the `NodeBinding` or `NodeBindingSeq` onto the HTML document.

``` scala
import org.scalajs.dom.document
html.render(document.body, root)
```

Now the rendering result continuously changes according the data sources changes.

## What's different from `@dom`

### The return type of the annotated function

A `@dom` annotated function always wraps the return type to a `Binding`.

``` scala
@dom def i: Binding[Int] = 42
```

In contrast, a `@html` annotated function never change the return type.

``` scala
@html def i: Int = 42
```

### The type of XHTML literals

The type of XHTML literals in a `@dom` annotated function is a subtype of `Node` or `BindingSeq[Node]`, then they will be wrapped in a `Binding` when returning.

``` scala
@dom def f: Binding[BindingSeq[Node]] = {
  val myDiv: HTMLDivElement = <div></div>
  val myNodes: BindingSeq[Node] = <hr/><br/><div>{myDiv}</div>
  myNodes
}
```

In contrast, the type of XHTML literals in a `@dom` annotated function is a subtype of `NodeBinding[Node]` or a subtype of `NodeBindingSeq[Node]`, and the types will not be changed when returning.

``` scala
@html def f: NodeBindingSeq[Node] = {
  val myDiv: NodeBinding[HTMLDivElement] = <div></div>
  val myNodes: NodeBindingSeq[Node] = <hr/><br/><div>{myDiv.bind}</div>
  myNodes
}
```

Note that `NodeBinding` is a subtype of `Binding`, and `NodeBindingSeq` is a subtype of `BindingSeq`.

Since the `@html` annotation does not change the return type any more, `.bind` is not available in an `@html` method body, unless `.bind` is in a XHTML interpolation expression. To use `.bind` in an `@html` method, explicit wrap the function to `Binding` is required.

For example, given the following code written in `@dom` method:

``` scala
@dom render(toggle: Binding[Boolean]): Binding[Node] = {
  if (toggle.bind) {
    <label>On</label>
  } else {
    <label>Off</label>
  }
}
```

To migrate it to `@html`, a `Binding` block is required, or `toggle.bind` will not compile:

``` scala
@html render(toggle: Binding[Boolean]): Binding[Node] = Binding {
  if (toggle.bind) {
    <label>On</label>.bind
  } else {
    <label>Off</label>.bind
  }
}
```

Note that XHTML literals are now `NodeBinding`s instead of raw HTML nodes. A `.bind` is required to extract the raw nodes, or the return type will become a nested type like `Binding[NodeBinding[Node]]`.

### Attributes and properties

In `@dom` annotated functions, XHTML attributes are translated to property assignments. The property name is case sensitive. If the property does not exist, an compiler error will be reported.

``` scala
// Compiles, because both `rowSpan` and `className` are valid properties
@dom def myDiv = <td rowSpan={3} className="my-class"></td>
```

``` scala
// Does not compile because the property name is rowSpan, not rowspan.
@dom def myDiv = <td rowspan={3}></td>
```

In an `@html` annotated function, an attribute will be translated to an HTML attribute instead of a DOM property if it matches the case sensitive [attribute names defined in HTML Standard](https://html.spec.whatwg.org/multipage/indices.html#attributes-3) are supported.

``` scala
// Does not compile, because neither `rowSpan` nor `className` is the exact attribute name defined in the HTML Standard.
@html def myDiv = <td rowSpan="3" className="my-class"></td>
```

``` scala
// Compiles, because both `rowspan` and `class` are defined in the HTML Standard.
@dom def myDiv = <td rowspan={"3"} class="my-class"></td>
```

In addition, an attribute with an interpolation expression will be translated to a DOM property instead of an HTML attribute. Only case sensitive [property names defined in scala-js-dom](https://www.scala-js.org/api/scalajs-dom/0.9.5/) are supported.


``` scala
// Compiles, because both `rowSpan` and `className` are defined in scala-js-dom.
@html def myDiv = <td rowSpan={3} className={"my-class"}></td>
```

To set an abitrary attribute, with or without an interpolation expression, prepend a `data:` prefix to the attribute.

``` scala
// All the following attriubtes compile
@html def myDiv = <td data:rowSpan={3.toString} data:class={"my-class"} data:custom-attribute-1="constant-value" data:custom-attribute-2={math.random.toString}></td>
```

For conditional attributes of an `@html` annotated function, wrap the value as an Option
``` scala
// When sandbox is provided resulting element will include the sandbox attribute, i.e. <iframe src="..." sandbox="..."></iframe>, otherwise it is omitted, <iframe src="..."></iframe>
@html def iframeWithOptionalSandbox(sandbox: Option[String], src: String) = 
<iframe src={src} data:sandbox={sandbox}></iframe>
```

### `id` and `local-id` attribute

There are special treatments to `id` and `local-id` attribute in `@dom`. Those treatments are removed in `@html`, as they cause red marks in IntelliJ IDEA or other IDEs.

## Custom tags

The `@html` annotation is a variety of [name based XML literals](https://github.com/GlasslabGames/nameBasedXml.scala), where the default prefix is ``org.lrng.binding.html.autoImports.`http://www.w3.org/1999/xhtml` `` instead of `xml`. You can create custom tags by provide builders for other prefix according to [the guideline for XML library vendors](https://github.com/GlasslabGames/nameBasedXml.scala#xml-library-vendors).

## Links

* [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala/)
* [Name based XML literals](https://github.com/GlasslabGames/nameBasedXml.scala)
