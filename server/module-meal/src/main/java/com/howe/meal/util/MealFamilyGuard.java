package com.howe.meal.util;

import com.howe.common.core.domain.entity.SysDept;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.system.mapper.SysDeptMapper;
import java.util.Objects;

/**
 * 点餐模块的有效家庭前置校验。
 *
 * <p>菜品与分类的可见范围是「公共（{@code dept_id = 0}）+ 当前家庭」。
 * 普通账号没有有效家庭时，{@code deptId} 为 {@code null}，如果把 {@code null} 直接传进 SQL，
 * {@code dept_id} 条件会被整段跳过，等于把全站数据当公共数据返回。
 * 因此工具只读路径必须在分页与主查询之前显式确认家庭有效并失败关闭。</p>
 *
 * <p>平台内置账号沿用既有全量范围，不要求家庭归属。</p>
 */
public final class MealFamilyGuard {

    /** 家庭部门类型。 */
    public static final String FAMILY_DEPT_TYPE = "F";

    /** 工具校验不允许实例化。 */
    private MealFamilyGuard() {
    }

    /**
     * 判定当前账号是否为内置平台账号。
     *
     * @return 平台账号返回 true
     */
    public static boolean isPlatformAccount() {
        return SecurityUtils.isAdmin();
    }

    /**
     * 校验当前账号属于有效家庭并返回家庭 ID。
     *
     * <p>有效性条件：部门存在、未删除、启用、类型为 {@code F}。平台内置账号不经过本方法，
     * 由调用方按既有全量范围处理。</p>
     *
     * @param depts 部门服务
     * @return 有效家庭 ID
     * @throws ServiceException 当前账号没有有效家庭
     */
    public static Long requireFamilyDeptId(SysDeptMapper depts) {
        Objects.requireNonNull(depts);
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null || deptId <= 0) {
            throw new ServiceException("当前账号没有可用的家庭归属");
        }
        SysDept dept = depts.selectDeptById(deptId);
        if (dept == null || !"0".equals(dept.getDelFlag()) || !"0".equals(dept.getStatus())
                || !FAMILY_DEPT_TYPE.equals(dept.getDeptType())) {
            throw new ServiceException("当前账号没有可用的家庭归属");
        }
        return deptId;
    }
}
