package com.howe.common.appversion.service;

import com.howe.common.appversion.domain.dto.AppPackageCheckRequest;
import com.howe.common.appversion.domain.vo.AppPackageCheckVO;
import com.howe.common.appversion.domain.AppVersion;
import com.howe.common.appversion.domain.AppReleaseChannel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * APP 资源包远程链接校验服务
 *
 * <p>校验是质量约束而不是准入凭据：不签发可复用的凭据，
 * 保存接口在需要时（新增 / 换 URL / 停用后重新发布）自行重新校验。</p>
 *
 * @author howe
 */
public interface AppPackageService {

    /** 上传前检查 ZIP 结构、应用标识及可选版本信息。 */
    void assertUpload(MultipartFile file, AppReleaseChannel channel, String versionName, Integer versionCode)
            throws IOException;

    /**
     * 校验远程 wgt 链接
     *
     * <p>判定标准（全部满足才算有效）：地址安全（https / 公网）、有界下载成功、
     * ZIP 头正确、能打开且含可解析的 manifest.json（有 id 与 version）、
     * 条目数与解压体积在限制内。</p>
     *
     * @param request 校验请求
     * @return 校验结果
     */
    AppPackageCheckVO check(AppPackageCheckRequest request, AppReleaseChannel channel);

    /**
     * 保存前校验：按需重新探测远程地址
     *
     * <ul>
     *   <li>新增：远程地址必须通过完整校验；</li>
     *   <li>修改了 wgtUrl：新地址必须通过完整校验；</li>
     *   <li>URL 未变且状态未从停用转发布：跳过联网；</li>
     *   <li>从停用状态重新发布：即使 URL 未变也重新校验。</li>
     * </ul>
     *
     * @param version   待保存的版本对象
     * @param oldStatus 修改前的状态（新增时为 null）
     * @throws com.howe.common.exception.ServiceException 校验不通过时抛出
     */
    void assertVerified(AppVersion version, String oldStatus);
}
