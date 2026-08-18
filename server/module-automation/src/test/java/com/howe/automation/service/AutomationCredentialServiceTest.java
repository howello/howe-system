package com.howe.automation.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import com.howe.common.exception.ServiceException;

class AutomationCredentialServiceTest
{
    private final AutomationCredentialService service = new AutomationCredentialService();

    @Test
    void shouldRejectCredentialAliasWithUnsafeCharacters()
    {
        assertThrows(ServiceException.class, () -> service.getRequired("site-a/password"));
    }
}
