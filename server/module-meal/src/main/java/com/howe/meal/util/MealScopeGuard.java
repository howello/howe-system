package com.howe.meal.util;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;

/**
 * 点餐模块的归属校验
 *
 * <p>菜品与分类承载「公共数据（dept_id = 0）+ 家庭私有数据」，两者都不走
 * {@code @DataScope}，所以归属校验必须在 Service 层显式做。订单 / 评价 / 提案
 * 虽然 list 走了数据权限，但「按 id 取详情」不受数据权限保护，同样要用这里的方法兜底。</p>
 *
 * @author howe
 */
public final class MealScopeGuard {

    private MealScopeGuard() {
    }

    /**
     * 校验当前登录用户是否有权改动这条数据
     *
     * <p>公共数据（{@code ownerDeptId = 0}）只有平台管理员能改；
     * 其余数据必须属于当前用户所在的家庭。</p>
     *
     * @param ownerDeptId 数据所属家庭ID
     * @param label       数据名称，用于拼错误提示
     */
    public static void assertWritable(Long ownerDeptId, String label) {
        if (ownerDeptId == null || ownerDeptId.longValue() == 0L) {
            if (!SecurityUtils.isAdmin()) {
                throw new ServiceException("公共" + label + "只能由平台管理员维护");
            }
            return;
        }
        assertSameDept(ownerDeptId, label);
    }

    /**
     * 校验数据属于当前登录用户的家庭
     *
     * @param ownerDeptId 数据所属家庭ID
     * @param label       数据名称，用于拼错误提示
     */
    public static void assertSameDept(Long ownerDeptId, String label) {
        Long deptId = SecurityUtils.getDeptId();
        if (ownerDeptId == null || deptId == null || !ownerDeptId.equals(deptId)) {
            throw new ServiceException("无权访问其他家庭的" + label);
        }
    }

    /**
     * 校验数据属于当前登录用户本人
     *
     * @param ownerUserId 数据所属用户ID
     * @param label       数据名称，用于拼错误提示
     */
    public static void assertSameUser(Long ownerUserId, String label) {
        Long userId = SecurityUtils.getUserId();
        if (ownerUserId == null || userId == null || !ownerUserId.equals(userId)) {
            throw new ServiceException("无权操作他人的" + label);
        }
    }
}
