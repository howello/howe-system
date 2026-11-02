package com.howe.share.service;

import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 分享策略版本改变时，密码票据与查看票据均应立即失效。 */
class ShareTicketServiceTest {
    /** 改密或手动撤销会改变版本，而逻辑焚毁不改变最后一次合法会话的版本。 */
    @Test
    void invalidatesTicketsWhenPolicyVersionChanges() {
        RedisCache redis = mock(RedisCache.class);
        Map<String, Object> values = new HashMap<>();
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(redis).setCacheObject(anyString(), any(), anyInt(), any());
        when(redis.getCacheObject(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        ShareTicketService service = new ShareTicketService(redis);
        ShareLink link = new ShareLink();
        link.setCode("a1b2c3d4");
        link.setAccessVersion(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var config = mockStatic(ConfigUtils.class)) {
            config.when(() -> ConfigUtils.getInt(anyString(), anyInt())).thenReturn(300);
            service.issuePwd(link, request, response);
            service.issueView(link, request, response);
        }
        Cookie[] cookies = response.getHeaders("Set-Cookie").stream().map(header -> {
            String[] pair = header.substring(0, header.indexOf(';')).split("=", 2);
            return new Cookie(pair[0], pair[1]);
        }).toArray(Cookie[]::new);
        request.setCookies(cookies);
        assertTrue(service.hasPwd(link, request));
        assertTrue(service.hasView(link, request));
        link.setStatus(ShareConstants.STATUS_BURNED);
        assertTrue(service.hasView(link, request));
        link.setAccessVersion(2L);
        assertFalse(service.hasPwd(link, request));
        assertFalse(service.hasView(link, request));
    }
}
