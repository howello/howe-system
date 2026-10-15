package com.howe.share.service;

import com.howe.share.domain.ShareSource;
import com.howe.share.domain.vo.WebDavEntry;
import com.howe.share.webdav.WebDavAuth;

import java.util.List;

/**
 * WebDAV 源配置 Service
 *
 * @author howe
 */
public interface IShareSourceService {

    /**
     * 查询源列表（密码脱敏）
     *
     * @param shareSource 查询条件
     * @return 源集合
     */
    List<ShareSource> selectShareSourceList(ShareSource shareSource);

    /**
     * 查询源详情（密码脱敏）
     *
     * @param sourceId 源ID
     * @return 源
     */
    ShareSource selectShareSourceById(Long sourceId);

    /**
     * 查询启用状态的源列表（密码脱敏）
     *
     * @return 源集合
     */
    List<ShareSource> selectEnabledSourceList();

    /**
     * 新增源
     *
     * @param shareSource 源（password 为明文）
     * @return 影响行数
     */
    int insertShareSource(ShareSource shareSource);

    /**
     * 修改源
     *
     * @param shareSource 源（password 非空时才更新密码）
     * @return 影响行数
     */
    int updateShareSource(ShareSource shareSource);

    /**
     * 批量删除源（存在分享记录时拒绝）
     *
     * @param sourceIds 源ID数组
     * @return 影响行数
     */
    int deleteShareSourceByIds(Long[] sourceIds);

    /**
     * 浏览源目录
     *
     * @param sourceId 源ID
     * @param path     相对源根的路径
     * @return 目录条目集合
     */
    List<WebDavEntry> browse(Long sourceId, String path);

    /**
     * 测试源连通性
     *
     * @param sourceId 源ID
     * @return 可达返回 true
     */
    boolean test(Long sourceId);

    /**
     * 取源的 WebDAV 访问凭据（内部使用，密码解密）
     *
     * @param sourceId 源ID
     * @return 访问凭据
     */
    WebDavAuth authOf(Long sourceId);
}
