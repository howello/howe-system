package com.howe.share.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.common.exception.ServiceException;
import com.howe.share.config.ShareSecretCipher;
import com.howe.share.domain.ShareSource;
import com.howe.share.domain.vo.WebDavEntry;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.mapper.ShareSourceMapper;
import com.howe.share.service.IShareSourceService;
import com.howe.share.webdav.ShareWebDavClient;
import com.howe.share.webdav.WebDavAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WebDAV 源配置 Service 实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class ShareSourceServiceImpl implements IShareSourceService {

    private final ShareSourceMapper shareSourceMapper;

    private final ShareLinkMapper shareLinkMapper;

    private final ShareSecretCipher secretCipher;

    private final ShareWebDavClient webDavClient;

    @Override
    public List<ShareSource> selectShareSourceList(ShareSource shareSource) {
        List<ShareSource> list = shareSourceMapper.selectShareSourceList(shareSource);
        list.forEach(this::mask);
        return list;
    }

    @Override
    public ShareSource selectShareSourceById(Long sourceId) {
        return mask(shareSourceMapper.selectShareSourceById(sourceId));
    }

    @Override
    public List<ShareSource> selectEnabledSourceList() {
        List<ShareSource> list = shareSourceMapper.selectEnabledSourceList();
        list.forEach(this::mask);
        return list;
    }

    @Override
    public int insertShareSource(ShareSource shareSource) {
        if (StrUtil.isBlank(shareSource.getPassword())) {
            throw new ServiceException("应用密码不能为空");
        }
        shareSource.setPasswordCipher(secretCipher.encrypt(shareSource.getPassword()));
        shareSource.setPassword(null);
        if (StrUtil.isBlank(shareSource.getRootPath())) {
            shareSource.setRootPath("/");
        }
        if (StrUtil.isBlank(shareSource.getStatus())) {
            shareSource.setStatus("0");
        }
        return shareSourceMapper.insertShareSource(shareSource);
    }

    @Override
    public int updateShareSource(ShareSource shareSource) {
        ShareSource existing = shareSourceMapper.selectShareSourceById(shareSource.getSourceId());
        if (existing == null) {
            throw new ServiceException("源不存在");
        }
        // 密码非空才更新；为空表示保持原密码不变
        if (StrUtil.isNotBlank(shareSource.getPassword())) {
            shareSource.setPasswordCipher(secretCipher.encrypt(shareSource.getPassword()));
        }
        shareSource.setPassword(null);
        return shareSourceMapper.updateShareSource(shareSource);
    }

    @Override
    public int deleteShareSourceByIds(Long[] sourceIds) {
        if (sourceIds == null || sourceIds.length == 0) {
            return 0;
        }
        for (Long sourceId : sourceIds) {
            long count = shareLinkMapper.countBySourceId(sourceId);
            if (count > 0) {
                throw new ServiceException("该源下仍有 " + count + " 条分享记录，请先删除分享后再删除源");
            }
        }
        return shareSourceMapper.deleteShareSourceByIds(sourceIds);
    }

    @Override
    public List<WebDavEntry> browse(Long sourceId, String path) {
        ShareSource source = requireSource(sourceId);
        return webDavClient.list(authOf(source), path);
    }

    @Override
    public boolean test(Long sourceId) {
        ShareSource source = requireSource(sourceId);
        return webDavClient.test(authOf(source));
    }

    @Override
    public WebDavAuth authOf(Long sourceId) {
        return authOf(requireSource(sourceId));
    }

    /**
     * 构造访问凭据（解密密码）
     *
     * @param source 源
     * @return 访问凭据
     */
    private WebDavAuth authOf(ShareSource source) {
        return new WebDavAuth(source.getBaseUrl(), source.getUsername(),
                secretCipher.decrypt(source.getPasswordCipher()));
    }

    /**
     * 取源，不存在时抛业务异常
     */
    private ShareSource requireSource(Long sourceId) {
        ShareSource source = shareSourceMapper.selectShareSourceById(sourceId);
        if (source == null) {
            throw new ServiceException("源不存在");
        }
        return source;
    }

    /**
     * 脱敏：清掉密码字段，仅保留「是否已配置」
     */
    private ShareSource mask(ShareSource source) {
        if (source == null) {
            return null;
        }
        source.setPasswordSet(StrUtil.isNotBlank(source.getPasswordCipher()));
        source.setPasswordCipher(null);
        source.setPassword(null);
        return source;
    }
}
