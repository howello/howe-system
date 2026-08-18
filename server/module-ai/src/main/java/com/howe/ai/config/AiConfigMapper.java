package com.howe.ai.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiConfigMapper {
    List<Map<String,Object>> list(@Param("table") String table, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    Map<String,Object> get(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") long id);
    int insert(@Param("table") String table, @Param("columns") String columns, @Param("values") String values, @Param("params") Map<String,Object> params);
    int update(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") long id, @Param("assignments") String assignments, @Param("params") Map<String,Object> params);
    int toggle(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") long id, @Param("enabled") String enabled);
    int delete(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") long id);
    int insertApiKey(@Param("channelId") long channelId, @Param("ciphertext") String ciphertext, @Param("keyVersion") int keyVersion);
    int disableApiKeys(@Param("channelId") long channelId);
}
