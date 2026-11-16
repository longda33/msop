package com.msop.player.core.network

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * WebDAV `207 Multi-Status` 响应解析（P7-07）。纯 JVM，可单测。
 *
 * 只取同步需要的五个属性：`displayname`、`resourcetype`、`getcontentlength`、
 * `getlastmodified`、`getcontenttype`。不同服务器（Nextcloud / nginx / rclone）
 * 的命名空间前缀与属性顺序都不一致，所以全程按 **localName** 匹配，不认前缀。
 *
 * 解析失败返回空列表而不是抛异常：列目录失败由 [WebDavClient] 转成可读错误，
 * 不能让一段坏 XML 直接把同步流程崩掉。
 */
object WebDavMultiStatusParser {

    data class Entry(
        val href: String,
        val displayName: String?,
        val isCollection: Boolean,
        val contentLength: Long?,
        val lastModifiedSeconds: Long,
        val mimeType: String?,
    )

    fun parse(xml: String): List<Entry> {
        if (xml.isBlank()) return emptyList()
        val document = runCatching { parseDocument(xml) }.getOrNull() ?: return emptyList()
        val root = document.documentElement ?: return emptyList()

        val responses = mutableListOf<Element>()
        collect(root, "response", responses)
        return responses.mapNotNull(::parseResponse)
    }

    private fun parseResponse(response: Element): Entry? {
        val href = children(response, "href").firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        // 多个 propstat 时只认 200 那一组；没有状态行（少数服务器）就退回第一组。
        val propstats = children(response, "propstat")
        val chosen = propstats.firstOrNull { propstat ->
            children(propstat, "status").firstOrNull()
                ?.textContent
                ?.contains(" 200 ")
                ?: false
        } ?: propstats.firstOrNull()
        val prop = chosen?.let { children(it, "prop").firstOrNull() } ?: return null

        val properties = children(prop).associateBy { localName(it) }

        val resourceType = properties["resourcetype"]
        val isCollection = resourceType != null && containsElement(resourceType, "collection")

        return Entry(
            href = href,
            displayName = properties["displayname"]?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            isCollection = isCollection,
            contentLength = properties["getcontentlength"]?.textContent?.trim()?.toLongOrNull()
                ?.takeIf { it >= 0L },
            lastModifiedSeconds = properties["getlastmodified"]?.textContent
                ?.let(::parseHttpDate)
                ?: 0L,
            mimeType = properties["getcontenttype"]?.textContent?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseDocument(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        // 与 TTML 解析同样的防线：歌词/目录响应都来自外部服务器，不解析 DOCTYPE。
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.trimStart().toByteArray(Charsets.UTF_8)))
    }

    /** `Wed, 21 Oct 2015 07:28:00 GMT`；个别服务器给 ISO-8601，两种都兜住。 */
    private fun parseHttpDate(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        runCatching {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
        }
        return runCatching { Instant.parse(text).epochSecond }.getOrDefault(0L)
    }

    private fun collect(element: Element, name: String, out: MutableList<Element>) {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            if (localName(child) == name) out += child
            collect(child, name, out)
        }
    }

    private fun containsElement(element: Element, name: String): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && (localName(child) == name || containsElement(child, name))) {
                return true
            }
            if (child is Node && child.nodeType == Node.TEXT_NODE) continue
        }
        return false
    }

    private fun children(element: Element, name: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && localName(child) == name) result += child
        }
        return result
    }

    private fun children(element: Element): List<Element> {
        val result = mutableListOf<Element>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) result += child
        }
        return result
    }

    private fun localName(element: Element): String =
        element.localName ?: element.tagName.substringAfter(':')
}
