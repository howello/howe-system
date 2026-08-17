package com.howe.ai.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Map;
import com.howe.ai.application.SecretCipher;
import com.howe.ai.web.AiConfigRequest;

class AiConfigServiceTest {
 @Test void pageSizeIsCapped() {
   AiConfigService s = new AiConfigService(null);
   assertEquals(50,s.clamp(100000)); assertEquals(1,s.clamp(0));
 }
 @Test void missingMasterKeyFailsClosedBeforeWrite() {
   assertThrows(IllegalStateException.class, () -> new AiConfigService(null, new SecretCipher(() -> null))
       .replaceApiKey(7, "secret"));
 }
 @Test void apiKeyIsEncryptedAndResponseOnlyContainsSummary() {
   var service = new AiConfigService(null, new SecretCipher(() -> "01234567890123456789012345678901"));
   var encrypted = service.encryptForStorage("plain-secret");
   assertNotEquals("plain-secret", encrypted.ciphertext());
   assertEquals(1, encrypted.keyVersion());
   assertFalse(encrypted.summary().contains("plain-secret"));
 }
 @Test void createAndUpdateDtoRejectSecretEchoAndClampPageSize() {
   var service = new AiConfigService(null, new SecretCipher(() -> "01234567890123456789012345678901"));
   var result = service.prepare("providers", new com.howe.ai.web.AiConfigRequest("openai", "OpenAI", "{}", "1", "secret"));
   assertFalse(result.containsKey("apiKey"));
   assertEquals(50, service.clamp(999));
 }
 @Test void createAndUpdateDelegateToMapper() {
   final boolean[] called = {false};
   var mapper = (AiConfigMapper) Proxy.newProxyInstance(AiConfigMapper.class.getClassLoader(), new Class[]{AiConfigMapper.class}, (o,m,a) -> {
     if (m.getName().equals("insert") || m.getName().equals("update")) { called[0] = true; return 1; }
     return null;
   });
   var s = new AiConfigService(mapper, new SecretCipher(() -> "01234567890123456789012345678901"));
   s.create("providers", new AiConfigRequest("openai", "OpenAI", "{}", "1", null));
   s.update("providers", 1L, new AiConfigRequest("openai", "OpenAI", "{}", "1", null));
   assertTrue(called[0]);
 }
 @Test void unknownResourceRejected() {
   AiConfigService s = new AiConfigService(null);
   assertThrows(IllegalArgumentException.class, () -> s.list("unknown",null,1,10));
 }
 @Test void channelWritesChannelTableColumnsInsteadOfRoutePolicyColumns() {
   final String[] captured = new String[3];
   var mapper = (AiConfigMapper) Proxy.newProxyInstance(AiConfigMapper.class.getClassLoader(), new Class[]{AiConfigMapper.class}, (o,m,a) -> {
     if (m.getName().equals("insert")) { captured[0]=String.valueOf(a[0]); captured[1]=String.valueOf(a[1]); return 1; }
     if (m.getName().equals("update")) { captured[2]=String.valueOf(a[3]); return 1; }
     return null;
   });
   var s = new AiConfigService(mapper, new SecretCipher(() -> "01234567890123456789012345678901"));
   s.create("channels", new AiConfigRequest("main-channel", "主渠道", "{}", "1", null));
   s.update("channels", 1L, new AiConfigRequest("main-channel", "主渠道", "{}", "1", null));
   assertEquals("ai_channel", captured[0]);
   assertEquals("channel_key,name,enabled,config_json", captured[1]);
   assertEquals("name=#{params.name},enabled=#{params.enabled},config_json=#{params.configJson}", captured[2]);
 }
}
