package com.msop.player.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WebDAV Multi-Status 解析（P7-07）。纯 JVM 单测。 */
class WebDavMultiStatusParserTest {

    @Test
    fun `解析目录与文件 按 localName 不认前缀`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/music/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>music</d:displayname>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/music/Album/a.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>a.flac</d:displayname>
                    <d:resourcetype/>
                    <d:getcontentlength>34929358</d:getcontentlength>
                    <d:getlastmodified>Wed, 21 Oct 2015 07:28:00 GMT</d:getlastmodified>
                    <d:getcontenttype>audio/flac</d:getcontenttype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavMultiStatusParser.parse(xml)
        assertEquals(2, entries.size)

        assertTrue(entries[0].isCollection)
        assertEquals("music", entries[0].displayName)

        val file = entries[1]
        assertFalse(file.isCollection)
        assertEquals("/dav/music/Album/a.flac", file.href)
        assertEquals(34_929_358L, file.contentLength)
        assertEquals("audio/flac", file.mimeType)
        assertEquals(1_445_412_480L, file.lastModifiedSeconds)
    }

    @Test
    fun `只认 200 的 propstat`() {
        val xml = """
            <multistatus xmlns="DAV:">
              <response>
                <href>/a.flac</href>
                <propstat>
                  <prop><displayname>错误组</displayname></prop>
                  <status>HTTP/1.1 404 Not Found</status>
                </propstat>
                <propstat>
                  <prop><displayname>正确名字</displayname><resourcetype/></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val entry = WebDavMultiStatusParser.parse(xml).single()
        assertEquals("正确名字", entry.displayName)
        assertEquals(0L, entry.lastModifiedSeconds)
        assertNull(entry.contentLength)
    }

    @Test
    fun `缺失 href 的响应被丢弃`() {
        val xml = """
            <multistatus xmlns="DAV:">
              <response>
                <propstat><prop><displayname>x</displayname></prop></propstat>
              </response>
            </multistatus>
        """.trimIndent()

        assertTrue(WebDavMultiStatusParser.parse(xml).isEmpty())
    }

    @Test
    fun `坏 XML 返回空列表而不是抛异常`() {
        assertTrue(WebDavMultiStatusParser.parse("<multistatus><response>").isEmpty())
        assertTrue(WebDavMultiStatusParser.parse("not xml at all").isEmpty())
        assertTrue(WebDavMultiStatusParser.parse("").isEmpty())
    }

    @Test
    fun `禁止 DOCTYPE 防止实体注入`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <multistatus xmlns="DAV:"><response><href>&xxe;</href></response></multistatus>
        """.trimIndent()

        assertTrue(WebDavMultiStatusParser.parse(xml).isEmpty())
    }
}
