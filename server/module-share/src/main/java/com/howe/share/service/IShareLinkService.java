package com.howe.share.service;

import com.howe.share.domain.ShareAccessLog;
import com.howe.share.domain.ShareLink;
import com.howe.share.domain.dto.ShareLinkCreateRequest;
import com.howe.share.domain.dto.ShareLinkUpdateRequest;

import java.util.List;

/**
 * 分享记录 Service
 *
 * @author howe
 */
public interface IShareLinkService {

    /**
     * 查询分享列表
     *
     * @param shareLink 查询条件
     * @return 分享集合（含短链地址与源名称）
     */
    List<ShareLink> selectShareLinkList(ShareLink shareLink);

    /**
     * 查询分享详情
     *
     * @param linkId 分享ID
     * @return 分享
     */
    ShareLink selectShareLinkById(Long linkId);

    /**
     * 按短码查询分享
     *
     * @param code 短码
     * @return 分享
     */
    ShareLink selectShareLinkByCode(String code);

    /**
     * 创建分享：校验文件、生成短码、落库并提交异步缓存
     *
     * @param request  创建请求
     * @param createBy 创建者
     * @return 新建的分享记录（含短链地址）
     */
    ShareLink create(ShareLinkCreateRequest request, String createBy);

    /**
     * 修改分享约束（密码 / 过期 / 次数 / 焚毁 / 备注 / 状态）
     *
     * @param request  修改请求
     * @param updateBy 修改者
     * @return 影响行数
     */
    int update(ShareLinkUpdateRequest request, String updateBy);

    /**
     * 手动失效 / 恢复
     *
     * @param linkId 分享ID
     * @param status 目标状态（0正常 1已失效）
     * @return 影响行数
     */
    int changeStatus(Long linkId, String status);

    /**
     * 批量删除分享（连带删路由缓存、存储对象与访问日志）
     *
     * @param linkIds 分享ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] linkIds);

    /**
     * 手动重新缓存
     *
     * @param linkId 分享ID
     * @return 影响行数
     */
    int recache(Long linkId);

    /**
     * 查询分享的访问记录
     *
     * @param linkId 分享ID
     * @return 日志集合
     */
    List<ShareAccessLog> selectLogs(Long linkId);
}
