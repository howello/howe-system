package com.howe.common.appversion.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.exception.file.InvalidExtensionException;
import com.howe.common.notify.wecom.AppReleaseWeComNotifier;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.file.FileUploadUtils;
import com.howe.common.appversion.domain.AppVersion;
import com.howe.common.appversion.domain.AppReleaseChannel;
import com.howe.common.appversion.domain.dto.AppUpdateCheckResult;
import com.howe.common.appversion.mapper.AppVersionMapper;
import com.howe.common.appversion.service.AppVersionService;
import com.howe.common.appversion.service.AppPackageService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 点餐-App 版本 服务层实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    /** 允许上传的扩展名 */
    private static final String[] WGT_ALLOWED_EXTENSION = { "wgt" };

    /** 脚本发布（发布令牌接口）落库时的创建者标识 */
    private static final String PUBLISH_CREATE_BY = "publish-token";

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "0";

    private final AppVersionMapper appVersionMapper;

    private final AppPackageService packageService;

    private final AppReleaseWeComNotifier appReleaseNotifier;


    @Override
    public List<AppVersion> selectAppVersionList(AppVersion appVersion) {
        if ("all".equals(appVersion.getAppKey())) {
            List<String> appKeys = Arrays.stream(AppReleaseChannel.values())
                    .filter(this::canManageChannel)
                    .map(AppReleaseChannel::getKey)
                    .toList();
            return appVersionMapper.selectAppVersionList(appVersion, appKeys);
        }
        AppReleaseChannel channel = AppReleaseChannel.resolve(appVersion.getAppKey());
        requireChannelManagement(channel);
        return appVersionMapper.selectAppVersionList(appVersion, List.of(channel.getKey()));
    }

    @Override
    public AppVersion selectAppVersionById(Long versionId) {
        AppVersion version = appVersionMapper.selectAppVersionById(versionId);
        if (version == null) {
            throw new ServiceException("版本记录不存在");
        }
        requireChannelManagement(AppReleaseChannel.resolve(version.getAppKey()));
        return version;
    }

    @Override
    public int insertAppVersion(AppVersion appVersion) {
        AppReleaseChannel channel = AppReleaseChannel.resolve(appVersion.getAppKey());
        requireChannelManagement(channel);
        appVersion.setAppKey(channel.getKey());
        if (StringUtils.isEmpty(appVersion.getStatus())) {
            appVersion.setStatus(STATUS_PUBLISHED);
        }
        if (StringUtils.isEmpty(appVersion.getForceUpdate())) {
            appVersion.setForceUpdate("0");
        }
        appVersion.setCreateBy(SecurityUtils.getUsername());
        int rows = appVersionMapper.insertAppVersion(appVersion);
        // 后台手工新增且直接发布时同样通知对应渠道
        if (rows > 0 && STATUS_PUBLISHED.equals(appVersion.getStatus())) {
            notifyPublished(appVersion);
        }
        return rows;
    }

    @Override
    public int updateAppVersion(AppVersion appVersion) {
        AppVersion exist = selectAppVersionById(appVersion.getVersionId());
        appVersion.setUpdateBy(SecurityUtils.getUsername());
        int rows = appVersionMapper.updateAppVersion(appVersion);
        // 只在「非发布 → 发布」的状态迁移时通知，避免编辑已发布版本时重复打扰
        String newStatus = StringUtils.isEmpty(appVersion.getStatus())
                ? exist.getStatus() : appVersion.getStatus();
        if (rows > 0 && STATUS_PUBLISHED.equals(newStatus) && !STATUS_PUBLISHED.equals(exist.getStatus())) {
            AppVersion published = appVersionMapper.selectAppVersionById(appVersion.getVersionId());
            if (published != null) {
                notifyPublished(published);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAppVersionByIds(Long[] versionIds) {
        if (versionIds == null || versionIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long versionId : versionIds) {
            selectAppVersionById(versionId);
        }
        return appVersionMapper.deleteAppVersionByIds(versionIds);
    }

    /**
     * 版本判定同时使用「版本名称 + 版本号」：
     * <ul>
     *   <li>最新版本号更大 → 有更新；</li>
     *   <li>版本号相同但版本名称不同 → 有更新（客户端未上报版本名称时只按版本号比较，避免误报）；</li>
     *   <li>最新版本号更小 → 无更新（不提示降级）。</li>
     * </ul>
     */
    @Override
    public AppUpdateCheckResult checkUpdate(AppReleaseChannel channel, String versionName, Integer versionCode) {
        AppVersion latest = appVersionMapper.selectLatestPublished(channel.getKey());
        if (latest == null || latest.getVersionCode() == null) {
            return AppUpdateCheckResult.none();
        }
        int clientCode = versionCode == null ? 0 : versionCode;
        int latestCode = latest.getVersionCode();
        String clientName = StringUtils.trimToEmpty(versionName);
        boolean hasUpdate = latestCode > clientCode
                || (latestCode == clientCode && StringUtils.isNotEmpty(clientName)
                        && !StringUtils.equals(latest.getVersionName(), clientName));
        if (!hasUpdate) {
            return AppUpdateCheckResult.none();
        }
        AppUpdateCheckResult result = new AppUpdateCheckResult();
        result.setUpdate(Boolean.TRUE);
        result.setVersionName(latest.getVersionName());
        result.setVersionCode(latest.getVersionCode());
        result.setWgtUrl(latest.getWgtUrl());
        result.setWgtSize(latest.getWgtSize());
        result.setForceUpdate("1".equals(latest.getForceUpdate()));
        result.setUpdateLog(latest.getUpdateLog());
        return result;
    }

    @Override
    public String uploadWgt(AppReleaseChannel channel, MultipartFile file) throws IOException, InvalidExtensionException {
        requireChannelManagement(channel);
        packageService.assertUpload(file, channel, null, null);
        return storeWgt(channel, file);
    }

    /** 用唯一对象键保存已通过校验的资源包。 */
    private String storeWgt(AppReleaseChannel channel, MultipartFile file) throws IOException, InvalidExtensionException {
        // 用 UUID 命名，保证每次上传的对象键唯一，避免 R2 / CDN 边缘缓存命中旧包
        return FileUploadUtils.uploadTo(channel.getDirectory(), file, WGT_ALLOWED_EXTENSION, true);
    }

    /** 原点餐授权随菜单移动，宝宝渠道的管理权限单独授予。 */
    private void requireChannelManagement(AppReleaseChannel channel) {
        if (!canManageChannel(channel)) {
            throw new AccessDeniedException("没有管理该 App 版本的权限");
        }
    }

    /** 判断当前用户是否有权管理应用渠道。 */
    private boolean canManageChannel(AppReleaseChannel channel) {
        return channel == AppReleaseChannel.MEAL || SecurityUtils.isAdmin()
                || SecurityUtils.hasPermi("tool:appVersion:" + channel.getKey());
    }

    /**
     * 脚本发布路径：不经过登录态，因此 createBy 记为固定标识，便于在列表里区分脚本发布与后台手工发布。
     * 参数校验放在这里，让发布接口的报错信息保持可读。
     */
    @Override
    public AppVersion publishWgt(AppReleaseChannel channel, MultipartFile file, String versionName, Integer versionCode,
            String updateLog, String forceUpdate) throws IOException, InvalidExtensionException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的 wgt 文件");
        }
        if (StringUtils.isEmpty(versionName)) {
            throw new ServiceException("版本名称不能为空");
        }
        if (versionCode == null || versionCode < 1) {
            throw new ServiceException("版本序号必须为正数");
        }
        if (channel != AppReleaseChannel.MEAL) {
            AppVersion latest = appVersionMapper.selectLatestPublished(channel.getKey());
            if (latest != null && latest.getVersionCode() >= versionCode) {
                throw new ServiceException("发版序号必须大于当前已发布版本");
            }
        }

        packageService.assertUpload(file, channel, versionName, versionCode);
        String url = storeWgt(channel, file);
        AppVersion version = new AppVersion();
        version.setAppKey(channel.getKey());
        version.setVersionName(versionName);
        version.setVersionCode(versionCode);
        version.setWgtUrl(url);
        version.setWgtSize(file.getSize());
        version.setFileName(file.getOriginalFilename());
        version.setUpdateLog(updateLog);
        version.setForceUpdate(StringUtils.isEmpty(forceUpdate) ? "0" : forceUpdate);
        version.setStatus(STATUS_PUBLISHED);
        version.setCreateBy(PUBLISH_CREATE_BY);
        appVersionMapper.insertAppVersion(version);
        // 脚本发布同样通知对应渠道；发送失败只记日志，不影响发布结果
        notifyPublished(version);
        return version;
    }

    /**
     * 版本发布后通知对应应用的企业微信群
     *
     * <p>只有成为「当前线上最新已发布版本」时才通知：补录旧版本或发布一个版本号更低的记录时，
     * 客户端检测不到更新，通知只会打扰家人。最新版本的判定放在事务提交后执行，
     * 保证读到的是本次发布后的数据；通知本身失败不影响发布结果。</p>
     *
     * @param version 已发布的版本记录
     */
    private void notifyPublished(AppVersion version) {
        if (version == null || version.getVersionId() == null) {
            return;
        }
        afterCommit(() -> {
            try {
                AppVersion latest = appVersionMapper.selectLatestPublished(version.getAppKey());
                if (latest == null || !version.getVersionId().equals(latest.getVersionId())) {
                    return;
                }
                AppReleaseChannel channel = AppReleaseChannel.resolve(latest.getAppKey());
                appReleaseNotifier.notifyPublished(channel.getKey(), channel.getDisplayName(), latest.getVersionName(),
                        latest.getVersionCode(), latest.getUpdateLog(), "1".equals(latest.getForceUpdate()));
            } catch (Exception e) {
                log.warn("发送 App 新版本企业微信通知失败，versionId={}", version.getVersionId(), e);
            }
        });
    }
    /** 已提交后再发送更新通知。 */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
