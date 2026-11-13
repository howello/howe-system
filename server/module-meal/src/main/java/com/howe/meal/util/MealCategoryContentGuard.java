package com.howe.meal.util;

import java.util.regex.Pattern;

/**
 * 分类文本内容校验器
 *
 * <p>固定提示词要求分类名称、说明与依据是纯文本且长度受限，但模型输出不可完全信任：
 * AI 润色结果在落库前必须再经过一次本地校验，避免 HTML、Markdown 链接、URL
 * 或超长文本被直接写入数据库。</p>
 *
 * <p>规则与 {@code MealCategoryReviewServiceImpl#FIXED_RULES} 保持一致：</p>
 * <ul>
 *   <li>名称：1–12 个字符（按 code point 计数）；</li>
 *   <li>说明：1–60 个字符；</li>
 *   <li>依据：1–300 个字符；</li>
 *   <li>三者均不得包含 HTML 标签、Markdown 链接或 http(s) URL。</li>
 * </ul>
 *
 * @author howe
 */
public final class MealCategoryContentGuard {

    /** 名称最大字符数 */
    private static final int MAX_NAME_LENGTH = 12;
    /** 说明最大字符数 */
    private static final int MAX_DESCRIPTION_LENGTH = 60;
    /** 依据最大字符数 */
    private static final int MAX_BASIS_LENGTH = 300;

    /** HTML 标签 */
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    /** Markdown 链接 */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*]\\([^)]*\\)");
    /** URL（http/https/www） */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s]+");

    private MealCategoryContentGuard() {
    }

    /**
     * 校验名称、说明与依据
     *
     * @param name        分类名称
     * @param description 分类说明
     * @param basis       分类依据
     * @return 第一条违规说明；全部合规返回 null
     */
    public static String violation(String name, String description, String basis) {
        String nameError = validateField("分类名称", name, MAX_NAME_LENGTH);
        if (nameError != null) {
            return nameError;
        }
        String descriptionError = validateField("分类说明", description, MAX_DESCRIPTION_LENGTH);
        if (descriptionError != null) {
            return descriptionError;
        }
        return validateField("分类依据", basis, MAX_BASIS_LENGTH);
    }

    /**
     * 校验单个字段
     *
     * @param label     字段中文名
     * @param value     字段值
     * @param maxLength 最大字符数（code point）
     * @return 违规说明；合规返回 null
     */
    private static String validateField(String label, String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return label + "不能为空";
        }
        if (containsMarkup(text)) {
            return label + "只能使用纯文本，不能包含 HTML、Markdown 链接或 URL";
        }
        int length = text.codePointCount(0, text.length());
        if (length > maxLength) {
            return label + "不能超过 " + maxLength + " 个字符（当前 " + length + " 个）";
        }
        return null;
    }

    /**
     * 判断文本是否包含 HTML、Markdown 链接或 URL
     *
     * @param text 文本
     * @return 含标记返回 true
     */
    public static boolean containsMarkup(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return HTML_TAG.matcher(text).find()
                || MARKDOWN_LINK.matcher(text).find()
                || URL_PATTERN.matcher(text).find();
    }
}
