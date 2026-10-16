package com.howe.web.controller.home;

import com.howe.blog.service.IBlogStatsService;
import com.howe.meal.service.IMealStatsService;
import com.howe.share.service.IShareStatsService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页待办聚合服务
 *
 * <p>把「待我处理」的事项聚合成一张卡的数据。每项只在当前用户有对应权限时
 * 才出现；全部项都无权限时返回空列表，由卡片目录决定是否下发该卡片。</p>
 *
 * <p>各模块数据范围遵循各自既有的隔离规则（点餐按本部门）。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class HomeTodoService {

    /** 待办项的跳转路径（前端直接使用） */
    private static final String PATH_PROPOSALS = "/meal/proposal";
    private static final String PATH_ORDERS = "/meal/order";
    private static final String PATH_DRAFTS = "/blog/draft";
    private static final String PATH_SHARES = "/share/link";

    private final com.howe.framework.web.service.PermissionService permissionService;
    private final IMealStatsService mealStatsService;
    private final IBlogStatsService blogStatsService;
    private final IShareStatsService shareStatsService;

    public HomeTodoService(com.howe.framework.web.service.PermissionService permissionService,
                           IMealStatsService mealStatsService,
                           IBlogStatsService blogStatsService,
                           IShareStatsService shareStatsService) {
        this.permissionService = permissionService;
        this.mealStatsService = mealStatsService;
        this.blogStatsService = blogStatsService;
        this.shareStatsService = shareStatsService;
    }

    /**
     * 聚合当前用户可见的待办项
     *
     * <p>单项取数失败只跳过该项，不影响其它待办项。</p>
     *
     * @return 待办项列表；无任何有权限的事项时为空列表
     */
    public List<TodoItem> listTodos() {
        List<TodoItem> items = new ArrayList<>();
        addIfPermitted(items, "meal:proposal:list", "待审新菜提案", PATH_PROPOSALS,
                () -> mealStatsService.countPendingProposals());
        addIfPermitted(items, "meal:order:list,meal:order:dispatch", "待接订单", PATH_ORDERS,
                () -> mealStatsService.countPendingOrders());
        addIfPermitted(items, "blog:draft:list", "未发布草稿", PATH_DRAFTS,
                () -> blogStatsService.countUnpublishedDrafts());
        addIfPermitted(items, "share:link:list", "24 小时内过期分享", PATH_SHARES,
                () -> shareStatsService.countExpiringSoon());
        return items;
    }

    /**
     * 当前用户有权限处理的待办项数量
     *
     * <p>供卡片目录判定 F3 是否下发：为 0 时整卡不出现。</p>
     *
     * @return 有权限的待办项数量
     */
    public int countVisibleItems() {
        return listTodos().size();
    }

    private void addIfPermitted(List<TodoItem> items, String permissions, String label,
                                String path, CountSupplier supplier) {
        if (!permissionService.hasAnyPermi(permissions)) {
            return;
        }
        try {
            items.add(new TodoItem(label, supplier.get(), path));
        } catch (Exception e) {
            // 单项失败不影响其它待办项
            log.warn("首页待办项[{}]取数失败：{}", label, e.getMessage());
        }
    }

    /**
     * 待办项
     *
     * @param label 事项名称
     * @param count 数量
     * @param path  前端跳转路径
     * @author howe
     */
    @Schema(description = "首页待办项")
    public record TodoItem(
            @Schema(description = "事项名称") String label,
            @Schema(description = "数量") long count,
            @Schema(description = "跳转路径") String path) {
    }

    /**
     * 待办计数取数函数
     *
     * @author howe
     */
    @FunctionalInterface
    private interface CountSupplier {
        /**
         * 取计数
         *
         * @return 数量
         */
        long get();
    }
}
