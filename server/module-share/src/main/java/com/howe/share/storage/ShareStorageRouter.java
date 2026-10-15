package com.howe.share.storage;

import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.constant.ShareConstants;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 分享文件存储路由
 *
 * <p>在创建分享时决策一次：文件字节落本地磁盘还是 R2。规则（扩展名优先，content-type 辅助）：</p>
 * <ol>
 *   <li>字节数超过 {@code share.cache.localMaxSize} → R2；</li>
 *   <li>{@code video/*} 或 {@code audio/*} → R2；</li>
 *   <li>扩展名在 {@code share.cache.docExts} 白名单内 → 本地；</li>
 *   <li>其余（未知格式）→ R2 兜底，防大文件落本地。</li>
 * </ol>
 *
 * @author howe
 */
@Component
public class ShareStorageRouter {

    /** 本地缓存大小上限默认值（10M） */
    private static final long DEFAULT_LOCAL_MAX_SIZE = 10485760L;

    /** 文档白名单默认值 */
    private static final String DEFAULT_DOC_EXTS = "md,pdf,txt,doc,docx,xls,xlsx,ppt,pptx";

    /**
     * 决策存储位置
     *
     * @param size        字节数
     * @param contentType MIME 类型，可为空
     * @param fileName    文件名
     * @return {@link ShareConstants#STORAGE_LOCAL} 或 {@link ShareConstants#STORAGE_R2}
     */
    public String route(long size, String contentType, String fileName) {
        long localMaxSize = ConfigUtils.getInt(ConfigConstants.SHARE_CACHE_LOCAL_MAX_SIZE,
                (int) DEFAULT_LOCAL_MAX_SIZE);
        if (size > localMaxSize) {
            return ShareConstants.STORAGE_R2;
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.startsWith("video/") || type.startsWith("audio/")) {
            return ShareConstants.STORAGE_R2;
        }
        String ext = extOf(fileName);
        if (!ext.isEmpty() && docExts().contains(ext)) {
            return ShareConstants.STORAGE_LOCAL;
        }
        return ShareConstants.STORAGE_R2;
    }

    /**
     * 取小写扩展名（不含点）
     *
     * @param fileName 文件名
     * @return 扩展名；无扩展名时返回空串
     */
    public static String extOf(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    /**
     * 解析文档白名单
     */
    private Set<String> docExts() {
        String raw = ConfigUtils.getString(ConfigConstants.SHARE_CACHE_DOC_EXTS, DEFAULT_DOC_EXTS);
        Set<String> result = new HashSet<>();
        if (StrUtil.isBlank(raw)) {
            return result;
        }
        for (String part : raw.split(",")) {
            String ext = part.trim().toLowerCase();
            if (!ext.isEmpty()) {
                result.add(ext);
            }
        }
        return result;
    }
}
