package com.howe.meal.service;

import com.alibaba.fastjson2.JSON;
import com.howe.ai.api.AiClient;
import com.howe.ai.api.AiException;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.mapper.MealDishMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证菜品搜索的可见范围、AI 只扩词以及 AI 失败时的可读错误。 */
class MealDishSearchServiceTest {

    /** 本地直接命中时不再调用 AI。 */
    @Test
    void returnsLocalMatchesWithoutCallingAi() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        MealDish dish = new MealDish();
        dish.setDishId(7L);
        dish.setName("番茄炒蛋");
        when(mapper.selectMealDishSearchCandidates(List.of("番茄"), 10L, 8)).thenReturn(List.of(dish));

        var result = withDept(10L, () -> new MealDishSearchService(ai, mapper).search(" 番茄 "));

        assertEquals("local", result.source());
        assertEquals(1, result.items().size());
        verifyNoInteractions(ai);
    }

    /** 本地无匹配时，AI 只生成词项，最终菜品仍由带家庭范围的 SQL 返回。 */
    @Test
    void usesAiTermsButOnlyReturnsVisibleDishes() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        MealDish dish = new MealDish();
        dish.setDishId(9L);
        dish.setName("番茄牛腩");
        when(mapper.selectMealDishSearchCandidates(List.of("西红柿"), 10L, 8)).thenReturn(List.of());
        when(ai.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder()
                .structured(Map.of("terms", List.of("番茄", " 西红柿 ")))
                .build());
        when(mapper.selectMealDishSearchCandidates(argThat(terms ->
                terms.size() == 2 && terms.contains("西红柿") && terms.contains("番茄")), eq(10L), eq(8)))
                .thenReturn(List.of(dish));

        var result = withDept(10L, () -> new MealDishSearchService(ai, mapper).search("西红柿"));

        assertEquals("ai", result.source());
        assertEquals(9L, result.items().get(0).getDishId());
    }

    /** AI 结构化结果缺少 terms 时必须抛出可读错误，不能返回臆造菜品。 */
    @Test
    void rejectsUnstructuredAiOutput() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        when(mapper.selectMealDishSearchCandidates(anyList(), eq(10L), anyInt())).thenReturn(List.of());
        when(ai.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder().structured("not-a-map").build());

        ServiceException error = assertThrows(ServiceException.class,
                () -> withDept(10L, () -> new MealDishSearchService(ai, mapper).search("寿司")));
        assertTrue(error.getMessage().contains("AI 搜索暂不可用"));
        verify(mapper, times(1)).selectMealDishSearchCandidates(anyList(), any(), anyInt());
    }

    /** AI 调用失败按原有网关异常向上冒泡，由全局处理器转成可读提示。 */
    @Test
    void propagatesAiFailure() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        when(mapper.selectMealDishSearchCandidates(anyList(), eq(10L), anyInt())).thenReturn(List.of());
        when(ai.chat(any(AiChatRequest.class))).thenThrow(new AiException(AiErrorCode.AI_DISABLED));

        assertThrows(AiException.class, () -> withDept(10L, () -> new MealDishSearchService(ai, mapper).search("寿司")));
    }

    /** 空白与超长查询在访问数据库前就被拒绝。 */
    @Test
    void rejectsBlankAndOversizedQueries() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        MealDishSearchService service = new MealDishSearchService(ai, mapper);

        assertThrows(ServiceException.class, () -> service.search("   "));
        assertThrows(ServiceException.class, () -> service.search("菜".repeat(129)));
        verifyNoInteractions(mapper, ai);
    }

    /** 非平台账号缺少家庭归属时拒绝，避免空部门条件变成全库查询。 */
    @Test
    void rejectsMissingDeptForNonAdmin() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getDeptId).thenReturn(null);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> new MealDishSearchService(ai, mapper).search("番茄"));
            assertTrue(error.getMessage().contains("无法确定当前家庭"));
        }
        verifyNoInteractions(mapper);
    }

    /** 平台管理员不套家庭过滤，传 null 表示沿用列表接口的可见范围。 */
    @Test
    void adminSearchesWithoutDeptFilter() {
        MealDishMapper mapper = mock(MealDishMapper.class);
        AiClient ai = mock(AiClient.class);
        when(mapper.selectMealDishSearchCandidates(anyList(), isNull(), eq(8))).thenReturn(List.of());
        when(ai.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder()
                .structured(Map.of("terms", List.of("番茄")))
                .build());
        when(mapper.selectMealDishSearchCandidates(argThat(terms -> terms.size() == 1), isNull(), eq(8)))
                .thenReturn(List.of());
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            assertEquals("ai", new MealDishSearchService(ai, mapper).search("番茄").source());
        }
        verify(mapper, times(2)).selectMealDishSearchCandidates(anyList(), isNull(), eq(8));
    }

    /** 在模拟的登录上下文中执行，返回结果。 */
    private static <T> T withDept(Long deptId, java.util.function.Supplier<T> action) {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getDeptId).thenReturn(deptId);
            return action.get();
        }
    }
}
