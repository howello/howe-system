package com.howe.common.utils.sign;

/**
 * 定长比较工具
 *
 * <p>
 * 比较签名、令牌这类敏感字符串时不能用 {@code equals}：它一遇到不同字符就返回，
 * 耗时会随「前缀猜对了多少位」变化，理论上可被逐位试探出来。这里始终比完全长。
 * </p>
 *
 * @author howe
 */
public class ConstantTimeUtils
{
    /**
     * 定长比较两个字符串
     *
     * <p>
     * 长度不同直接判定不等——长度本身不是秘密，没必要为它做等时处理。
     * </p>
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param ignoreCase 是否忽略大小写（十六进制签名常见大小写不一致）
     * @return 是否相等
     */
    public static boolean equals(String expected, String actual, boolean ignoreCase)
    {
        if (expected == null || actual == null || expected.length() != actual.length())
        {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++)
        {
            char left = expected.charAt(i);
            char right = actual.charAt(i);
            if (ignoreCase)
            {
                left = Character.toLowerCase(left);
                right = Character.toLowerCase(right);
            }
            diff |= left ^ right;
        }
        return diff == 0;
    }

    /**
     * 定长比较两个字符串，区分大小写
     *
     * @param expected 期望值
     * @param actual 实际值
     * @return 是否相等
     */
    public static boolean equals(String expected, String actual)
    {
        return equals(expected, actual, false);
    }
}
