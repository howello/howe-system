package com.howe.share.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** 原始分享文件直接访问的类型边界。 */
class ShareFilePolicyTest {
    /** HTML、SVG 和伪 PDF 不能进入内联路径，真实 PDF 与纯文本可以预览。 */
    @Test
    void rejectsActiveContentAndSpoofedTypes() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertNull(ShareFilePolicy.inlineType("a.html", html));
        assertNull(ShareFilePolicy.inlineType("a.svg", html));
        assertNull(ShareFilePolicy.inlineType("a.pdf", html));
        assertEquals("application/pdf", ShareFilePolicy.inlineType("a.pdf", "%PDF-1.7".getBytes()));
        assertEquals("text/plain; charset=UTF-8", ShareFilePolicy.inlineType("a.md", html));
    }
}
