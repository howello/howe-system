package com.howe.system.service;

import com.howe.common.core.domain.entity.SysDept;

/**
 * 家庭邀请码 服务层
 *
 * <p>
 * 邀请码是短期凭证，只存在 Redis（键 {@code family_code:{邀请码}}，值是部门ID），
 * 天然带 TTL，不落库。这样设计有三个好处：
 * </p>
 * <ul>
 *   <li>过期即物理消失，不需要定时任务清理，也不会在表里留下历史码；</li>
 *   <li>重新生成只需覆盖写入，旧码自动失效；</li>
 *   <li>数据库里不存在「邀请码」这一列，导库、备份、审计都不会带出可用凭据。</li>
 * </ul>
 *
 * @author howe
 */
public interface IFamilyCodeService
{
    /** 邀请码有效期（天） */
    int CODE_TTL_DAYS = 1;

    /** 邀请码长度 */
    int CODE_LENGTH = 16;

    /**
     * 为指定家庭生成（或重新生成）邀请码
     *
     * <p>
     * 重新生成会覆盖旧码：先删掉该家庭名下的全部旧码，再写入新码。
     * </p>
     *
     * @param deptId 家庭部门ID
     * @return 新的邀请码
     */
    String generateCode(Long deptId);

    /**
     * 校验邀请码并返回对应家庭
     *
     * <p>
     * 校验链路：Redis 里查得到 → 部门存在 → 未删除 → 启用中 → 部门类型是家庭。
     * 任何一步不通过都返回 null，调用方按「邀请码无效」处理，不区分具体原因，
     * 避免通过错误提示枚举出哪些码存在。
     * </p>
     *
     * @param code 邀请码
     * @return 家庭部门；无效时返回 null
     */
    SysDept resolveFamily(String code);

    /**
     * 查询某家庭当前的邀请码
     *
     * @param deptId 家庭部门ID
     * @return 邀请码；没有或已过期返回 null
     */
    String selectCode(Long deptId);

    /**
     * 使某家庭当前的邀请码立即失效
     *
     * @param deptId 家庭部门ID
     */
    void revokeCode(Long deptId);

    /**
     * 查询某家庭当前的邀请码及剩余有效期
     *
     * @param deptId 家庭部门ID
     * @return 邀请码与剩余秒数；没有有效码时 {@code code} 为空
     */
    FamilyCodeInfo describe(Long deptId);

    /**
     * 把刚生成的邀请码包装成展示对象
     *
     * @param deptId 家庭部门ID
     * @param code 邀请码
     * @return 邀请码与剩余秒数
     */
    FamilyCodeInfo describe(Long deptId, String code);

    /**
     * 家庭邀请码展示对象
     *
     * @param code 邀请码，没有时为 null
     * @param expireSeconds 剩余有效期（秒），没有时为 0
     * @param ttlDays 有效期（天），便于前端直接展示文案
     */
    record FamilyCodeInfo(String code, long expireSeconds, int ttlDays)
    {
    }
}
