package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class FeedParserAndModelsTest {

    @Test
    fun `sloppyLinkToStrictURL handles normal and protocol-relative URLs`() {
        val normalUrl = sloppyLinkToStrictURL("https://news.ycombinator.com/rss")
        assertEquals("https", normalUrl.protocol)
        assertEquals("news.ycombinator.com", normalUrl.host)
        assertEquals("/rss", normalUrl.path)

        val schemeLessUrl = sloppyLinkToStrictURL("feedburner.com/testfeed")
        assertEquals("http", schemeLessUrl.protocol)
        assertEquals("feedburner.com", schemeLessUrl.host)
    }

    @Test
    fun `sloppyLinkToStrictURLNoThrows never throws on empty or invalid string`() {
        val emptyUrl = sloppyLinkToStrictURLNoThrows("")
        assertNotNull(emptyUrl)

        val invalidUrl = sloppyLinkToStrictURLNoThrows(":::invalid-url:::")
        assertNotNull(invalidUrl)
    }

    @Test
    fun `Share text URL regex extraction`() {
        val shareText = "Check out this awesome RSS feed: https://archlinux.org/feeds/news/ for latest updates!"
        val urlRegex = Regex("""(https?://[^\s]+)""")
        val match = urlRegex.find(shareText)?.value

        assertNotNull(match)
        assertEquals("https://archlinux.org/feeds/news/", match)
    }

    @Test
    fun `Feed and Article data model consistency`() {
        val feedUrl = URL("https://example.com/rss.xml")
        val feed = Feed(
            url = feedUrl,
            title = "Example News",
            description = "Top stories",
            isEnabled = true,
            sourceType = "rss"
        )

        assertEquals("Example News", feed.title)
        assertEquals("Top stories", feed.description)
        assertTrue(feed.isEnabled)
        assertEquals("rss", feed.sourceType)
        assertEquals("https://example.com/rss.xml", feed.url.toString())
    }
}
