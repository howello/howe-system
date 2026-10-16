package com.howe.web.controller.home;

import com.howe.framework.web.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 首页快捷入口服务
 *
 * <p>入口清单在前端配置，但「是否有任一可用入口」必须由后端判定：
 * spec §3.4 要求全部入口都无权限时 N1 整卡不下发，而卡片可见性判定集中在后端。</p>
 *
 * <p>权限点与前端 {@code QuickEntryCard.vue} 的 ENTRIES 保持一致。</p>
 *
 * @author howe
 */
@Service
public class QuickEntryService {

    /** 快捷入口所需权限（与前端 ENTRIES 一一对应） */
    private static final List<String> ENTRY_PERMISSIONS = List.of(
            "blog:article:add",
            "meal:dish:add",
            "share:link:add",
            "system:user:add");

    private final PermissionService permissionService;

    public QuickEntryService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 当前用户可用的快捷入口数量
     *
     * @return 有权限的入口数量；为 0 时 N1 不下发
     */
    public int countVisibleEntries() {
        return (int) ENTRY_PERMISSIONS.stream()
                .filter(permissionService::hasPermi)
                .count();
    }
}
