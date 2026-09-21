package com.howe.ai.capability;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiImage;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.config.AiConfigService;
import com.howe.ai.config.AiProperties;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiImageAsset;
import com.howe.ai.provider.ImageSpec;
import com.howe.ai.route.AiRoutePlan;
import com.howe.ai.route.AiRouteResolver;
import com.howe.ai.storage.AiImageTransfer;
import com.howe.ai.usage.AiRequestDigest;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文生图能力
 *
 * <p>内部完成「提交 → 轮询 → 下载 → 转存」，业务模块拿到的永远是对象存储上的永久地址。
 * 转存失败时抛 {@code TRANSFER_FAILED} 且不返回厂商临时地址。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCapability
{
    /** 默认尺寸 */
    private static final String DEFAULT_SIZE = "1024*1024";

    /** 默认生成张数 */
    private static final int DEFAULT_COUNT = 1;

    private final AiRouteResolver aiRouteResolver;

    private final AiConfigCache aiConfigCache;

    private final AiConfigService aiConfigService;

    private final AiExecutionTemplate aiExecutionTemplate;

    private final AiImageTransfer aiImageTransfer;

    /**
     * 文生图
     *
     * @param request 文生图请求
     * @return 文生图结果
     */
    public AiImageResult image(AiImageRequest request)
    {
        if (StrUtil.isBlank(request.getPrompt()))
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "文生图提示词不能为空");
        }
        AiRoutePlan plan = aiRouteResolver.resolve(request.getScene(), AiCapability.IMAGE,
                request.getChannel(), request.getModel());
        AiProperties properties = aiConfigCache.getProperties();
        int count = resolveCount(request, plan, properties);
        String size = resolveSize(request, plan);
        String fullPrompt = buildPrompt(request, plan);

        ImageSpec spec = new ImageSpec(size, count, request.getNegativePrompt(), request.getSeed());
        AiCallContext context = new AiCallContext(IdUtil.fastSimpleUUID(), request.getScene(),
                AiCapability.IMAGE,
                AiRequestDigest.of(request.getScene(), AiCapability.IMAGE.name(), request.getChannel(),
                        request.getModel(), fullPrompt, null, request.getMetadata()),
                currentOperator());

        AiInvocation<List<AiImage>> invocation = aiExecutionTemplate.execute(plan, context, target ->
        {
            ImageModel imageModel = aiConfigCache.imageModel(target.channel(), target.model());
            ImagePrompt prompt = new ImagePrompt(fullPrompt,
                    aiConfigCache.imageOptions(target.channel(), target.model(), spec));
            ImageResponse response = imageModel.call(prompt);
            return transferAll(response);
        }, (callLog, images) ->
        {
            callLog.setImageCount(images.size());
            List<AiImageAsset> assets = new ArrayList<>(images.size());
            for (AiImage image : images)
            {
                AiImageAsset asset = new AiImageAsset();
                asset.setObjectKey(image.getKey());
                asset.setUrl(image.getUrl());
                asset.setWidth(image.getWidth());
                asset.setHeight(image.getHeight());
                assets.add(asset);
            }
            return assets;
        });

        return AiImageResult.builder()
                .images(invocation.value())
                .prompt(fullPrompt)
                .provider(invocation.target().channel().getProviderCode())
                .model(invocation.target().model().getModelName())
                .elapsedMs(invocation.elapsedMs())
                .callLogId(invocation.callLogId())
                .build();
    }

    /**
     * 把厂商返回的每张图转存为永久地址
     *
     * @param response 厂商响应
     * @return 生成图列表
     */
    private List<AiImage> transferAll(ImageResponse response)
    {
        List<ImageGeneration> generations = response.getResults();
        if (generations == null || generations.isEmpty())
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "模型没有返回任何图片");
        }
        List<AiImage> images = new ArrayList<>(generations.size());
        for (ImageGeneration generation : generations)
        {
            Image image = generation.getOutput();
            if (image == null)
            {
                continue;
            }
            images.add(aiImageTransfer.transfer(image.getUrl(), image.getB64Json(), null));
        }
        if (images.isEmpty())
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "模型返回的图片内容为空");
        }
        return images;
    }

    /**
     * 组装完整提示词：业务提示词 + 风格后缀模板
     *
     * @param request 请求
     * @param plan    路由计划
     * @return 完整提示词
     */
    private String buildPrompt(AiImageRequest request, AiRoutePlan plan)
    {
        String styleKey = StrUtil.isNotBlank(request.getStyle())
                ? request.getStyle().trim() : plan.params().getString("styleSuffixKey");
        String suffix = aiConfigService.styleSuffix(styleKey);
        if (StrUtil.isBlank(suffix))
        {
            return request.getPrompt().trim();
        }
        return request.getPrompt().trim() + "，" + suffix;
    }

    /**
     * 解析生成张数并校验上限
     *
     * @param request    请求
     * @param plan       路由计划
     * @param properties 参数快照
     * @return 生成张数
     */
    private int resolveCount(AiImageRequest request, AiRoutePlan plan, AiProperties properties)
    {
        Integer count = request.getCount();
        if (count == null && plan.params().containsKey("count"))
        {
            count = plan.params().getInteger("count");
        }
        int resolved = count == null ? DEFAULT_COUNT : count;
        if (resolved <= 0)
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "生成张数必须大于 0");
        }
        int max = properties.getImageMaxCount();
        if (resolved > max)
        {
            throw new AiException(AiErrorCode.BAD_REQUEST,
                    "单次最多生成 " + max + " 张，当前请求 " + resolved + " 张");
        }
        return resolved;
    }

    /**
     * 解析尺寸：请求显式 &gt; 场景 params &gt; 默认值
     *
     * @param request 请求
     * @param plan    路由计划
     * @return 尺寸
     */
    private String resolveSize(AiImageRequest request, AiRoutePlan plan)
    {
        if (StrUtil.isNotBlank(request.getSize()))
        {
            return request.getSize().trim();
        }
        String fromScene = plan.params().getString("size");
        return StrUtil.isBlank(fromScene) ? DEFAULT_SIZE : fromScene.trim();
    }

    /**
     * 取当前操作人，未登录时返回 system
     *
     * @return 操作人
     */
    private String currentOperator()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return StringUtils.isEmpty(username) ? "system" : username;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
