# html.scala

This **html.scala** library provides the `@html` for creating reactive HTML templates. It is a reimplementation of `@dom` annotation in [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala), delicately optimized for minimize the generated code size. The typing rules are also improved, preventing red marks in IDEs.

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

In `@dom` annotated functions, XHTML attributes are translated to property assignments. If the property does not exist, an compiler error will be reported. `@html` handles attributes as same as `@dom`, except `htmlFor` and `className` attributes are not supported any more.

``` scala
// compiles
@dom def render = <div className="my-div"></div>
```
``` scala
// compiles
@dom def render = <div class="my-div"></div>
```
``` scala
// compiles
@dom def render = <label htmlFor="my-radio"></label>
```
``` scala
// compiles
@dom def render = <label for="my-radio"></label>
```
``` scala
// does not compile
@html def render = <div className="my-div"></div>
```
``` scala
// compiles
@html def render = <div class="my-div"></div>
```
``` scala
// does not compile
@html def render = <label htmlFor="my-radio"></label>
```
``` scala
// compiles
@html def render = <label for="my-radio"></label>
```


`@html` supports `data:` prefix for dynamic named attributes as `@dom` did.

### `id` and `local-id` attribute

There are special treatments to `id` and `local-id` attribute in `@dom`. Those treatments are removed in `@html`, as they cause red marks in IntelliJ IDEA or other IDEs.

## Custom tags

The `@html` annotation is a variety of [name based XML literals](https://github.com/Atry/nameBasedXml.scala), where the default prefix is ``com.concentricsky.html.autoImports.`http://www.w3.org/1999/xhtml` `` instead of `xml`. You can create custom tags by provide builders for other prefix according to [the guideline for XML library vendors](https://github.com/Atry/nameBasedXml.scala#xml-library-vendors).

## Links

* [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala/)
* [Name based XML literals](https://github.com/Atry/nameBasedXml.scala)
