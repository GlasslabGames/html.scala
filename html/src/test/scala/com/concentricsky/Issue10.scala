package org.lrng.binding
import com.thoughtworks.binding.Binding
import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
class Issue10 extends AnyFreeSpec with Matchers {
  "bind method for an HTML literal should compile" in {
    @html val shouldCompile = Binding {
      <div innerHTML={"<span>Span InnerHTML</span>"}></div>.bind
    }
    @html
    val container = <div>{shouldCompile}</div>
    container.watch()
    container.value.innerHTML should be("<div><span>Span InnerHTML</span></div>")
  }
}
