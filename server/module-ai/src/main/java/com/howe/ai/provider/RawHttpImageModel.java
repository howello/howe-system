package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * raw-http 协议的文生图模型
 *
 * <p>面向 Spring AI 未覆盖的异步文生图接口：先提交任务拿到 {@code task_id}，
 * 再按固定间隔轮询直到完成，最后把厂商临时地址包装成 {@link ImageResponse}。
 * 下载与转存不在这里做，由能力层统一处理。</p>
 *
 * @author howe
 */
@Slf4j
public class RawHttpImageModel implements ImageModel
{
    /** 提交任务的路径 */
    private static final String SUBMIT_PATH = "/api/v1/services/aigc/text2image/image-synthesis";

    /** 查询任务的路径前缀 */
    private static final String TASK_PATH_PREFIX = "/api/v1/tasks/";

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 2000L;

    /** 厂商返回的任务状态：成功 */
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** 厂商返回的任务状态：失败 */
    private static final String STATUS_FAILED = "FAILED";

    private final String baseUrl;

    private final String apiKey;

    private final String defaultModel;

    private final RawImageOptions defaultOptions;

    private final long timeoutMs;

    public RawHttpImageModel(String baseUrl, String apiKey, String defaultModel, RawImageOptions defaultOptions,
            long timeoutMs)
    {
        this.baseUrl = StrUtil.removeSuffix(baseUrl, "/");
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.defaultOptions = defaultOptions;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ImageResponse call(ImagePrompt prompt)
    {
        String text = prompt.getInstructions().isEmpty() ? "" : prompt.getInstructions().get(0).getText();
        if (StrUtil.isBlank(text))
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "文生图提示词不能为空");
        }
        RawImageOptions options = resolveOptions(prompt.getOptions());

        String taskId = submit(text, options);
        JSONObject output = poll(taskId);

        JSONArray results = output.getJSONArray("results");
        if (results == null || results.isEmpty())
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "文生图任务完成但没有返回图片地址");
        }
        List<ImageGeneration> generations = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++)
        {
            String url = results.getJSONObject(i).getString("url");
            if (StrUtil.isNotBlank(url))
            {
                generations.add(new ImageGeneration(new Image(url, null)));
            }
        }
        if (generations.isEmpty())
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "文生图任务完成但没有返回可用的图片地址");
        }
        return new ImageResponse(generations);
    }

    /**
     * 提交生成任务
     *
     * @param text    提示词
     * @param options 选项
     * @return 任务 ID
     */
    private String submit(String text, RawImageOptions options)
    {
        JSONObject input = new JSONObject();
        input.put("prompt", text);
        if (StrUtil.isNotBlank(options.negativePrompt()))
        {
            input.put("negative_prompt", options.negativePrompt());
        }

        JSONObject parameters = new JSONObject();
        if (StrUtil.isNotBlank(options.size()))
        {
            parameters.put("size", options.size());
        }
        if (options.getN() != null)
        {
            parameters.put("n", options.getN());
        }
        if (options.seed() != null)
        {
            parameters.put("seed", options.seed());
        }

        JSONObject body = new JSONObject();
        body.put("model", StrUtil.isBlank(options.getModel()) ? defaultModel : options.getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        JSONObject response = execute(HttpRequest.post(baseUrl + SUBMIT_PATH)
                .header("X-DashScope-Async", "enable")
                .body(body.toJSONString()), "提交文生图任务");

        JSONObject output = response.getJSONObject("output");
        String taskId = output == null ? null : output.getString("task_id");
        if (StrUtil.isBlank(taskId))
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "文生图任务提交成功但没有返回 task_id");
        }
        return taskId;
    }

    /**
     * 轮询任务直到完成
     *
     * @param taskId 任务 ID
     * @return 任务输出
     */
    private JSONObject poll(String taskId)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            JSONObject response = execute(HttpRequest.get(baseUrl + TASK_PATH_PREFIX + taskId), "查询文生图任务");
            JSONObject output = response.getJSONObject("output");
            String status = output == null ? null : output.getString("task_status");
            if (STATUS_SUCCEEDED.equalsIgnoreCase(status))
            {
                return output;
            }
            if (STATUS_FAILED.equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status))
            {
                String message = output.getString("message");
                throw new AiException(AiErrorCode.PROVIDER_ERROR,
                        "文生图任务失败：" + (StrUtil.isBlank(message) ? status : message));
            }
            try
            {
                Thread.sleep(POLL_INTERVAL_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new AiException(AiErrorCode.TIMEOUT, "文生图任务等待被中断", e);
            }
        }
        throw new AiException(AiErrorCode.TIMEOUT, "文生图任务轮询超时");
    }

    /**
     * 发送请求并解析响应
     *
     * @param request 请求
     * @param action  动作描述
     * @return 响应 JSON
     */
    private JSONObject execute(HttpRequest request, String action)
    {
        request.header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
        try (HttpResponse response = request.execute())
        {
            String body = response.body();
            if (!response.isOk())
            {
                throw new AiException(classifyStatus(response.getStatus()), action + "失败：" + brief(body));
            }
            if (StrUtil.isBlank(body))
            {
                throw new AiException(AiErrorCode.PROVIDER_ERROR, action + "返回了空响应");
            }
            return JSON.parseObject(body);
        }
    }

    /**
     * 按 HTTP 状态码判定错误码
     *
     * @param status 状态码
     * @return 错误码
     */
    private AiErrorCode classifyStatus(int status)
    {
        if (status == 401 || status == 403)
        {
            return AiErrorCode.AUTH_FAILED;
        }
        if (status == 429)
        {
            return AiErrorCode.RATE_LIMITED;
        }
        if (status >= 500)
        {
            return AiErrorCode.PROVIDER_ERROR;
        }
        return AiErrorCode.BAD_REQUEST;
    }

    /**
     * 截断响应体，避免把厂商原始响应整段抛出
     *
     * @param body 响应体
     * @return 截断后的文本
     */
    private String brief(String body)
    {
        if (StrUtil.isBlank(body))
        {
            return "无响应内容";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    /**
     * 取本次请求的选项
     *
     * @param options 提示词携带的选项
     * @return raw-http 选项
     */
    private RawImageOptions resolveOptions(ImageOptions options)
    {
        return options instanceof RawImageOptions raw ? raw : defaultOptions;
    }
}
