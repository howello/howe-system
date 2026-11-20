package com.howe.meal.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.http.RemoteFileClient;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.AppReleaseChannel;
import com.howe.meal.domain.dto.AppPackageCheckRequest;
import com.howe.meal.domain.vo.AppPackageCheckVO;
import com.howe.meal.service.MealAppPackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * APP 资源包远程链接校验实现
 *
 * <p>「有效 wgt」的判定可复现，不写「基本结构」这种含糊标准：
 * ZIP 头、可打开、manifest.json 可解析且含 id 与 version、条目数与解压体积受限。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class MealAppPackageServiceImpl implements MealAppPackageService {

    /** 整体校验时间预算（毫秒），前台同步调用不能太久 */
    private static final long CHECK_DEADLINE_MS = 20000;

    /** 最多跟随的重定向次数 */
    private static final int MAX_REDIRECTS = 3;

    /** ZIP 条目数上限（wgt 实际约百级，防异常包） */
    private static final int MAX_ENTRIES = 10000;

    /** 解压后总大小上限（字节，防 zip bomb） */
    private static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;

    /** 单条目解压上限（字节） */
    private static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;

    /** manifest.json 的路径（wgt 根目录下） */
    private static final String MANIFEST_ENTRY = "manifest.json";

    @Override
    public AppPackageCheckVO check(AppPackageCheckRequest request) {
        return check(request, null, null, null);
    }

    /** 下载并校验远程资源包，可绑定到指定应用和版本。 */
    private AppPackageCheckVO check(AppPackageCheckRequest request, AppReleaseChannel channel,
                                    String versionName, Integer versionCode) {
        Path tempFile = null;
        try {
            long maxBytes = maxPackageBytes();
            tempFile = Files.createTempFile("wgt-check-", ".tmp");
            RemoteFileClient.ProbeResult result = RemoteFileClient.download(request.url(), maxBytes,
                    CHECK_DEADLINE_MS, MAX_REDIRECTS, tempFile);
            if (!result.reachable() || result.tempFile() == null) {
                return fail(result.reason());
            }
            String wgtError = verifyWgtStructure(result.tempFile(), channel, versionName, versionCode);
            if (wgtError != null) {
                return fail(wgtError);
            }
            AppPackageCheckVO vo = new AppPackageCheckVO();
            vo.setReachable(true);
            vo.setSize(result.contentLength());
            vo.setFileName(inferFileName(request.url()));
            vo.setCheckedAt(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, new java.util.Date()));
            return vo;
        } catch (ServiceException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.warn("远程资源包校验失败：{}", e.getMessage());
            return fail("校验失败：" + e.getMessage());
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile.toFile());
            }
        }
    }

    @Override
    public void assertVerified(MealAppVersion version, String oldStatus) {
        AppReleaseChannel channel = AppReleaseChannel.resolve(version.getAppKey());
        String url = version.getWgtUrl();
        if (StrUtil.isBlank(url) || !url.startsWith("https://")) {
            if (channel == AppReleaseChannel.PREGNANCY) {
                throw new ServiceException("宝宝资源包必须使用 HTTPS 地址");
            }
            return;
        }
        AppPackageCheckRequest request = new AppPackageCheckRequest(url);
        AppPackageCheckVO vo = check(request, channel,
                channel == AppReleaseChannel.PREGNANCY ? version.getVersionName() : null,
                channel == AppReleaseChannel.PREGNANCY ? version.getVersionCode() : null);
        if (!vo.isReachable()) {
            throw new ServiceException("远程资源包校验未通过：" + vo.getReason());
        }
    }

    @Override
    public void assertUpload(MultipartFile file, AppReleaseChannel channel, String versionName, Integer versionCode)
            throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的 wgt 文件");
        }
        if (file.getSize() > maxPackageBytes()) {
            throw new ServiceException("资源包超过大小上限");
        }
        Path temp = Files.createTempFile("wgt-upload-", ".zip");
        try {
            file.transferTo(temp);
            String error = verifyWgtStructure(temp, channel, versionName, versionCode);
            if (error != null) {
                throw new ServiceException(error);
            }
        } finally {
            FileUtil.del(temp.toFile());
        }
    }

    /**
     * 校验文件是合法的 wgt 结构
     *
     * @param file 已下载的临时文件
     * @return null 表示通过，否则返回具体失败原因
     */
    private String verifyWgtStructure(Path file, AppReleaseChannel channel, String versionName, Integer versionCode) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            // 1. ZIP 头：ZipFile 能打开就说明结构合法（打不开会在 open 时抛异常）
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            long totalUncompressed = 0;
            ZipEntry manifest = null;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > MAX_ENTRIES) {
                    return "资源包条目数超过 " + MAX_ENTRIES;
                }
                long size = entry.getSize();
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                        return "资源包解压体积超过上限";
                    }
                    if (size > MAX_ENTRY_BYTES) {
                        return "资源包含超过 " + MAX_ENTRY_BYTES + " 字节的单条目";
                    }
                }
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    manifest = entry;
                }
            }
            if (manifest == null) {
                return "资源包缺少 manifest.json";
            }
            // 2. manifest 必须能解析，且含 id 与 version
            try (InputStream in = new BufferedInputStream(zip.getInputStream(manifest))) {
                byte[] bytes = in.readNBytes(128 * 1024 + 1);
                if (bytes.length > 128 * 1024) {
                    return "manifest.json 超过大小上限";
                }
                JSONObject json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                if (json == null || StrUtil.isBlank(json.getString("id"))) {
                    return "manifest.json 缺少 id";
                }
                if (channel != null && !channel.getAppId().equals(json.getString("id"))) {
                    return "资源包应用标识与发布渠道不匹配";
                }
                JSONObject versionObj = json.getJSONObject("version");
                if (versionObj == null || StrUtil.isBlank(versionObj.getString("name"))) {
                    return "manifest.json 缺少 version 信息";
                }
                if (versionName != null && !versionName.equals(versionObj.getString("name"))
                        || versionCode != null && !versionCode.equals(versionObj.getInteger("code"))) {
                    return "资源包版本与发布参数不一致";
                }
            }
            return null;
        } catch (Exception e) {
            return "不是有效的 wgt/zip 文件：" + e.getMessage();
        }
    }

    /**
     * 从 URL 推断文件名
     *
     * @param url 地址
     * @return 文件名
     */
    private String inferFileName(String url) {
        String name = StrUtil.subAfter(url, '/', true);
        return StrUtil.isBlank(name) ? "app.wgt" : name;
    }

    /**
     * 构造失败结果
     *
     * @param reason 失败原因
     * @return 校验结果
     */
    private AppPackageCheckVO fail(String reason) {
        AppPackageCheckVO vo = new AppPackageCheckVO();
        vo.setReachable(false);
        vo.setReason(StrUtil.blankToDefault(reason, "远程资源包不可达"));
        return vo;
    }

    /**
     * 读取资源包大小上限配置
     *
     * @return 字节数
     */
    private long maxPackageBytes() {
        String value = ConfigUtils.getString(ConfigConstants.MEAL_APP_PACKAGE_MAX_SIZE);
        try {
            return StrUtil.isBlank(value) ? 50L * 1024 * 1024 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 50L * 1024 * 1024;
        }
    }
}
