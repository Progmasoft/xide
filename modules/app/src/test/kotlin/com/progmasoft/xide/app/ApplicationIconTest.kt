/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

class ApplicationIconTest {
  private fun iconBytes(): ByteArray =
    assertNotNull(
      WorkspaceSession::class.java.getResourceAsStream(APPLICATION_ICON_RESOURCE),
      "the application icon must be packaged beside the application classes",
    ).use { it.readBytes() }

  /** Parses without resolving DTDs or external entities, so the test itself cannot fetch anything. */
  private fun parse(bytes: ByteArray): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    return factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
  }

  @Test
  fun iconIsAPackagedSquareVectorImage() {
    val root = parse(iconBytes())

    assertEquals("svg", root.localName)
    assertEquals("http://www.w3.org/2000/svg", root.namespaceURI)
    val viewBox = root.getAttribute("viewBox").trim().split(Regex("""\s+""")).map(String::toDouble)
    assertEquals(4, viewBox.size)
    assertEquals(viewBox[2], viewBox[3], "the icon must be square")
    assertTrue(viewBox[2] > 0.0)
  }

  @Test
  fun iconIsSelfContained() {
    // The icon is decoded at start-up. It must not be able to load other resources or run script.
    val root = parse(iconBytes())
    val elements = root.getElementsByTagNameNS("*", "*")
    val forbidden = setOf("script", "image", "foreignObject", "use", "a", "style", "iframe", "animate", "set")
    for (index in 0 until elements.length) {
      val element = elements.item(index) as Element
      assertTrue(element.localName !in forbidden, "icon contains a <${element.localName}> element")
      val attributes = element.attributes
      for (attribute in 0 until attributes.length) {
        val node = attributes.item(attribute)
        val name = node.localName ?: node.nodeName
        assertTrue(!name.startsWith("on", ignoreCase = true), "icon contains the event attribute $name")
        if (name == "href") {
          assertTrue(node.nodeValue.startsWith("#"), "icon references the external resource ${node.nodeValue}")
        }
        assertTrue(
          !Regex("""url\(\s*['"]?(?!#)""").containsMatchIn(node.nodeValue),
          "icon attribute $name references an external resource",
        )
      }
    }
  }
}
