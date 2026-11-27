package com.howe.common.weread.service.impl;

import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.exception.ServiceException;
import com.howe.common.weread.service.WeReadService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 使用固定微信读书网关地址，统一处理鉴权、超时及响应校验。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeReadServiceImpl implements WeReadService {
    private static final String GATEWAY = "https://i.weread.qq.com/api/agent/gateway";
    private static final String SKILL_VERSION = "1.0.6";
    private static final int PAGE_SIZE = 20;
    private final ObjectMapper json;

    /** 获取电子书检索结果。 */
    @Override
    public JsonNode search(String apiKey, String keyword, String context) {
        return request(apiKey, "/store/search", Map.of("keyword", keyword, "context", context, "scope", 10));
    }

    /** 获取单本书籍详情。 */
    @Override
    public JsonNode bookInfo(String apiKey, String bookId) {
        return request(apiKey, "/book/info", Map.of("bookId", bookId));
    }

    /** 获取书架。 */
    @Override
    public JsonNode shelf(String apiKey) {
        return request(apiKey, "/shelf/sync", Map.of());
    }

    /** 获取一页笔记本概览。 */
    @Override
    public JsonNode notebooks(String apiKey, Long lastSort) {
        return request(apiKey, "/user/notebooks", lastSort == null ? Map.of("count", PAGE_SIZE)
            : Map.of("count", PAGE_SIZE, "lastSort", lastSort));
    }

    /** 获取划线列表。 */
    @Override
    public JsonNode highlights(String apiKey, String bookId) {
        return request(apiKey, "/book/bookmarklist", Map.of("bookId", bookId));
    }

    /** 获取一页个人想法和点评。 */
    @Override
    public JsonNode reviews(String apiKey, String bookId, long syncKey) {
        return request(apiKey, "/review/list/mine", Map.of("bookid", bookId, "synckey", syncKey, "count", PAGE_SIZE));
    }

    /** 发送平铺的 JSON 参数，错误消息中只保留可安全展示的状态。 */
    private JsonNode request(String apiKey, String apiName, Map<String, ?> parameters) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException("请在参数设置中配置微信读书 API Key");
        }
        var body = json.createObjectNode().put("api_name", apiName).put("skill_version", SKILL_VERSION);
        parameters.forEach((key, value) -> body.set(key, json.valueToTree(value)));
        try (var response = HttpRequest.post(GATEWAY).bearerAuth(apiKey)
            .body(body.toString(), "application/json").timeout(8000).setFollowRedirects(false).execute()) {
            if (response.getStatus() == 401 || response.getStatus() == 403) {
                throw new ServiceException("微信读书授权失败，请检查 API Key");
            }
            if (!response.isOk()) {
                log.warn("微信读书请求失败，接口={}，HTTP 状态={}", apiName, response.getStatus());
                throw new ServiceException("微信读书请求失败（HTTP " + response.getStatus() + "）");
            }
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isObject()) {
                throw new ServiceException("微信读书返回格式无效");
            }
            JsonNode error = result.path("errcode");
            if (!error.isMissingNode() && error.asInt(-1) != 0) {
                throw new ServiceException("微信读书请求失败（错误码 " + error.asInt(-1) + "）");
            }
            return result;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("微信读书网关调用异常，接口={}，异常类型={}", apiName, exception.getClass().getSimpleName());
            throw new ServiceException("微信读书暂不可用，请稍后重试");
        }
    }
}
