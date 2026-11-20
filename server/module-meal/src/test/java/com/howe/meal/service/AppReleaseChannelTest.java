package com.howe.meal.service;

import com.howe.common.core.domain.model.LoginUser;
import com.howe.meal.domain.AppReleaseChannel;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.mapper.MealAppVersionMapper;
import com.howe.meal.service.impl.MealAppVersionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 回归验证点餐与宝宝不会共享最新资源包或管理授权。 */
class AppReleaseChannelTest {

    /** 每个应用按自己的版本号返回更新，旧客户端继续取点餐。 */
    @Test
    void updateChecksAreIsolated() {
        var service = service();
        var meal = service.checkUpdate(AppReleaseChannel.resolve(null), null, 100);
        var baby = service.checkUpdate(AppReleaseChannel.resolve("pregnancy"), null, 100);
        assertEquals(500, meal.getVersionCode());
        assertEquals(101, baby.getVersionCode());
        assertEquals("https://cdn.example.test/pregnancy.wgt", baby.getWgtUrl());
    }

    /** 旧点餐授权不能经列表、详情或删除入口操作宝宝渠道。 */
    @Test
    void pregnancyManagementRequiresItsOwnGrant() {
        var service = service();
        login(Set.of("tool:appVersion:list", "tool:appVersion:remove"));
        assertEquals("meal", service.selectMealAppVersionList(new MealAppVersion()).get(0).getAppKey());
        var query = new MealAppVersion();
        query.setAppKey("pregnancy");
        assertThrows(AccessDeniedException.class, () -> service.selectMealAppVersionList(query));
        assertThrows(AccessDeniedException.class, () -> service.selectMealAppVersionById(2L));
        assertThrows(AccessDeniedException.class, () -> service.deleteMealAppVersionByIds(new Long[] {2L}));

        login(Set.of("tool:appVersion:list", "tool:appVersion:pregnancy"));
        assertEquals("pregnancy", service.selectMealAppVersionList(query).get(0).getAppKey());
        assertEquals("pregnancy", service.selectMealAppVersionById(2L).getAppKey());
    }

    /** 清理测试使用的安全上下文。 */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** 使用明确的权限列表模拟管理端账号。 */
    private static void login(Set<String> permissions) {
        var user = new LoginUser(9L, null, null, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    /** 单独模拟每个渠道的版本记录，任何未预期的变更都会失败。 */
    private static MealAppVersionServiceImpl service() {
        var meal = version(1L, "meal", 500);
        var baby = version(2L, "pregnancy", 101);
        var channels = Map.of("meal", meal, "pregnancy", baby);
        var ids = Map.of(1L, meal, 2L, baby);
        var mapper = (MealAppVersionMapper) Proxy.newProxyInstance(MealAppVersionMapper.class.getClassLoader(),
                new Class<?>[] {MealAppVersionMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectLatestPublished" -> channels.get(args[0]);
                    case "selectMealAppVersionById" -> ids.get(args[0]);
                    case "selectMealAppVersionList" -> List.of(channels.get(((MealAppVersion) args[0]).getAppKey()));
                    default -> throw new AssertionError("未预期的访问：" + method.getName());
                });
        return new MealAppVersionServiceImpl(mapper, null, null, null);
    }

    /** 创建一条已发布版本。 */
    private static MealAppVersion version(Long id, String channel, int code) {
        var record = new MealAppVersion();
        record.setVersionId(id);
        record.setAppKey(channel);
        record.setVersionCode(code);
        record.setWgtUrl("https://cdn.example.test/" + channel + ".wgt");
        return record;
    }
}
