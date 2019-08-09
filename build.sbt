name := "html"

ThisBuild / organization := "com.concentricsky"

enablePlugins(ScalaJSBundlerPlugin)

enablePlugins(ScalaJSPlugin)

enablePlugins(Example)

import scala.meta._

exampleSuperTypes += ctor"_root_.org.scalatest.Inside"

libraryDependencies += "com.thoughtworks.binding" %%% "binding" % "11.8.1"

libraryDependencies += "com.thoughtworks.binding" %%% "bindable" % "1.1.0"

libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.8" % Test

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.7"

dependsOn(RootProject(file("nameBasedXml.scala")))

scalacOptions in Test += "-Xxml:-coalescing"

requireJsDomEnv in Test := true

installJsdom / version  := "15.1.1"

val generateEntityBuilders = taskKey[File]("Generate EntityBuilders.scala file from HTML Living Standard")

generateEntityBuilders := {
  import _root_.io.circe.generic.auto._
  import com.softwaremill.sttp._
  import com.softwaremill.sttp.circe._
  implicit val backend = HttpURLConnectionBackend()
  final case class Entity(codepoints: Seq[Int], characters: String)
  val entityMap = sttp
      .get(uri"https://html.spec.whatwg.org/entities.json")
      .response(asJson[collection.immutable.SortedMap[String, Entity]])
      .send()
      .unsafeBody
      .left.map(_.error)
      .toTry
      .get
  val EntityRefRegex = "&(.*);".r
  val entityDefs = for ((EntityRefRegex(entityName), Entity(_, text)) <- entityMap.view) yield {
      q"@inline def ${Term.Name(entityName)} = new TextBuilder($text)"
  }
  val geneatedAst = q"""
  package com.concentricsky {
    import com.concentricsky.html.NodeBinding.Constant.TextBuilder
    private[concentricsky] object EntityBuilders {
      ..${entityDefs.toList}
    }
  }
  """
  val file = (Compile / scalaSource).value / "com" / "concentricsky" / "EntityBuilders.scala"
  val fileContent = Seq(
    "// Don't edit this file, because it is generated by `sbt generateEntityBuilders`",
    geneatedAst.syntax
  )
  IO.writeLines(file, fileContent)
  file
}

val generateElementBuilders = taskKey[File]("Generate ElementBuilders.scala file from HTML Living Standard")

generateElementBuilders := {
  import com.gargoylesoftware.htmlunit.WebClient
  import com.gargoylesoftware.htmlunit.html._
  import scala.collection.JavaConverters._
  val webClient = new WebClient()
  webClient.getOptions().setJavaScriptEnabled(false)
  webClient.getOptions().setCssEnabled(false)
  val HeadingTag = """h\d""".r
  val document = webClient.getPage[HtmlPage]("https://html.spec.whatwg.org/")
  val tagDefs = document.querySelectorAll("dl.element").asScala.view.flatMap { elementDl =>
    val domInterface = elementDl.querySelectorAll(".idl dfn[id$=element]").asScala match {
      case Seq(domInterfaceDfn) =>
          domInterfaceDfn.asText
      case Seq() =>
        elementDl.querySelectorAll("dd:last-child code").asScala.filter { code =>
          Set("Uses", "Use", "Supplied by the element's author (inherits from").contains(code.getPreviousSibling.asText)
        } match {
          case Seq(code) =>
            code.asText
          case _ =>
            throw new MessageOnlyException(
              s"No IDL interfaces found.\n${elementDl.asXml}" 
            )
          }
      case _ =>
        throw new MessageOnlyException(
          s"Multiple IDL interfaces found.\n${elementDl.asXml}" 
        )
    }
    val Some(heading) = Iterator.iterate(elementDl)(_.getPreviousElementSibling).find { element =>
      HeadingTag.unapplySeq(element.getLocalName).isDefined
    }
    heading.querySelectorAll("dfn").asScala.view.map { elementDfn =>
      val tagName = elementDfn.asText
      q"""
        @inline def ${Term.Name(tagName)} = 
          new AttributeBuilder(document.createElement($tagName).asInstanceOf[${Type.Name(domInterface)}])
      """
    }
  }
  val geneatedAst = q"""
  package com.concentricsky {
    import org.scalajs.dom.document
    import org.scalajs.dom.raw._
    import com.concentricsky.html.NodeBinding.Constant.AttributeBuilder
    import com.concentricsky.html.elementTypes._
    private[concentricsky] object ElementBuilders {
      ..${tagDefs.toList}
    }
  }
  """
  val file = (Compile / scalaSource).value / "com" / "concentricsky" / "ElementBuilders.scala"
  val fileContent = Seq(
    "// Don't edit this file, because it is generated by `sbt generateElementBuilders`",
    geneatedAst.syntax
  )
  IO.writeLines(file, fileContent)
  file
}
