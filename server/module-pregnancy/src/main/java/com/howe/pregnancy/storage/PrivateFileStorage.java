package com.howe.pregnancy.storage;

import com.howe.common.config.YmlConfig;
import com.howe.common.utils.StringUtils;
import com.howe.pregnancy.util.PregnancyException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 孕期私有原图存储
 *
 * <p>现有 {@code howe.profile} 由 {@code /profile/**} 匿名映射为公开静态资源，
 * 不能存放孕期原图。这里使用独立的私有根目录，只通过鉴权接口读回字节。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>根目录与公开目录的真实路径互不包含，启动时校验；</li>
 *   <li>存储键由服务端生成且不可变，形如 {@code 2026/11/&lt;uuid&gt;.jpg}；</li>
 *   <li>所有路径都经规范化，拒绝 {@code ..} 与符号链接逃逸；</li>
 *   <li>对外只返回文件 ID，错误信息不包含物理路径。</li>
 * </ul>
 *
 * @author howe
 */
@Slf4j
@Component
public class PrivateFileStorage {

    /** 私有根目录配置键 */
    private static final String CONFIG_KEY = "howe.pregnancy.privateDir";

    /** 私有根目录 */
    private final Path root;

    /**
     * 构造存储
     *
     * @param privateDir 私有根目录，由部署配置提供
     */
    public PrivateFileStorage(@Value("${howe.pregnancy.privateDir:}") String privateDir) {
        String value = privateDir;
        if (StringUtils.isEmpty(value)) {
            value = Paths.get(System.getProperty("user.home"), ".howe", "pregnancy-private").toString();
        }
        this.root = Paths.get(value).toAbsolutePath().normalize();
    }

    /**
     * 启动校验：创建目录并确认与公开目录不重叠
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建孕期私有目录：" + CONFIG_KEY, e);
        }
        String publicProfile = YmlConfig.getProfile();
        if (StringUtils.isNotEmpty(publicProfile)) {
            Path publicPath = Paths.get(publicProfile).toAbsolutePath().normalize();
            if (root.startsWith(publicPath) || publicPath.startsWith(root)) {
                throw new IllegalStateException(
                        "孕期私有目录不能与公开上传目录重叠：" + CONFIG_KEY + " 与 howe.profile");
            }
        }
        log.info("孕期私有原图目录已就绪：{}", root);
    }

    /**
     * 解析存储键对应的文件路径
     *
     * @param storageKey 存储键
     * @return 文件路径
     */
    public Path resolve(String storageKey) {
        if (StringUtils.isEmpty(storageKey) || storageKey.contains("..")) {
            throw PregnancyException.notFound();
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw PregnancyException.notFound();
        }
        return target;
    }

    /**
     * 写入字节
     *
     * @param storageKey 存储键
     * @param input      输入流
     * @return 实际写入字节数
     * @throws IOException 写入失败
     */
    public long write(String storageKey, InputStream input) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        return Files.copy(input, target);
    }

    /**
     * 判断文件是否存在
     *
     * @param storageKey 存储键
     * @return 存在返回 true
     */
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    /**
     * 读取文件字节
     *
     * @param storageKey 存储键
     * @return 输入流，调用方负责关闭
     * @throws IOException 读取失败
     */
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    /**
     * 取文件大小
     *
     * @param storageKey 存储键
     * @return 字节数，不存在返回 0
     */
    public long size(String storageKey) {
        try {
            return Files.size(resolve(storageKey));
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 删除文件
     *
     * @param storageKey 存储键
     * @return 删除成功或文件本不存在返回 true
     */
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("删除孕期私有文件失败：key={}", storageKey, e);
            return false;
        }
    }

    /**
     * 取私有根目录（仅用于同模块的清理任务）
     *
     * @return 根目录
     */
    public Path root() {
        return root;
    }
}
