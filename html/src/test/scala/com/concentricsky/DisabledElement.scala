package org.lrng.binding

import com.thoughtworks.binding.Binding
import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DisabledElement extends AnyFreeSpec with Matchers {
  "disabled button should compile" in {
    @html val shouldCompile = Binding {
      <button name={"aasdf"} autofocus={true} disabled={false}></button>.bind
    }
    @html
    val container = <div>{shouldCompile}</div>
    container.watch()
    container.value.innerHTML should be("""<button name="aasdf" autofocus=""></button>""")
  }

  "disabled input should compile" in {
    @html val shouldCompile = Binding {
      <input disabled={false}></input>.bind
    }
    @html
    val container = <div>{shouldCompile}</div>
    container.watch()
    container.value.innerHTML should be("""<input>""")
  }

  "enabled input should compile" in {
    @html val shouldCompile = Binding {
      <input disabled={true}></input>.bind
    }
    @html
    val container = <div>{shouldCompile}</div>
    container.watch()
    container.value.innerHTML should be("""<input disabled="">""")
  }
}
