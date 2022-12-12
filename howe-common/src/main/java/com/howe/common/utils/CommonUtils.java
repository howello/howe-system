package com.howe.common.utils;

import com.howe.common.constant.ListConstant;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/12 16:53 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public class CommonUtils {

    /**
     * 校验是否为基本类型
     *
     * @param type
     * @return
     */
    public static boolean isPrimitive(Class<?> type) {
        String typeName = type.getSimpleName();
        return ListConstant.PRIMITIVE_LIST.contains(typeName);
    }
}
