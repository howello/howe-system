package com.howe.pregnancy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 加载随孕期模块发布的种子和只读资料
 *
 * <p>产检时间表与待产包清单来自审定素材，整理为随模块发布的只读 JSON；
 * 首次读取时幂等初始化，已存在稳定键的行跳过，不覆盖用户修改，也不恢复已软删的行。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class PregnancySeedProvider {

    /** 种子资源路径 */
    private static final String SEED_RESOURCE = "pregnancy/seed-content.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 已读取的资源，失败的读取允许重试。 */
    private final ConcurrentMap<String, JsonNode> resources = new ConcurrentHashMap<>();

    /**
     * 取种子根节点
     *
     * @return JSON 根节点，读取失败返回 null
     */
    public JsonNode root() {
        return resources.computeIfAbsent(SEED_RESOURCE, this::read);
    }

    /** 读取每日和每周变化素材。 */
    public JsonNode growth() {
        return resources.computeIfAbsent("pregnancy/growth-content.json", this::read);
    }

    /** 读取食物资料。 */
    public JsonNode foods() {
        return resources.computeIfAbsent("pregnancy/food-content.json", this::read);
    }

    /** 从模块 classpath 读取内置 JSON 文件。 */
    private JsonNode read(String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException e) {
            log.error("读取孕期资料失败：{}", resource, e);
            return null;
        }
    }

    /**
     * 取产检种子
     *
     * @return 产检节点，缺失返回空数组节点
     */
    public JsonNode checkups() {
        JsonNode root = root();
        return root == null ? objectMapper.createArrayNode() : root.path("checkups");
    }

    /** 按稳定键查找产检参考资料，用户创建的产检没有关联资料。 */
    public JsonNode checkup(String seedKey) {
        if (seedKey != null) {
            for (JsonNode checkup : checkups()) {
                if (seedKey.equals(checkup.path("seedKey").asText())) {
                    return checkup;
                }
            }
        }
        return MissingNode.getInstance();
    }

    /**
     * 取待产包种子
     *
     * @return 待产包节点，缺失返回空对象
     */
    public JsonNode bag() {
        JsonNode root = root();
        return root == null ? objectMapper.createObjectNode() : root.path("bag");
    }

    /**
     * 根据归属和物品名称获取清单提供的介绍。
     *
     * @param category 物品分类
     * @param name 物品名称
     * @return 介绍文字，未收录时为空
     */
    public String bagIntroduction(String category, String name) {
        return bag().path("introductions").path(category).path(name).asText(null);
    }

    /** 查找物品图，按当前分类和名称匹配，支持家庭自行添加同名物品。 */
    public String bagImage(String category, String name) {
        for (JsonNode item : bag().path("items")) {
            if (item.path("category").asText().equals(category) && item.path("name").asText().equals(name)) {
                return item.path("imageUrl").asText(null);
            }
        }
        return null;
    }
}
