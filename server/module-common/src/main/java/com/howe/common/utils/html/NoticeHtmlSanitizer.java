package com.howe.common.utils.html;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** 公告富文本安全边界，保留排版和图片，拒绝脚本、事件属性及危险协议。 */
@Slf4j
public final class NoticeHtmlSanitizer {
    private static final Safelist POLICY = Safelist.relaxed()
            .addTags("span", "hr", "s")
            .addAttributes(":all", "class")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .removeProtocols("a", "href", "ftp");

    /** 禁止实例化净化工具。 */
    private NoticeHtmlSanitizer() {
    }

    /** 净化公告 HTML，空入参保持为空，供新增和局部更新共用。 */
    public static String clean(String html) {
        return html == null ? null : Jsoup.clean(html, "", POLICY, new Document.OutputSettings().prettyPrint(false));
    }
}
