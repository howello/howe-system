package com.howe.ai.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AiSseControllerTest {
    @Test
    void exposesAuthenticatedEventStreamWithLastEventId() throws Exception {
        Class<?> controller = Class.forName("com.howe.ai.controller.AiSseController");
        Method method = controller.getDeclaredMethod("events", long.class, String.class,
            jakarta.servlet.http.HttpServletResponse.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        RequestMapping root = controller.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/ai/admin/runs"}, root.value());
        assertArrayEquals(new String[]{"/{runId}/events/stream"}, mapping.value());
        assertArrayEquals(new String[]{"text/event-stream"}, mapping.produces());
        assertNotNull(method.getParameters()[1].getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class));
    }

    /**
     * 端点必须是持续推送的长连接，而不是一次性返回集合。
     * 一次性返回既拿不到实时事件，Spring MVC 也没有对应的消息转换器。
     */
    @Test
    void streamEndpointIsALongLivedConnectionInsteadOfOneShotCollection() throws Exception {
        Method method = Class.forName("com.howe.ai.controller.AiSseController")
            .getDeclaredMethod("events", long.class, String.class,
                jakarta.servlet.http.HttpServletResponse.class);
        assertEquals(void.class, method.getReturnType(), "长连接端点不应有返回体");

        String source = Files.readString(Path.of("src/main/java/com/howe/ai/controller/AiSseController.java"));
        assertTrue(source.contains("Last-Event-ID"));
        assertTrue(source.contains("stream.stream("), "必须委派给事件流写入器：" + source);
        assertTrue(source.contains("checkError"), "必须能感知客户端断开");
        assertFalse(source.contains("ServerSentEvent"), "不得使用 WebFlux 的 ServerSentEvent 类型");
    }
}
