package com.howe.book.service;

import java.util.List;
import java.util.Map;

/** 在线书目和配图候选。 */
public interface BookSearchService {

    /** 查询微信读书候选的详细书目和封面。 */
    Map<String, Object> weReadBook(String bookId, String searchToken);
    /** 查询书目。 */
    List<Map<String, Object>> catalog(String keyword, String source);

    /** 查询可预览的配图。 */
    List<Map<String, Object>> images(String keyword);

    /**
     * 按指定来源查询配图。
     *
     * @param source bing（必应）/ so360（360）/ auto（必应优先，失败回退 360）
     */
    List<Map<String, Object>> images(String keyword, String source);
}
