package com.howe.common.utils.mybatis;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/8 15:40 星期四
 * <p>@Version 1.0
 * <p>@Description
 */
public class MybatisUtils {
    /**
     * mybatis plus 传入对象生成queryWaper对象
     *
     * @param t
     * @param <T>
     * @return
     */
    @SneakyThrows
    public static <T> QueryWrapper<T> assembleQueryWrapper(T t) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        Field[] fields = ReflectUtil.getFields(t.getClass());
        for (Field field : fields) {
            ReflectUtil.setAccessible(field);
            Object o = field.get(t);
            TableId tableId = field.getAnnotation(TableId.class);
            TableField tableField = field.getAnnotation(TableField.class);
            String colName = tableId == null ? tableField == null ? "" : StringUtils.isBlank(tableField.value()) ? "" : tableField.value()
                    : StringUtils.isBlank(tableId.value()) ? "" : tableId.value();
            if (StringUtils.isBlank(colName)) {
                continue;
            }
            boolean condition;
            if (o instanceof String) {
                condition = StringUtils.isNotBlank((String) o);
            } else if (o instanceof Collection) {
                condition = CollectionUtils.isNotEmpty((Collection<?>) o);
            } else if (o instanceof Arrays) {
                condition = ArrayUtil.isNotEmpty(o);
            } else if (o instanceof Map) {
                condition = MapUtils.isNotEmpty((Map<?, ?>) o);
            } else {
                condition = o != null;
            }
            qw.eq(condition, colName, o);
        }
        return qw;
    }
}
