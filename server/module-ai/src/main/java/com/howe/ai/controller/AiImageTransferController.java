package com.howe.ai.controller;

import com.howe.ai.storage.AiImageCompensationStore;
import com.howe.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录用户查询自己生成图片的补偿转存结果。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/image-transfer")
@Tag(name = "AI 图片转存")
public class AiImageTransferController {
    private final AiImageCompensationStore store;

    /** 查询转存结果，任务归属和补偿期限由存储服务校验。 */
    @Operation(summary = "查询图片转存结果")
    @GetMapping("/{taskId}")
    public AjaxResult status(@PathVariable String taskId) {
        return AjaxResult.success(store.status(taskId));
    }
}
