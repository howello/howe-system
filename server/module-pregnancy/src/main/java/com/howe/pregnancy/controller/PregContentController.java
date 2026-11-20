package com.howe.pregnancy.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import com.howe.pregnancy.service.impl.PregContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.regex.Pattern;

/** 孕周、食物及随包配图的只读接口。 */
@RestController
@RequestMapping("/pregnancy/content")
@RequiredArgsConstructor
@Tag(name = "孕期-参考资料")
public class PregContentController extends BaseController {

    private static final Pattern IMAGE_ID = Pattern.compile("[a-f0-9]{64}");
    private static final MediaType WEBP = MediaType.valueOf("image/webp");

    private final PregContentService contentService;

    /** 查询指定孕周的阶段资料。 */
    @Operation(summary = "查看孕周变化")
    @GetMapping("/weeks/{week}")
    public AjaxResult week(@PathVariable("week") int week) {
        return success(contentService.week(week));
    }

    /** 按关键词和孕期评级分页查询。 */
    @Operation(summary = "查找孕期食物资料")
    @GetMapping("/foods")
    public AjaxResult foods(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String rating,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "36") int size) {
        return success(contentService.foods(keyword, rating, page, size));
    }

    /** 获取食物参考详情。 */
    @Operation(summary = "查看食物资料")
    @GetMapping("/foods/{id}")
    public AjaxResult food(@PathVariable("id") String id) {
        return success(contentService.food(id));
    }

    /** 返回随包只读图片，使 H5 和 App 的图片组件可以直接渲染。 */
    @Anonymous
    @RateLimiter(time = 60, count = 600, limitType = LimitType.IP)
    @Operation(summary = "读取孕期内置配图")
    @GetMapping("/images/{id}.webp")
    public ResponseEntity<Resource> image(@PathVariable("id") String id) {
        if (!IMAGE_ID.matcher(id).matches()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new ClassPathResource("pregnancy/images/" + id + ".webp");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(WEBP)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(resource);
    }
}
