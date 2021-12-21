import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.{HtmlPage, HtmlTable}
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._

import scala.collection.JavaConverters._
import scala.meta._

/**
  * @author 杨博 (Yang Bo)
  */
object Generators extends AutoPlugin {
  final case class AttributeDefinition(
      interfacesByTagName: Map[ElementName, InterfaceName],
      interfacesByAttribute: Map[AttributeName, Seq[InterfaceName]],
  )

  type InterfaceName = String
  type ElementName = String
  type PropertyName = String
  type AttributeName = String
  type TypeName = String

  override def requires = PlatformDepsPlugin

  object autoImport {
    val generateAttributeFactories = taskKey[File]("Generate AttributeFactories.scala file from scala-js-dom")
    val generateElementFactories = taskKey[File]("Generate ElementFactories.scala file from HTML Living Standard")
    val generateEntityBuilders = taskKey[File]("Generate EntityBuilders.scala file from HTML Living Standard")

    val attributeDefinition = taskKey[AttributeDefinition]("Load attribute definition from HTML living standard.")
    val propertyDefinition =
      taskKey[Map[PropertyName, Seq[(InterfaceName, Type, List[Mod])]]]("Load property definition from scala-js-dom")
  }

  import autoImport._
  override def projectSettings = Seq(
    propertyDefinition := {
      object JsNativeSetter {
        private val SetterRegex = """(\w+)_=""".r
        private def pf: PartialFunction[Stat, (String, Seq[Mod], Type)] = {
          case q"..$mods var ${Pat.Var(varName)}: ${Some(tpe)} = js.native" =>
            (varName.value, mods, tpe)
          case q"..$mods def ${Term.Name(SetterRegex(defPrefix))}($value: ${Some(tpe: Type)}): $unit = js.native" =>
            (defPrefix, mods, tpe)
        }
        def unapply(stat: Stat) = pf.lift(stat)
      }
      val Some(compileReport) = updateClassifiers.value.configuration(Compile)
      val Seq(domModuleReport) = compileReport.modules.filter { moduleReport =>
        moduleReport.module.organization == "org.scala-js" && moduleReport.module.name.startsWith("scalajs-dom")
      }
      val Seq((_, sourceJar)) = domModuleReport.artifacts.filter {
        case (domArtifact, _) => domArtifact.classifier.contains("sources")
      }
      import java.util.jar.JarFile
      val jarFile = new JarFile(sourceJar)
      val parsedSource = try {
        def parseFromJar(fileName: String) = {
          val stream = jarFile.getInputStream(jarFile.getJarEntry(fileName))
          try {
            stream.parse[Source].get
          } finally {
            stream.close()
          }
        }
        Seq(parseFromJar("org/scalajs/dom/Element.scala"), parseFromJar("org/scalajs/dom/HTMLElement.scala"),
          parseFromJar("org/scalajs/dom/SVGTextElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLButtonElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLInputElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLOptGroupElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLOptionElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLSelectElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLTextAreaElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLFieldSetElement.scala"),
          parseFromJar("org/scalajs/dom/HTMLLinkElement.scala"),
          )
      } finally {
        jarFile.close()
      }
      (for {
        Source(rootStats) <- parsedSource.view
        q"package $_ { ..$packageStats }" <- rootStats.view
        Defn.Class(mods, className, _, _, template) <- packageStats.view
        if className.value.endsWith("Element")
        JsNativeSetter(setterName, methodMods, tpe) <- template.stats.view
      } yield
        setterName -> (className.value, tpe, (mods.view ++ methodMods).collect {
          case deprecatedAnnotation @ mod"@deprecated(..$_)" =>
            deprecatedAnnotation
        }.toList))
        .groupBy(_._1)
        .mapValues(_.map(_._2).toSeq)
    },
    attributeDefinition := {
      val webClient = new WebClient()
      try {
        webClient.getOptions.setJavaScriptEnabled(false)
        webClient.getOptions.setCssEnabled(false)
        val document = webClient.getPage[HtmlPage]("https://html.spec.whatwg.org/multipage/indices.html")
        try {
          val interfacesByTagName = {
            for {
              tbody <- document
                .querySelector("#element-interfaces ~ table")
                .asInstanceOf[HtmlTable]
                .getBodies
                .asScala
                .view
              row <- tbody.getRows.asScala.view
              Seq(tagNameCell, interfaceCell) = row.getCells.asScala
              tagNameCode <- tagNameCell.getElementsByTagName("code").asScala.view
            } yield tagNameCode.asText -> interfaceCell.getElementsByTagName("code").asScala.head.asText
          }.toMap
          val interfacesByAttribute = {
            for {
              tbody <- document.getElementById("attributes-1").asInstanceOf[HtmlTable].getBodies.asScala.view ++
                document.querySelector("#attributes-1 ~ table").asInstanceOf[HtmlTable].getBodies.asScala.view
              row <- tbody.getRows.asScala.view
              interfaceName <- if (row.getCell(1).asText == "HTML elements") {
                Seq("HTMLElement")
              } else {
                row
                  .getCell(1)
                  .getElementsByTagName("code")
                  .asScala
                  .view
                  .map { code =>
                    interfacesByTagName(code.asText)
                  }
              }
            } yield row.getCell(0).asText -> interfaceName
          }.groupBy(_._1).mapValues(_.map(_._2).distinct.toList)
          AttributeDefinition(interfacesByTagName, interfacesByAttribute)
        } finally {
          document.cleanUp()
        }
      } finally {
        webClient.close()
      }
    },
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
        .left
        .map(_.error)
        .toTry
        .get
      val EntityRefRegex = "&(.*);".r
      val entityDefs = for ((EntityRefRegex(entityName), Entity(_, text)) <- entityMap.view) yield {
        q"@inline def ${Term.Name(entityName)} = new TextBuilder($text)"
      }
      val geneatedAst = q"""
      package org.lrng.binding {
        import org.lrng.binding.html.NodeBinding.Constant.TextBuilder
        private[lrng] object EntityBuilders {
          ..${entityDefs.toList}
        }
      }
      """
      val file = (Compile / scalaSource).value / "org" / "lrng" / "binding" / "EntityBuilders.scala"
      val fileContent = Seq(
        "// Don't edit this file, because it is generated by `sbt generateEntityBuilders`",
        geneatedAst.syntax
      )
      IO.writeLines(file, fileContent)
      file
    },
    generateAttributeFactories := {
      val AttributeDefinition(interfacesByTagName, interfacesByAttribute) = attributeDefinition.value
      val perperties = propertyDefinition.value
      val sortedNames =
        (perperties.keySet ++ interfacesByAttribute.keySet).toArray.sorted
      val (defs, propertyOnlyDefOptions) = sortedNames.view.map {
        attributeName =>
          val propertyOnly = !interfacesByAttribute.contains(attributeName)
          val attributeTermName = {
            Term.Name(reflect.NameTransformer.encode(attributeName))
          }
          val attributeSetters = for {
            interfaceNames <- interfacesByAttribute.get(attributeName).view
            interfaceName <- interfaceNames.view
          } yield {
            val evidenceName = s"attributeSetter_$interfaceName"
            q"""
              @inline implicit def ${Term.Name(evidenceName)}:
                AttributeSetter[${Type.Name(interfaceName)}, $attributeTermName.type] =
                new AttributeSetter(${if (attributeName == "style") {
              // Workaround for IE
              q"_.style.cssText = _"
            } else {
              q"_.setAttribute($attributeName, _)"
            }})
            """
          }
          val attributeRemovers = for {
            interfaceNames <- interfacesByAttribute.get(attributeName).view
            interfaceName <- interfaceNames.view
          } yield {
            val evidenceName = s"attributeRemover_$interfaceName"
            q"""
              @inline implicit def ${Term.Name(evidenceName)}:
                AttributeRemover[${Type.Name(interfaceName)}, $attributeTermName.type] =
                new AttributeRemover(_.removeAttribute($attributeName))
            """
          }
          val propertyConstructors = for {
            setters <- perperties.get(attributeName).view
            (interfaceName, tpe, mods) <- setters.view
          } yield {
            val interfaceTypeName = Type.Name(interfaceName)
            val mountPointBuilderTermName =
              Term.Name(s"mountPointBuilder_${tpe.syntax.replace('.', '_')}_$interfaceName")
            q"""
              ..$mods implicit object $mountPointBuilderTermName extends MountPointBuilder[$interfaceTypeName, $attributeTermName.type, $tpe] {
                ..$mods def mountProperty(element: $interfaceTypeName, binding: Binding[$tpe]) = {
                  Binding.BindingInstances.map(binding)(${tpe match {
              case t"String" if attributeName == "style" =>
                // Workaround for IE
                q"element.style.cssText"
              case _ =>
                q"element.$attributeTermName"
            }} = _)
                }
              }
            """
          }
          val objectDefinition = q"""
            object $attributeTermName extends AttributeFactory.Typed {
              ..${propertyConstructors.toList}
              ..${attributeSetters.toList}
              ..${attributeRemovers.toList}
            }
          """
          if (propertyOnly) {
            q"@inline def $attributeTermName: properties.$attributeTermName.type = properties.$attributeTermName" -> Some(
              objectDefinition)
          } else {
            objectDefinition -> None
          }
      }.unzip
      val propertyOnlyDefs = propertyOnlyDefOptions.flatten
      val geneatedAst = q"""
        package org.lrng.binding {
          import scala.scalajs.js
          import org.scalajs.dom._
          import org.lrng.binding.html.NodeBinding.Interpolated.MountPointBuilder
          import org.lrng.binding.html.ElementFactory
          import org.lrng.binding.html.AttributeFactory
          import org.lrng.binding.html.NodeBinding.Constant.AttributeSetter
          import org.lrng.binding.html.NodeBinding.Constant.AttributeRemover
          import org.lrng.binding.html.elementTypes._
          import com.thoughtworks.binding.Binding
          private[lrng] object AttributeFactories {
            private[lrng] object properties {
              ..${propertyOnlyDefs.toList}
            }
            import properties._
            ..${defs.toList}
          }
        }
      """
      val file = (Compile / scalaSource).value / "org" / "lrng" / "binding" / "AttributeFactories.scala"
      val fileContent = Seq(
        "// Don't edit this file, because it is generated by `sbt generateAttributeFactories`",
        geneatedAst.syntax
      )
      IO.writeLines(file, fileContent)
      file
    },
    generateElementFactories := {
      import com.gargoylesoftware.htmlunit.WebClient
      import com.gargoylesoftware.htmlunit.html._

      import scala.collection.JavaConverters._
      val webClient = new WebClient()
      webClient.getOptions.setJavaScriptEnabled(false)
      webClient.getOptions.setCssEnabled(false)
      val HeadingTag = """h\d""".r
      val document = webClient.getPage[HtmlPage]("https://html.spec.whatwg.org/")
      val tagDefs =
        document.querySelectorAll("dl.element").asScala.view.flatMap {
          elementDl =>
            val domInterface = elementDl.querySelectorAll(".idl dfn[id$=element]").asScala match {
              case Seq(domInterfaceDfn) =>
                domInterfaceDfn.asText
              case Seq() =>
                elementDl.querySelectorAll("dd:last-child code").asScala.filter { code =>
                  Set("Uses", "Use", "Supplied by the element's author (inherits from").contains(
                    code.getPreviousSibling.asText)
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
                object ${Term.Name(tagName)} extends ElementFactory[${Type.Name(domInterface)}] {
                  @inline protected def tagName = $tagName
                }
              """
            }
        }
      val geneatedAst = q"""
        package org.lrng.binding {
          import org.scalajs.dom.document
          import org.scalajs.dom._
          import org.lrng.binding.html.ElementFactory
          import org.lrng.binding.html.elementTypes._
          private[lrng] object ElementFactories {
            ..${tagDefs.toList}
          }
        }
      """
      val file = (Compile / scalaSource).value / "org" / "lrng" / "binding" / "ElementFactories.scala"
      val fileContent = Seq(
        "// Don't edit this file, because it is generated by `sbt generateElementFactories`",
        geneatedAst.syntax
      )
      IO.writeLines(file, fileContent)
      file
    }
  )
}
