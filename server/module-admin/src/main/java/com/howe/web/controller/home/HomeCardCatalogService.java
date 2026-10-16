package com.howe.web.controller.home;

import com.howe.framework.web.service.PermissionService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页卡片目录服务
 *
 * <p>负责决定「当前登录用户能看到哪些卡片、按什么顺序」，判定依据集中在后端：
 * 权限（沿用既有权限点，支持 {@code *:*:*} 通配）与模块数据表可用性。前端只按
 * 下发的清单渲染，不自行猜测模块是否存在。</p>
 *
 * <p>卡片清单不携带统计数据，统计数据由各卡片各自的接口提供。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class HomeCardCatalogService {

    /** 角色层：按监控权限出现（服务器资源 / 系统监控 / 缓存监控 三张独立卡） */
    private static final List<CardDef> ROLE_CARDS = List.of(
            new CardDef("R1", null, "monitor:server:list"),
            new CardDef("R2", null, "monitor:online:list,monitor:logininfor:list,monitor:operlog:list"),
            new CardDef("R3", null, "monitor:cache:list"));

    /** 模块层：模块表已初始化 + 有权限才出现 */
    private static final List<CardDef> MODULE_CARDS = List.of(
            // 博客四块两行：第一行「博客统计 + 友链」，第二行「博客趋势 + 博客分类」
            new CardDef("M1", "blog", "blog:article:list,blog:draft:list"),
            new CardDef("N13", "blog", "blog:link:list"),
            new CardDef("M14", "blog", "blog:article:list,blog:draft:list"),
            new CardDef("M15", "blog", "blog:article:list,blog:draft:list"),
            new CardDef("M2", "meal", "meal:order:list,meal:review:list"),
            new CardDef("N9", "meal", "meal:dish:list"),
            new CardDef("N10", "meal", "meal:appVersion:list"),
            new CardDef("N11", "meal", "meal:order:list"),
            new CardDef("M3", "ai", "ai:calllog:stats"),
            new CardDef("N5", "ai", "ai:channel:list"),
            new CardDef("N8", "ai", "ai:calllog:list"),
            new CardDef("M4", "share", "share:link:list"),
            new CardDef("N16", "share", "share:link:list"));

    private final PermissionService permissionService;
    private final HomeModuleAvailabilityService moduleAvailabilityService;
    private final HomeTodoService homeTodoService;
    private final QuickEntryService quickEntryService;
    private final HomeProfileService homeProfileService;

    public HomeCardCatalogService(PermissionService permissionService,
                                  HomeModuleAvailabilityService moduleAvailabilityService,
                                  HomeTodoService homeTodoService,
                                  QuickEntryService quickEntryService,
                                  HomeProfileService homeProfileService) {
        this.permissionService = permissionService;
        this.moduleAvailabilityService = moduleAvailabilityService;
        this.homeTodoService = homeTodoService;
        this.quickEntryService = quickEntryService;
        this.homeProfileService = homeProfileService;
    }

    /**
     * 获取当前用户可见的卡片清单（按固定层 → 角色层 → 模块层顺序）
     *
     * <p>固定层中 F3/N1/N4 的显示内容本身按权限/模块过滤，全部无可见项时
     * 整卡不下发（见 spec §3.3/§3.4/§3.5）；F1/F2 对所有登录用户可见。</p>
     *
     * @return 可见卡片的标识列表，顺序即前端渲染顺序
     */
    public List<String> visibleCards() {
        List<String> cards = new ArrayList<>();
        // F1 欢迎卡、F2 公告卡：所有登录用户可见
        cards.add("F1");
        cards.add("F2");
        if (homeTodoService.countVisibleItems() > 0) {
            cards.add("F3");
        }
        if (quickEntryService.countVisibleEntries() > 0) {
            cards.add("N1");
        }
        if (homeProfileService.countVisibleFootprintItems() > 0) {
            cards.add("N4");
        }
        ROLE_CARDS.stream().filter(this::hasAnyPermission).forEach(c -> cards.add(c.key()));
        MODULE_CARDS.stream()
                .filter(c -> moduleAvailable(c.module()))
                .filter(this::hasAnyPermission)
                .forEach(c -> cards.add(c.key()));
        return cards;
    }

    private boolean moduleAvailable(String module) {
        if (module == null) {
            return true;
        }
        // 四个模块统一用 information_schema 探测（含 blog），与 HomeModuleAvailabilityService 口径一致
        return moduleAvailabilityService.isAvailable(module);
    }

    private boolean hasAnyPermission(CardDef def) {
        if (def.permissions() == null || def.permissions().isBlank()) {
            return true;
        }
        return permissionService.hasAnyPermi(def.permissions());
    }

    /**
     * 卡片定义
     *
     * @param key         卡片标识（前端注册表以此匹配组件）
     * @param module      所属模块标识；null 表示不依赖模块
     * @param permissions 所需权限（逗号分隔，任一满足即可）；null 表示无权限要求
     * @author howe
     */
    private record CardDef(String key, String module, String permissions) {
    }

    /**
     * 卡片清单响应
     *
     * @param cards 可见卡片标识列表，顺序即渲染顺序
     * @author howe
     */
    @Schema(description = "首页可见卡片清单")
    public record HomeCards(
            @Schema(description = "可见卡片标识列表，顺序即渲染顺序") List<String> cards) {
    }
}
