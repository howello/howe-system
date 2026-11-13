package com.howe.meal.util;

import com.howe.common.exception.ServiceException;

import java.util.List;
import java.util.Set;

/**
 * 分类图标校验器
 *
 * <p>分类图标只允许使用图标选择器内置的 emoji，不接受外链图片、{@code data:} URL
 * 或任意文本。采用固定白名单而不是 Unicode 区间判断：emoji 可能带变体选择符、
 * 肤色修饰符或零宽连接符，区间正则容易误收或误拒。</p>
 *
 * <p>白名单必须与前端 {@code MealCategoryIconPicker.vue} 的默认图标集保持一致。</p>
 *
 * @author howe
 */
public final class MealCategoryIconGuard {

    /** 图标最大长度（Java UTF-16 code unit） */
    private static final int MAX_LENGTH = 16;

    /**
     * 允许的 emoji 图标（去重后的固定集合）
     *
     * <p>按用途分组：荤菜、素菜、水产、蛋豆、凉卤、汤羹、面点、地方特色、其他。</p>
     */
    private static final Set<String> ALLOWED_ICONS = Set.of(
            // 荤菜
            "🍖", "🍗", "🥩", "🥓", "🍔", "🌭",
            // 半荤（公共分类「半荤」的默认图标）
            "🥘",
            // 素菜
            "🥬", "🥦", "🥒", "🥕", "🌽", "🍆", "🍄",
            // 水产
            "🐟", "🐠", "🦐", "🦀", "🦑", "🦪",
            // 蛋豆
            "🥚", "🍳", "🫘", "🧈",
            // 凉卤
            "🥗", "🫒", "🧊", "🧄", "🌶️",
            // 汤羹
            "🥣", "🍲", "🫕", "🍵", "🥄",
            // 面点
            "🍜", "🍝", "🥟", "🥐", "🍞", "🥖", "🍚", "🍙",
            // 地方特色
            "🏔️", "🫑", "🌿", "🥢", "🍯",
            // 其他
            "🍽️", "✨", "⭐", "🔥", "❤️");

    private MealCategoryIconGuard() {
    }

    /**
     * 校验并规范化分类图标
     *
     * @param icon 前端提交的图标
     * @return 规范化后的图标；空白值归一为空字符串
     * @throws ServiceException 图标不是白名单内的 emoji
     */
    public static String validateIcon(String icon) {
        if (icon == null) {
            return "";
        }
        String value = icon.strip();
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() > MAX_LENGTH || !ALLOWED_ICONS.contains(value)) {
            throw new ServiceException("分类图标只能使用图标选择器中的 emoji，最多 16 个字符");
        }
        return value;
    }

    /**
     * 判断整批分类中是否存在需要迁移的非 emoji 图标
     *
     * @param icons 待检查的图标值
     * @return 第一个不合法的图标；全部合法返回 null
     */
    public static String findInvalidIcon(List<String> icons) {
        if (icons == null || icons.isEmpty()) {
            return null;
        }
        for (String icon : icons) {
            if (icon == null || icon.isBlank()) {
                continue;
            }
            String value = icon.strip();
            if (value.length() > MAX_LENGTH || !ALLOWED_ICONS.contains(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取默认图标（图标为空或非法时使用）
     *
     * @return 默认 emoji
     */
    public static String defaultIcon() {
        return "🍽️";
    }
}
