package com.howe.meal.service;

import com.howe.common.exception.file.InvalidExtensionException;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.dto.AppUpdateCheckResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 点餐-App 版本 服务层
 *
 * @author howe
 */
public interface IMealAppVersionService {

    /**
     * 查询版本列表
     *
     * @param mealAppVersion 查询条件
     * @return 版本集合
     */
    List<MealAppVersion> selectMealAppVersionList(MealAppVersion mealAppVersion);

    /**
     * 按主键查询版本
     *
     * @param versionId 版本记录ID
     * @return 版本
     */
    MealAppVersion selectMealAppVersionById(Long versionId);

    /**
     * 新增版本
     *
     * @param mealAppVersion 版本
     * @return 结果
     */
    int insertMealAppVersion(MealAppVersion mealAppVersion);

    /**
     * 修改版本
     *
     * @param mealAppVersion 版本
     * @return 结果
     */
    int updateMealAppVersion(MealAppVersion mealAppVersion);

    /**
     * 批量删除版本（逻辑删除）
     *
     * @param versionIds 版本记录ID数组
     * @return 结果
     */
    int deleteMealAppVersionByIds(Long[] versionIds);

    /**
     * 检测是否有新版本
     *
     * <p>客户端同时上报「版本名称 + 版本号」：最新已发布版本号更大，或版本号相同但版本名称不同，
     * 都视为有新版本；最新版本号更小时不提示降级。</p>
     *
     * @param versionName 客户端当前版本名称
     * @param versionCode 客户端当前版本序号
     * @return 检测结果
     */
    AppUpdateCheckResult checkUpdate(String versionName, Integer versionCode);

    /**
     * 上传 wgt 资源包到对象存储（R2），返回可访问地址
     *
     * @param file wgt 文件
     * @return 可访问的资源包地址
     * @throws IOException 读取上传流出错
     * @throws InvalidExtensionException 扩展名不是 wgt
     */
    String uploadWgt(MultipartFile file) throws IOException, InvalidExtensionException;
}
