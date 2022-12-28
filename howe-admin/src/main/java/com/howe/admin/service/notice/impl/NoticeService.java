package com.howe.admin.service.notice.impl;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;
import com.howe.common.dto.notice.NoticeDTO;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/28 10:22 星期三
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public interface NoticeService extends IService<NoticeDTO> {


    /**
     * 获取通知列表
     *
     * @param noticeDTO
     * @return
     */
    List<NoticeDTO> getNoticeList(NoticeDTO noticeDTO);


    /**
     * 分页获取通知
     *
     * @param noticeDTO
     * @return
     */
    PageInfo<NoticeDTO> getNoticePage(NoticeDTO noticeDTO);

    /**
     * 更新通知
     *
     * @param noticeDTO
     * @return
     */
    Boolean updateNotice(NoticeDTO noticeDTO);

    /**
     * 添加通知
     *
     * @param noticeDTO
     * @return
     */
    Boolean addNotice(NoticeDTO noticeDTO);

    /**
     * 删除通知
     *
     * @param noticeIds
     * @return
     */
    List<NoticeDTO> delNotice(String noticeIds);
}
