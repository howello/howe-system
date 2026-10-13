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
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class MealAppVersionServiceImpl implements IMealAppVersionService {

    /** wgt 资源包在对象存储上的业务目录 */
    private static final String WGT_BIZ_DIR = "meal/app";

    /** 允许上传的扩展名 */
    private static final String[] WGT_ALLOWED_EXTENSION = { "wgt" };

    private final MealAppVersionMapper mealAppVersionMapper;

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
            mealAppVersion.setStatus("0");
        }
        if (StringUtils.isEmpty(mealAppVersion.getForceUpdate())) {
            mealAppVersion.setForceUpdate("0");
        }
        mealAppVersion.setCreateBy(SecurityUtils.getUsername());
        return mealAppVersionMapper.insertMealAppVersion(mealAppVersion);
    }

    @Override
    public int updateMealAppVersion(MealAppVersion mealAppVersion) {
        MealAppVersion exist = mealAppVersionMapper.selectMealAppVersionById(mealAppVersion.getVersionId());
        if (exist == null) {
            throw new ServiceException("版本记录不存在");
        }
        mealAppVersion.setUpdateBy(SecurityUtils.getUsername());
        return mealAppVersionMapper.updateMealAppVersion(mealAppVersion);
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
}
