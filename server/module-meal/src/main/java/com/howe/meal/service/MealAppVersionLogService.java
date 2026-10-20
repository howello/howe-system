package com.howe.meal.service;

import com.howe.ai.api.AiClient;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * App 发版日志 AI 润色服务
 *
 * <p>把 CI 收集到的提交标题交给对话模型，提炼成家庭用户能看懂的中文更新说明。
 * 只做文本转换、不落库：润色结果由发版脚本随后作为 {@code updateLog} 交给发布接口，
 * 与 wgt 一起写进版本记录。任何失败都抛 {@link ServiceException}，由脚本回退原始提交摘要，
 * 不阻塞发版。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealAppVersionLogService {

    /** 发版日志润色场景（路由 key） */
    private static final String SCENE_VERSION_LOG = "meal.app.version.log";

    /** 输入摘要最大长度，避免把整段 git log 塞给模型 */
    private static final int SOURCE_MAX_LENGTH = 2000;

    /** 输出最多保留的行数 */
    private static final int OUTPUT_MAX_LINES = 3;

    /** 输出整段最大字符数（不含换行） */
    private static final int OUTPUT_MAX_LENGTH = 100;

    /**
     * 润色提示词缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_APP_VERSION_LOG}）。</p>
     */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是家庭点餐 App 的版本公告编辑。用户会把本次发版的代码提交标题交给你，"
            + "请把它们改写成家庭成员能看懂的更新说明。"
            + "要求：只使用给定提交里明确提到的内容，不编造、不夸大、不写技术术语（如 commit、分支、重构、依赖）；"
            + "每条一行，最多 3 行，整段不超过 100 个汉字；不使用序号、Markdown、emoji；"
            + "语气自然平实，例如「新增菜品编辑页，可以直接修改做法」「修复下架的菜仍显示在点餐区的问题」。";

    private final AiClient aiClient;

    /**
     * 润色发版日志
     *
     * @param source      原始提交摘要，每行一条
     * @param versionName 版本名称，可为空
     * @param versionCode 版本序号，可为空
     * @return 润色后的更新说明（纯文本，最多 3 行）
     */
    public String polish(String source, String versionName, Integer versionCode) {
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException("请提供需要润色的提交摘要");
        }
        if (trimmed.length() > SOURCE_MAX_LENGTH) {
            trimmed = trimmed.substring(0, SOURCE_MAX_LENGTH);
        }

        AiChatResult result;
        try {
            result = aiClient.chat(AiChatRequest.builder()
                    .scene(SCENE_VERSION_LOG)
                    .system(systemPrompt())
                    .prompt(buildPrompt(trimmed, versionName, versionCode))
                    .build());
        } catch (Exception e) {
            log.warn("App 发版日志 AI 润色调用失败，versionName={}", versionName, e);
            throw new ServiceException("日志润色失败：" + readableMessage(e));
        }

        String text = normalizeOutput(result == null ? null : result.getText());
        if (text.isEmpty()) {
            throw new ServiceException("日志润色未返回有效内容");
        }
        return text;
    }

    /**
     * 读取润色提示词（可在「系统管理 > 参数设置」修改，未配置时用缺省值）
     *
     * @return system 提示词
     */
    private String systemPrompt() {
        return ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_APP_VERSION_LOG, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 组装用户提示词
     *
     * @param source      提交摘要
     * @param versionName 版本名称
     * @param versionCode 版本序号
     * @return 提示词
     */
    private String buildPrompt(String source, String versionName, Integer versionCode) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotEmpty(versionName)) {
            builder.append("版本：v").append(versionName);
            if (versionCode != null) {
                builder.append("（").append(versionCode).append("）");
            }
            builder.append("\n");
        }
        builder.append("本次提交：\n").append(source)
                .append("\n请按系统提示词的要求输出更新说明。");
        return builder.toString();
    }

    /**
     * 规整模型输出：去掉 Markdown 标记与空行，最多保留 3 行、整段不超过 100 字
     *
     * @param raw 模型原始输出
     * @return 规整后的文本
     */
    private String normalizeOutput(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        int length = 0;
        for (String line : raw.split("\\r?\\n")) {
            String cleaned = stripMarkdown(line).trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            // 逐行控制总长度，超出时截断当前行后结束
            int remaining = OUTPUT_MAX_LENGTH - length;
            if (remaining <= 0) {
                break;
            }
            if (cleaned.length() > remaining) {
                cleaned = cleaned.substring(0, remaining);
            }
            lines.add(cleaned);
            length += cleaned.length();
            if (lines.size() >= OUTPUT_MAX_LINES) {
                break;
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 去掉行首的 Markdown 序号、项目符号、标题与强调符号
     *
     * @param line 单行文本
     * @return 清理后的文本
     */
    private String stripMarkdown(String line) {
        String cleaned = line == null ? "" : line;
        cleaned = cleaned.replaceAll("^\\s*(?:[-*•]+|\\d+[.、)])\\s*", "");
        cleaned = cleaned.replaceAll("^#+\\s*", "");
        cleaned = cleaned.replace("**", "").replace("__", "").replace("`", "");
        return cleaned.trim();
    }

    /**
     * 取异常的可读提示，避免把堆栈暴露给调用方
     *
     * @param e 异常
     * @return 可读提示
     */
    private String readableMessage(Exception e) {
        return e instanceof ServiceException && e.getMessage() != null ? e.getMessage() : "AI 暂时不可用";
    }
}
