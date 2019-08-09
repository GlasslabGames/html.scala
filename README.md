# html.scala

This **html.scala** library provides the `@html` for creating reactive HTML templates. It is a reimplementation of `@dom` annotation in [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala), delicately optimized for minimize the generated code size.

## Getting started

The `@html` annotation enables XHTML literals, which return some subtypes of `NodeBinding[Node]`s or `NodeBindingSeq[Node]`, according to the tag name of the XHTML literals.

``` scala
import com.thoughtworks.binding.Binding, Binding._
import com.concentricsky.html, html.NodeBinding
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

### Attributes and properties

In `@dom` annotated functions, XHTML attributes are translated to property assignments. If the property does not exist, an compiler error will be reported.

``` scala
// Compile error!
@dom def myDiv = <div non-exists-attribute="1"></div>
```

In `@html` annotated functions, an attribute without an interpolation expression will be translated to HTML attribute instead of a property. No compiler error will be reported for any attribute name.

``` scala
// OK
@html def myDiv = <div non-exists-attribute="1"></div>
```

However, an attribute with an interpolation expression are still type checked as `@dom`.

``` scala
// Compile error!
@html def myDiv = <div non-exists-attribute={1}></div>
```

`@html` does not support `data:` prefix by default.

## Custom tags

The `@html` annotation is a variety of [name based XML literals](https://github.com/Atry/nameBasedXml.scala), where the default prefix is <code>com.concentricsky.html.autoImports.&#96;http://www.w3.org/1999/xhtml&#96;</code> instead of `xml`. You can create custom tags by provide builders for other prefix according to [the guideline for XML library vendors](https://github.com/Atry/nameBasedXml.scala#xml-library-vendors).

## Links

* [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala/)
* [Name based XML literals](https://github.com/Atry/nameBasedXml.scala)
