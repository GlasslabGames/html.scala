package org.lrng.binding

import com.thoughtworks.binding.Binding.Var
import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class Issue17 extends AnyFreeSpec with Matchers {
  "attributes should support Option[String]" in {

    val changing: Var[Option[String]] = Var(None)
    @html val changingContainer = <div><div id={changing.bind}></div></div>
    changingContainer.watch()
    changingContainer.value.innerHTML should be("<div></div>")
    changing.value = Some("myId")
    changingContainer.value.innerHTML should be("<div id=\"myId\"></div>")
    changing.value = None
    changingContainer.value.innerHTML should be("<div></div>")

    @html val dataPrefixedChangingContainer = <div><div data:optional-attr={changing.bind}></div></div>
    dataPrefixedChangingContainer.watch()
    dataPrefixedChangingContainer.value.innerHTML should be("<div></div>")
    changing.value = Some("optionalValue")
    dataPrefixedChangingContainer.value.innerHTML should be("<div optional-attr=\"optionalValue\"></div>")
    changing.value = None
    dataPrefixedChangingContainer.value.innerHTML should be("<div></div>")

  }
}
