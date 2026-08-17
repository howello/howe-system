package com.howe.blog.waline;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * waline 评论 JSON 与文本解析器
 *
 * <p>解析 waline API 返回的 JSON，提取 time/orig/status 字段；
 * 解析 orig 纯文本（name/link/avatar/desc 四行）。</p>
 *
 * @author howe
 */
@Slf4j
public class WalineLinkParser {

    /**
     * waline 单条评论条目
     *
     * @param time   评论时间（毫秒时间戳）
     * @param orig   纯文本友链申请内容（name:\nlink:\navatar:\ndesc:）
     * @param status 状态（approved/waiting/spam）
     */
    public record WalineLinkItem(Long time, String orig, String status) {}

    /**
     * 解析 waline 响应体
     *
     * @param json 响应 JSON 字符串
     * @return 评论条目列表
     */
    public static List<WalineLinkItem> parseResponse(String json) {
        JSONObject root = JSONObject.parseObject(json);
        Integer errno = root.getInteger("errno");
        if (errno == null || errno != 0) {
            throw new ServiceException("waline 响应 errno=" + errno + "，errmsg=" + root.getString("errmsg"));
        }
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            log.warn("waline 响应缺少 data 字段");
            return new ArrayList<>();
        }
        JSONArray items = data.getJSONArray("data");
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<WalineLinkItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            Long time = item.getLong("time");
            String orig = item.getString("orig");
            String status = item.getString("status");
            if (time == null || StrUtil.isBlank(orig)) {
                log.warn("waline 条目缺少 time 或 orig，跳过：{}", item);
                continue;
            }
            result.add(new WalineLinkItem(time, orig, status));
        }
        return result;
    }

    /**
     * 解析 orig 纯文本四字段（name/link/avatar/desc）
     *
     * <p>格式示例：
     * <pre>
     * name: 王艳涛博客
     * link: https://www.wyantao.com/
     * avatar: https://q1.qlogo.cn/g?b=qq&nk=1669937522&s=640
     * desc: 保持努力，保持进步。
     * </pre>
     * </p>
     *
     * @param orig 纯文本
     * @return 键值对（键 name/link/avatar/desc，值去首尾空白）；缺失的键不在 map 里
     */
    public static Map<String, String> parseOrig(String orig) {
        Map<String, String> result = new HashMap<>();
        if (StrUtil.isBlank(orig)) {
            return result;
        }
        String[] lines = orig.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 中文输入法下用户常打出全角冒号，先归一为半角，否则整条申请会被解析成空 map 后静默丢弃
            line = line.replace('：', ':');
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("name".equals(key) || "link".equals(key) || "avatar".equals(key) || "desc".equals(key)) {
                result.put(key, value);
            }
        }
        return result;
    }
}
