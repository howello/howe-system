package com.howe.ai.config;

import org.springframework.stereotype.Service;
import java.util.*;
import com.howe.ai.application.SecretCipher;
import com.howe.ai.web.AiConfigRequest;

@Service
public class AiConfigService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Map<String,String[]> SPECS = Map.of(
        "providers", new String[]{"ai_provider","provider_id"},
        "models", new String[]{"ai_model","model_id"},
        "channels", new String[]{"ai_channel","channel_id"},
        "routes", new String[]{"ai_route_policy","policy_id"},
        "route-items", new String[]{"ai_route_item","route_item_id"},
        "prices", new String[]{"ai_model_price","price_id"},
        "agents", new String[]{"ai_agent","agent_id"}
    );
    private final AiConfigMapper mapper;
    private final SecretCipher cipher;
    public AiConfigService(AiConfigMapper mapper) { this(mapper, null); }
    /** Spring 注入入口：主密钥未配置时不存在 SecretCipher bean，此处按缺省处理并在调用点失败关闭。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AiConfigService(org.springframework.beans.factory.ObjectProvider<AiConfigMapper> mapper,
                           org.springframework.beans.factory.ObjectProvider<SecretCipher> cipher) {
        this(mapper.getIfAvailable(), cipher.getIfAvailable());
    }
    public AiConfigService(AiConfigMapper mapper, SecretCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }
    public List<Map<String,Object>> list(String resource, String keyword, int pageNum, int pageSize) {
        String[] s = spec(resource); if (mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置"); int size = clamp(pageSize);
        return mapper.list(s[0], keyword == null ? "" : keyword, Math.max(0,pageNum-1)*size, size);
    }
    public Map<String,Object> get(String resource, long id) { if(mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置"); String[] s=spec(resource); return mapper.get(s[0],s[1],id); }
    public int toggle(String resource,long id,boolean enabled) { if(mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置"); String[] s=spec(resource); return mapper.toggle(s[0],s[1],id,enabled?"1":"0"); }
    public int delete(String resource,long id) { if(mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置"); String[] s=spec(resource); return mapper.delete(s[0],s[1],id); }
    public int clamp(int n) { return Math.max(1,Math.min(MAX_PAGE_SIZE,n)); }

    public String validateResource(String resource) {
        spec(resource);
        return resource;
    }

    public void validatePrice(Long modelId, String currency, String inputPrice, String outputPrice,
                              String cachePrice, String imagePrice, String effectiveFrom) {
        if (modelId == null || modelId <= 0 || currency == null || !currency.matches("[A-Z]{3}")
                || blank(inputPrice) || blank(outputPrice) || blank(cachePrice) || blank(imagePrice)
                || blank(effectiveFrom)) {
            throw new IllegalArgumentException("价格字段不合法");
        }
        try {
            if (new java.math.BigDecimal(inputPrice).signum() < 0 || new java.math.BigDecimal(outputPrice).signum() < 0
                    || new java.math.BigDecimal(cachePrice).signum() < 0 || new java.math.BigDecimal(imagePrice).signum() < 0) {
                throw new IllegalArgumentException("价格不能为负数");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("价格格式不合法", ex);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public EncryptedKey encryptForStorage(String secret) {
        if (cipher == null) throw new IllegalStateException("AI_MASTER_KEY 未配置");
        String ciphertext = cipher.encrypt(secret);
        return new EncryptedKey(ciphertext, 1, cipher.mask(ciphertext));
    }
    public long replaceApiKey(long channelId, String secret) {
        return replaceApiKeyValue(channelId, secret).keyId();
    }
    public Replacement replaceApiKeyValue(long channelId, String secret) {
        EncryptedKey key = encryptForStorage(secret);
        if (mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置");
        mapper.disableApiKeys(channelId);
        long keyId = mapper.insertApiKey(channelId, key.ciphertext(), key.keyVersion());
        return new Replacement(keyId, key.summary(), key.keyVersion());
    }
    public record Replacement(long keyId, String summary, int keyVersion) {}
    public Map<String,Object> prepare(String resource, AiConfigRequest request) {
        spec(resource);
        if (request == null || request.key() == null || !request.key().matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("配置编码不合法");
        Map<String,Object> result = new HashMap<>();
        result.put("key", request.key()); result.put("name", request.name()); result.put("configJson", request.configJson()); result.put("enabled", request.enabled());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            EncryptedKey key = encryptForStorage(request.apiKey());
            result.put("secretCipher", key.ciphertext()); result.put("keyVersion", key.keyVersion()); result.put("keySummary", key.summary());
        }
        return result;
    }

    public int create(String resource, AiConfigRequest request) {
        if (mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置");
        Map<String,Object> p = prepare(resource, request);
        String[] s = spec(resource);
        p.put("enabled", p.get("enabled") == null ? "0" : p.get("enabled"));
        return mapper.insert(s[0], columns(resource), values(resource), p);
    }

    public int update(String resource, long id, AiConfigRequest request) {
        if (mapper == null) throw new IllegalStateException("AI 配置持久化服务未配置");
        Map<String,Object> p = prepare(resource, request);
        String[] s = spec(resource);
        p.put("enabled", p.get("enabled") == null ? "0" : p.get("enabled"));
        return mapper.update(s[0], s[1], id, assignments(resource), p);
    }

    private static String columns(String resource) {
        if ("agents".equals(resource)) return "agent_key,name,status,draft_json,create_by,create_time,update_by,update_time";
        if ("providers".equals(resource)) return "provider_key,name,provider_type,enabled,config_json";
        if ("models".equals(resource)) return "model_key,model_name,enabled,capabilities";
        if ("channels".equals(resource)) return "channel_key,name,enabled,config_json";
        return "policy_key,name,enabled,policy_json";
    }
    private static String values(String resource) {
        if ("agents".equals(resource)) return "#{params.key},#{params.name},'0',#{params.configJson},'admin',NOW(),'admin',NOW()";
        if ("providers".equals(resource)) return "#{params.key},#{params.name},'custom',#{params.enabled},#{params.configJson}";
        if ("models".equals(resource)) return "#{params.key},#{params.name},#{params.enabled},#{params.configJson}";
        return "#{params.key},#{params.name},#{params.enabled},#{params.configJson}";
    }
    private static String assignments(String resource) {
        if ("agents".equals(resource)) return "name=#{params.name},draft_json=#{params.configJson},update_time=NOW()";
        if ("providers".equals(resource)) return "name=#{params.name},enabled=#{params.enabled},config_json=#{params.configJson}";
        if ("models".equals(resource)) return "model_name=#{params.name},enabled=#{params.enabled},capabilities=#{params.configJson}";
        if ("channels".equals(resource)) return "name=#{params.name},enabled=#{params.enabled},config_json=#{params.configJson}";
        return "name=#{params.name},enabled=#{params.enabled},policy_json=#{params.configJson}";
    }
    public record EncryptedKey(String ciphertext, int keyVersion, String summary) {}
    private String[] spec(String resource) { String[] s=SPECS.get(resource); if(s==null) throw new IllegalArgumentException("不支持的配置资源"); return s; }
}
