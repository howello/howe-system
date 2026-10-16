package com.howe.web.controller.home;

import com.howe.blog.service.IBlogStatsService;
import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.framework.web.service.PermissionService;
import com.howe.meal.service.IMealStatsService;
import com.howe.share.service.IShareStatsService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 首页欢迎卡与个人足迹服务
 *
 * <p>欢迎卡展示当前用户的昵称、角色名与上次登录信息；个人足迹只统计当前用户
 * 自己的数据。两者都只依赖登录态，不做业务权限区分（无权限或模块不可用的
 * 计数项由调用方按可见性决定是否展示）。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class HomeProfileService {

    private final IMealStatsService mealStatsService;
    private final IBlogStatsService blogStatsService;
    private final IShareStatsService shareStatsService;
    private final PermissionService permissionService;
    private final HomeModuleAvailabilityService moduleAvailabilityService;

    public HomeProfileService(IMealStatsService mealStatsService,
                              IBlogStatsService blogStatsService,
                              IShareStatsService shareStatsService,
                              PermissionService permissionService,
                              HomeModuleAvailabilityService moduleAvailabilityService) {
        this.mealStatsService = mealStatsService;
        this.blogStatsService = blogStatsService;
        this.shareStatsService = shareStatsService;
        this.permissionService = permissionService;
        this.moduleAvailabilityService = moduleAvailabilityService;
    }

    /**
     * 欢迎卡信息
     *
     * @return 昵称、角色名列表、上次登录时间与 IP
     */
    public WelcomeInfo getWelcomeInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        String nickName = loginUser.getUser().getNickName();
        List<String> roleNames = new ArrayList<>();
        if (loginUser.getUser().getRoles() != null) {
            for (SysRole role : loginUser.getUser().getRoles()) {
                if (role.getRoleName() != null && !role.getRoleName().isBlank()) {
                    roleNames.add(role.getRoleName());
                }
            }
        }
        Date loginDate = loginUser.getUser().getLoginDate();
        String lastLoginTime = loginDate == null
                ? null
                : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, loginDate).substring(0, 16);
        String loginIp = loginUser.getUser().getLoginIp();
        return new WelcomeInfo(nickName, roleNames, lastLoginTime,
                loginIp == null || loginIp.isBlank() ? null : loginIp);
    }

    /**
     * 个人足迹
     *
     * <p>各项独立取数，仅在「该模块可用 + 当前用户有该模块查看权限」时才统计；
     * 单项失败只跳过该项（对应模块可能未初始化）。</p>
     *
     * @return 个人足迹计数；不可用项为 null
     */
    public FootprintInfo getFootprint() {
        Long articles = visible("blog", "blog:article:list") ? safeCount(blogStatsService::countMyArticles) : null;
        Long orders = visible("meal", "meal:order:list") ? safeCount(mealStatsService::countMyOrders) : null;
        Long proposals = visible("meal", "meal:proposal:list") ? safeCount(mealStatsService::countMyProposals) : null;
        Long links = visible("share", "share:link:list") ? safeCount(shareStatsService::countMyLinks) : null;
        return new FootprintInfo(articles, orders, proposals, links);
    }

    /**
     * 当前用户可见的个人足迹项数量
     *
     * <p>供卡片目录判定 N4 是否下发：为 0 时整卡不出现（spec §3.5）。</p>
     *
     * @return 可见项数量
     */
    public int countVisibleFootprintItems() {
        FootprintInfo info = getFootprint();
        int count = 0;
        if (info.articles() != null) count++;
        if (info.orders() != null) count++;
        if (info.proposals() != null) count++;
        if (info.links() != null) count++;
        return count;
    }

    /**
     * 模块可用且当前用户具备查看权限
     *
     * @param module      模块标识
     * @param permission  查看权限
     * @return 可见返回 true
     */
    private boolean visible(String module, String permission) {
        return moduleAvailabilityService.isAvailable(module) && permissionService.hasPermi(permission);
    }

    private Long safeCount(CountSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("首页个人足迹取数跳过（模块可能未初始化）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 欢迎卡信息
     *
     * @param nickName      昵称
     * @param roleNames     角色名列表
     * @param lastLoginTime 上次登录时间（yyyy-MM-dd HH:mm）；从未登录时为 null
     * @param lastLoginIp   上次登录 IP；为空时为 null
     * @author howe
     */
    @Schema(description = "首页欢迎卡信息")
    public record WelcomeInfo(
            @Schema(description = "昵称") String nickName,
            @Schema(description = "角色名列表") List<String> roleNames,
            @Schema(description = "上次登录时间") String lastLoginTime,
            @Schema(description = "上次登录 IP") String lastLoginIp) {
    }

    /**
     * 个人足迹计数
     *
     * <p>值为 null 表示该模块不可用，前端不展示对应项。</p>
     *
     * @param articles  我发布的文章数；模块不可用时为 null
     * @param orders    我下过的订单数；模块不可用时为 null
     * @param proposals 我提交的提案数；模块不可用时为 null
     * @param links     我创建的分享数；模块不可用时为 null
     * @author howe
     */
    @Schema(description = "首页个人足迹")
    public record FootprintInfo(
            @Schema(description = "我发布的文章数；模块不可用时为 null") Long articles,
            @Schema(description = "我下过的订单数；模块不可用时为 null") Long orders,
            @Schema(description = "我提交的提案数；模块不可用时为 null") Long proposals,
            @Schema(description = "我创建的分享数；模块不可用时为 null") Long links) {
    }

    /**
     * 计数取数函数
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
        Long get();
    }
}
