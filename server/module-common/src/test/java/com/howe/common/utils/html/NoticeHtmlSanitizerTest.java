package com.howe.common.utils.html;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import static org.junit.jupiter.api.Assertions.*;

/** 公告存储边界的富文本回归。 */
class NoticeHtmlSanitizerTest {
    /** 保留正常排版和图片，拒绝事件、SVG 与混淆的危险协议。 */
    @Test
    void preservesFormattingWithoutActiveContent() {
        String clean = NoticeHtmlSanitizer.clean("<p class='ql-align-center'><strong>通知</strong><img src='https://example.com/a.png' onerror='x()'></p>"
                + "<a href='jav&#x61;script:alert(1)'>链接</a><svg onload='x()'></svg><script>x()</script>");
        var body = Jsoup.parseBodyFragment(clean).body();
        assertEquals("通知", body.selectFirst("strong").text());
        assertTrue(body.selectFirst("p").hasClass("ql-align-center"));
        assertEquals("https://example.com/a.png", body.selectFirst("img").attr("src"));
        assertFalse(body.selectFirst("img").hasAttr("onerror"));
        assertFalse(body.selectFirst("a").hasAttr("href"));
        assertTrue(body.select("script,svg").isEmpty());
    }
}
