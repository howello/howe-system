package com.howe.ai.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AiSseAuthorizationTest {
    @Test
    void streamEndpointChecksRunOwnershipBeforeReadingEvents() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/controller/AiSseController.java"));
        assertTrue(source.contains("requireRunAccess"));
        assertTrue(source.contains("SecurityUtils.getUserId()"));
    }

    @Test
    void controllerOwnsPersistenceAuthorizationBeforeEventRead() throws Exception {
        Class<?> controller = Class.forName("com.howe.ai.controller.AiSseController");
        assertTrue(java.util.Arrays.stream(controller.getConstructors())
            .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
            .anyMatch(type -> type.getName().equals("com.howe.ai.persistence.AiFactPersistenceService")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/controller/AiSseController.java"));
        assertTrue(source.indexOf("requireRunAccess") < source.indexOf("stream.stream("),
            "归属校验必须早于任何事件读取");
    }
}
