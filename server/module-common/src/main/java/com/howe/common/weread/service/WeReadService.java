package com.howe.common.weread.service;

import com.fasterxml.jackson.databind.JsonNode;

/** 微信读书网关的公共服务，凭据由调用模块提供。 */
public interface WeReadService {
    /** 搜索电子书及封面。 */
    JsonNode search(String apiKey, String keyword, String context);

    /** 查询单本电子书的基本信息。 */
    JsonNode bookInfo(String apiKey, String bookId);

    /** 读取授权账号的书架和自定义书单。 */
    JsonNode shelf(String apiKey);

    /** 按时间游标读取一页笔记本概览。 */
    JsonNode notebooks(String apiKey, Long lastSort);

    /** 读取单本书的个人划线及章节信息。 */
    JsonNode highlights(String apiKey, String bookId);

    /** 按游标读取一页个人想法和点评。 */
    JsonNode reviews(String apiKey, String bookId, long syncKey);
}
