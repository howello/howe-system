package com.howe.system.service;

import java.util.List;

/**
 * 工具专用的公告批量已读结果。
 *
 * @param acceptedIds 本次新写入已读的公告ID，升序
 * @param alreadyReadIds 本次调用前已读的公告ID，升序
 */
public record SysNoticeReadBatchResult(List<Long> acceptedIds, List<Long> alreadyReadIds) {

    /** 防御性复制两个集合，避免调用方修改结果。 */
    public SysNoticeReadBatchResult {
        acceptedIds = acceptedIds == null ? List.of() : List.copyOf(acceptedIds);
        alreadyReadIds = alreadyReadIds == null ? List.of() : List.copyOf(alreadyReadIds);
    }
}
