package com.howe.ai.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiRunEvent;

class EventPersistenceBridgeTest {
    @Test
    void eventStoreCanPublishFactAfterSequenceAllocation() throws Exception {
        Class<?> store = Class.forName("com.howe.ai.event.AiFactEventStore");
        assertTrue(java.util.Arrays.stream(store.getMethods()).anyMatch(method -> method.getName().equals("append")));
        assertTrue(java.util.Arrays.stream(store.getMethods()).anyMatch(method -> method.getName().equals("readAfter")));
    }

    @Test
    void eventBridgePublishesOnlyAfterFactAppend() throws Exception {
        Class<?> bridge = Class.forName("com.howe.ai.event.RedisEventBridge");
        assertTrue(java.util.Arrays.stream(bridge.getMethods()).anyMatch(method -> method.getName().equals("publish")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/event/RedisEventBridge.java"));
        assertTrue(source.indexOf("facts.append") < source.indexOf("realtime.publish"));
    }

    @Test
    void eventReadUsesPersistencePageAfterCursor() throws Exception {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
        assertTrue(mapper.contains("selectRunEventsAfter"));
        assertTrue(mapper.contains("sequence_no &gt; #{sequence}"));
    }
}
