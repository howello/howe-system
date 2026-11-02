package com.howe.share.service;

import com.howe.share.storage.ShareStorageRouter;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 分享原始文件的内联预览策略，不信任源站 Content-Type。 */
@Slf4j
public final class ShareFilePolicy {
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"), Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"));

    /** 禁止实例化策略工具。 */
    private ShareFilePolicy() {
    }

    /** 根据扩展名与文件头返回安全内联类型；未知或不匹配时仅允许附件下载。 */
    public static String inlineType(String name, byte[] header) {
        String ext = ShareStorageRouter.extOf(name);
        if (java.util.Set.of("txt", "md", "markdown", "log", "csv").contains(ext)) return "text/plain; charset=UTF-8";
        String text = new String(header, StandardCharsets.ISO_8859_1);
        boolean valid = switch (ext) {
            case "png" -> text.startsWith("\u0089PNG\r\n\u001a\n");
            case "jpg", "jpeg" -> header.length >= 3 && (header[0] & 255) == 255
                    && (header[1] & 255) == 216 && (header[2] & 255) == 255;
            case "gif" -> text.startsWith("GIF87a") || text.startsWith("GIF89a");
            case "webp" -> text.startsWith("RIFF") && text.length() >= 12 && text.substring(8, 12).equals("WEBP");
            case "pdf" -> text.startsWith("%PDF-");
            case "mp4" -> text.length() >= 8 && text.substring(4, 8).equals("ftyp");
            case "mp3" -> text.startsWith("ID3") || header.length >= 2 && (header[0] & 255) == 255
                    && (header[1] & 224) == 224;
            case "wav" -> text.startsWith("RIFF") && text.length() >= 12 && text.substring(8, 12).equals("WAVE");
            case "ogg" -> text.startsWith("OggS");
            default -> false;
        };
        return valid ? TYPES.get(ext) : null;
    }
}
