package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.exception.file.InvalidExtensionException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.file.FileUploadUtils;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.dto.AppUpdateCheckResult;
import com.howe.meal.mapper.MealAppVersionMapper;
import com.howe.meal.service.IMealAppVersionService;
import com.howe.meal.service.IMealWeComNotifyService;
import com.howe.meal.service.ITransactionCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 点餐-App 版本 服务层实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealAppVersionServiceImpl implements IMealAppVersionService {

    /** wgt 资源包在对象存储上的业务目录 */
    private static final String WGT_BIZ_DIR = "meal/app";

    /** 允许上传的扩展名 */
    private static final String[] WGT_ALLOWED_EXTENSION = { "wgt" };

    /** 脚本发布（发布令牌接口）落库时的创建者标识 */
    private static final String PUBLISH_CREATE_BY = "publish-token";

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "0";

    private final MealAppVersionMapper mealAppVersionMapper;

    private final IMealWeComNotifyService mealWeComNotifyService;

    private final ITransactionCommitExecutor transactionCommitExecutor;

    @Override
    public List<MealAppVersion> selectMealAppVersionList(MealAppVersion mealAppVersion) {
        return mealAppVersionMapper.selectMealAppVersionList(mealAppVersion);
    }

    @Override
    public MealAppVersion selectMealAppVersionById(Long versionId) {
        MealAppVersion version = mealAppVersionMapper.selectMealAppVersionById(versionId);
        if (version == null) {
            throw new ServiceException("版本记录不存在");
        }
        return version;
    }

    @Override
    public int insertMealAppVersion(MealAppVersion mealAppVersion) {
        if (StringUtils.isEmpty(mealAppVersion.getStatus())) {
            mealAppVersion.setStatus(STATUS_PUBLISHED);
        }
        if (StringUtils.isEmpty(mealAppVersion.getForceUpdate())) {
            mealAppVersion.setForceUpdate("0");
        }
        mealAppVersion.setCreateBy(SecurityUtils.getUsername());
        int rows = mealAppVersionMapper.insertMealAppVersion(mealAppVersion);
        // 后台手工新增且直接发布时同样通知家庭群
        if (rows > 0 && STATUS_PUBLISHED.equals(mealAppVersion.getStatus())) {
            notifyPublished(mealAppVersion);
        }
        return rows;
    }

    @Override
    public int updateMealAppVersion(MealAppVersion mealAppVersion) {
        MealAppVersion exist = mealAppVersionMapper.selectMealAppVersionById(mealAppVersion.getVersionId());
        if (exist == null) {
            throw new ServiceException("版本记录不存在");
        }
        mealAppVersion.setUpdateBy(SecurityUtils.getUsername());
        int rows = mealAppVersionMapper.updateMealAppVersion(mealAppVersion);
        // 只在「非发布 → 发布」的状态迁移时通知，避免编辑已发布版本时重复打扰
        String newStatus = StringUtils.isEmpty(mealAppVersion.getStatus())
                ? exist.getStatus() : mealAppVersion.getStatus();
        if (rows > 0 && STATUS_PUBLISHED.equals(newStatus) && !STATUS_PUBLISHED.equals(exist.getStatus())) {
            MealAppVersion published = mealAppVersionMapper.selectMealAppVersionById(mealAppVersion.getVersionId());
            if (published != null) {
                notifyPublished(published);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealAppVersionByIds(Long[] versionIds) {
        if (versionIds == null || versionIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long versionId : versionIds) {
            if (mealAppVersionMapper.selectMealAppVersionById(versionId) == null) {
                throw new ServiceException("版本记录不存在");
            }
        }
        return mealAppVersionMapper.deleteMealAppVersionByIds(versionIds);
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
    public AppUpdateCheckResult checkUpdate(String versionName, Integer versionCode) {
        MealAppVersion latest = mealAppVersionMapper.selectLatestPublished();
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
    public String uploadWgt(MultipartFile file) throws IOException, InvalidExtensionException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的 wgt 文件");
        }
        // 用 UUID 命名，保证每次上传的对象键唯一，避免 R2 / CDN 边缘缓存命中旧包
        return FileUploadUtils.uploadTo(WGT_BIZ_DIR, file, WGT_ALLOWED_EXTENSION, true);
    }

    /**
     * 脚本发布路径：不经过登录态，因此 createBy 记为固定标识，便于在列表里区分脚本发布与后台手工发布。
     * 参数校验放在这里，让发布接口的报错信息保持可读。
     */
    @Override
    public MealAppVersion publishWgt(MultipartFile file, String versionName, Integer versionCode,
            String updateLog, String forceUpdate) throws IOException, InvalidExtensionException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的 wgt 文件");
        }
        if (StringUtils.isEmpty(versionName)) {
            throw new ServiceException("版本名称不能为空");
        }
        if (versionCode == null) {
            throw new ServiceException("版本序号不能为空");
        }

        String url = uploadWgt(file);
        MealAppVersion version = new MealAppVersion();
        version.setVersionName(versionName);
        version.setVersionCode(versionCode);
        version.setWgtUrl(url);
        version.setWgtSize(file.getSize());
        version.setFileName(file.getOriginalFilename());
        version.setUpdateLog(updateLog);
        version.setForceUpdate(StringUtils.isEmpty(forceUpdate) ? "0" : forceUpdate);
        version.setStatus(STATUS_PUBLISHED);
        version.setCreateBy(PUBLISH_CREATE_BY);
        mealAppVersionMapper.insertMealAppVersion(version);
        // 脚本发布同样通知家庭群；发送失败只记日志，不影响发布结果
        notifyPublished(version);
        return version;
    }

    /**
     * 版本发布后通知家庭企业微信群
     *
     * <p>只有成为「当前线上最新已发布版本」时才通知：补录旧版本或发布一个版本号更低的记录时，
     * 客户端检测不到更新，通知只会打扰家人。最新版本的判定放在事务提交后执行，
     * 保证读到的是本次发布后的数据；通知本身失败不影响发布结果。</p>
     *
     * @param version 已发布的版本记录
     */
    private void notifyPublished(MealAppVersion version) {
        if (version == null || version.getVersionId() == null) {
            return;
        }
        transactionCommitExecutor.afterCommit(() -> {
            try {
                MealAppVersion latest = mealAppVersionMapper.selectLatestPublished();
                if (latest == null || !version.getVersionId().equals(latest.getVersionId())) {
                    return;
                }
                mealWeComNotifyService.notifyAppRelease(version);
            } catch (Exception e) {
                log.warn("发送 App 新版本企业微信通知失败，versionId={}", version.getVersionId(), e);
            }
        });
    }
}
