package com.howe.system.service.impl;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysDept;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.StringUtils;
import com.howe.system.mapper.SysDeptMapper;
import com.howe.system.service.IFamilyCodeService;
import com.howe.system.service.IFamilyCodeService.FamilyCodeInfo;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 家庭邀请码 服务层实现
 *
 * <p>
 * 邀请码只存 Redis，不落库。存储结构：
 * </p>
 * <ul>
 *   <li>{@code family_code:{code}} → 部门ID，TTL 1 天，用于注册时反查家庭；</li>
 *   <li>{@code family_code:dept:{deptId}} → 当前码，TTL 同一天，用于「查看/重新生成」时
 *       定位旧码并删除。两把键互相指向，重新生成时按后者找到前者精确删除，
 *       不用 {@code keys} 扫描全库。</li>
 * </ul>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyCodeServiceImpl implements IFamilyCodeService
{
    /** 邀请码字符集：去掉 0/O、1/I/L 等易混淆字符，避免手抄出错 */
    private static final String CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** 家庭 → 当前码 的键前缀 */
    private static final String DEPT_CODE_PREFIX = CacheConstants.FAMILY_CODE_KEY + "dept:";

    private final RedisCache redisCache;

    private final SysDeptMapper deptMapper;

    /**
     * 为指定家庭生成（或重新生成）邀请码
     *
     * @param deptId 家庭部门ID
     * @return 新的邀请码
     */
    @Override
    public String generateCode(Long deptId)
    {
        SysDept family = deptId == null ? null : deptMapper.selectDeptById(deptId);
        assertFamilyDept(family);

        // 先让旧码失效：删掉「家庭 → 当前码」指向的那把键
        revokeCode(deptId);

        String code = newCode();
        redisCache.setCacheObject(CacheConstants.FAMILY_CODE_KEY + code, String.valueOf(deptId),
                CODE_TTL_DAYS, TimeUnit.DAYS);
        redisCache.setCacheObject(DEPT_CODE_PREFIX + deptId, code, CODE_TTL_DAYS, TimeUnit.DAYS);
        log.info("家庭 {} 生成新邀请码，有效期 {} 天", deptId, CODE_TTL_DAYS);
        return code;
    }

    /**
     * 校验邀请码并返回对应家庭
     *
     * @param code 邀请码
     * @return 家庭部门；无效时返回 null
     */
    @Override
    public SysDept resolveFamily(String code)
    {
        if (StringUtils.isEmpty(code))
        {
            return null;
        }
        // 统一大写并去空格：用户手输时常带空格、大小写混用
        String normalized = code.trim().toUpperCase();
        String deptIdText = redisCache.getCacheObject(CacheConstants.FAMILY_CODE_KEY + normalized);
        if (StringUtils.isEmpty(deptIdText))
        {
            return null;
        }
        Long deptId;
        try
        {
            deptId = Long.valueOf(deptIdText);
        }
        catch (NumberFormatException e)
        {
            log.warn("家庭邀请码 {} 对应的部门ID不合法：{}", normalized, deptIdText);
            return null;
        }
        SysDept family = deptMapper.selectDeptById(deptId);
        if (family == null
                || !"0".equals(family.getDelFlag())
                || !UserConstants.DEPT_NORMAL.equals(family.getStatus())
                || !UserConstants.DEPT_TYPE_FAMILY.equals(family.getDeptType()))
        {
            return null;
        }
        return family;
    }

    /**
     * 查询某家庭当前的邀请码
     *
     * @param deptId 家庭部门ID
     * @return 邀请码；没有或已过期返回 null
     */
    @Override
    public String selectCode(Long deptId)    {
        if (deptId == null)
        {
            return null;
        }
        String code = redisCache.getCacheObject(DEPT_CODE_PREFIX + deptId);
        if (StringUtils.isEmpty(code))
        {
            return null;
        }
        // 双向键的 TTL 一致，但以防万一还是确认正向键仍在，避免展示一个已失效的码
        return redisCache.hasKey(CacheConstants.FAMILY_CODE_KEY + code) ? code : null;
    }

    /**
     * 使某家庭当前的邀请码立即失效
     *
     * @param deptId 家庭部门ID
     */
    @Override
    public void revokeCode(Long deptId)
    {
        if (deptId == null)
        {
            return;
        }
        String key = DEPT_CODE_PREFIX + deptId;
        String oldCode = redisCache.getCacheObject(key);
        if (StringUtils.isNotEmpty(oldCode))
        {
            redisCache.deleteObject(CacheConstants.FAMILY_CODE_KEY + oldCode);
        }
        redisCache.deleteObject(key);
    }

    /**
     * 查询某家庭当前的邀请码及剩余有效期
     *
     * @param deptId 家庭部门ID
     * @return 邀请码与剩余秒数；没有有效码时 {@code code} 为空
     */
    @Override
    public FamilyCodeInfo describe(Long deptId)
    {
        String code = selectCode(deptId);
        if (StringUtils.isEmpty(code))
        {
            return new FamilyCodeInfo(null, 0L, CODE_TTL_DAYS);
        }
        long expire = redisCache.getExpire(CacheConstants.FAMILY_CODE_KEY + code);
        return new FamilyCodeInfo(code, Math.max(0L, expire), CODE_TTL_DAYS);
    }

    /**
     * 把刚生成的邀请码包装成展示对象
     *
     * @param deptId 家庭部门ID
     * @param code 邀请码
     * @return 邀请码与剩余秒数
     */
    @Override
    public FamilyCodeInfo describe(Long deptId, String code)
    {
        if (StringUtils.isEmpty(code))
        {
            return describe(deptId);
        }
        long expire = redisCache.getExpire(CacheConstants.FAMILY_CODE_KEY + code);
        return new FamilyCodeInfo(code, Math.max(0L, expire), CODE_TTL_DAYS);
    }

    /**
     * 清理所有已过期的反向键
     *
     * <p>
     * 正常不需要调用：Redis 会自动删掉过期键。留这个方法是给「家庭被删除/停用」
     * 的场景用，避免用户拿着还有效的码去注册。
     * </p>
     *
     * @param deptId 家庭部门ID
     */
    public void purgeDept(Long deptId)
    {
        revokeCode(deptId);
        Collection<String> keys = redisCache.keys(CacheConstants.FAMILY_CODE_KEY + "*");
        if (StringUtils.isEmpty(keys))
        {
            return;
        }
        String target = String.valueOf(deptId);
        for (String key : keys)
        {
            if (key.startsWith(DEPT_CODE_PREFIX))
            {
                continue;
            }
            if (target.equals(redisCache.getCacheObject(key)))
            {
                redisCache.deleteObject(key);
            }
        }
    }

    /**
     * 生成一个不与现有键冲突的随机码
     *
     * @return 16 位邀请码
     */
    private String newCode()
    {
        // 32^16 的空间，碰撞概率可忽略；仍做一次存在性检查，避免极端巧合覆盖别人的码
        for (int i = 0; i < 5; i++)
        {
            String code = RandomUtil.randomString(CODE_CHARS, CODE_LENGTH);
            if (!Boolean.TRUE.equals(redisCache.hasKey(CacheConstants.FAMILY_CODE_KEY + code)))
            {
                return code;
            }
        }
        throw new ServiceException("生成家庭邀请码失败，请重试");
    }

    /**
     * 校验部门确实是可以发邀请码的家庭
     *
     * @param family 部门
     */
    private void assertFamilyDept(SysDept family)
    {
        if (family == null || !"0".equals(family.getDelFlag()))
        {
            throw new ServiceException("家庭不存在");
        }
        if (!UserConstants.DEPT_TYPE_FAMILY.equals(family.getDeptType()))
        {
            throw new ServiceException("只有家庭类型可以生成邀请码");
        }
        if (!UserConstants.DEPT_NORMAL.equals(family.getStatus()))
        {
            throw new ServiceException("家庭已停用，无法生成邀请码");
        }
    }
}
