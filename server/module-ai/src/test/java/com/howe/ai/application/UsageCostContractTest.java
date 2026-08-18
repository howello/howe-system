package com.howe.ai.application;

import com.howe.ai.persistence.AiFactMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class UsageCostContractTest {
    @Test
    void usageAndCostAreBackedByCallMappers() throws Exception {
        assertNotNull(AiFactMapper.class.getMethod("selectModelCalls", long.class, long.class, int.class, int.class));
        assertNotNull(AiFactMapper.class.getMethod("selectToolCalls", long.class, long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listUsage", long.class, long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listToolUsage", long.class, long.class, int.class, int.class));
    }
}
