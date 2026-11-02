package com.howe.share.service;

import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.storage.StoredObject;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import com.howe.share.mapper.ShareAccessLogMapper;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.storage.ShareStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 撤销策略必须在每次原始文件请求上执行，同时保留最后一次合法预览。 */
class SharePublicServiceTest {
    /** 手动失效时即使曾签发票据，也不得继续读取存储。 */
    @Test
    void rejectsManuallyRevokedLinkBeforeOpeningStorage() {
        ShareLinkMapper links = mock(ShareLinkMapper.class);
        ShareStorageService storage = mock(ShareStorageService.class);
        ShareLink link = new ShareLink();
        link.setStatus(ShareConstants.STATUS_INVALID);
        when(links.selectShareLinkByCode("testcode")).thenReturn(link);
        SharePublicService service = new SharePublicService(links, mock(ShareAccessLogMapper.class),
                mock(ShareRouteCache.class), storage, mock(ShareTicketService.class), mock(RedisCache.class));
        assertThrows(ServiceException.class, () -> service.openFile("testcode", "file", new MockHttpServletRequest()));
        verifyNoInteractions(storage);
    }

    /** 逻辑焚毁不能阻断最后一个合法会话加载剩余字节请求。 */
    @Test
    void allowsFinalLegitimateBurnedSession() throws Exception {
        ShareLinkMapper links = mock(ShareLinkMapper.class);
        ShareStorageService storage = mock(ShareStorageService.class);
        ShareTicketService tickets = mock(ShareTicketService.class);
        ShareLink link = new ShareLink();
        link.setLinkId(1L);
        link.setCode("testcode");
        link.setStatus(ShareConstants.STATUS_BURNED);
        link.setStorageType("0");
        link.setStorageLocation("share/test/a.txt");
        link.setContentType("text/plain");
        link.setFileName("a.txt");
        link.setFileSize(1L);
        when(links.selectShareLinkByCode("testcode")).thenReturn(link);
        when(tickets.hasView(eq(link), any())).thenReturn(true);
        StoredObject object = new StoredObject("key", "a.txt", "text/plain", 1, new ByteArrayInputStream(new byte[] { 1 }));
        when(storage.open(anyString(), anyString(), anyString(), anyString())).thenReturn(object);
        SharePublicService service = new SharePublicService(links, mock(ShareAccessLogMapper.class),
                mock(ShareRouteCache.class), storage, tickets, mock(RedisCache.class));
        try (StoredObject result = service.openFile("testcode", "file", new MockHttpServletRequest())) {
            assertSame(object, result);
        }
    }
}
