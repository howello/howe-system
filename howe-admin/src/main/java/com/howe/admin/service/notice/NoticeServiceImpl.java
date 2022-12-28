package com.howe.admin.service.notice;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.admin.dao.notice.NoticeDAO;
import com.howe.admin.service.notice.impl.NoticeService;
import com.howe.common.dto.notice.NoticeDTO;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/28 10:22 星期三
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeDAO, NoticeDTO> implements NoticeService {

    private final NoticeDAO noticeDAO;

    private final IdGeneratorUtil idGeneratorUtil;

    private final UserInfoUtils userInfoUtils;


    /**
     * 获取通知列表
     *
     * @param noticeDTO
     * @return
     */
    @Override
    public List<NoticeDTO> getNoticeList(NoticeDTO noticeDTO) {
        QueryWrapper<NoticeDTO> qw = MybatisUtils.assembleQueryWrapper(noticeDTO);
        List<NoticeDTO> noticeList = noticeDAO.selectList(qw);
        return noticeList;
    }

    /**
     * 分页获取通知
     *
     * @param noticeDTO
     * @return
     */
    @Override
    public PageInfo<NoticeDTO> getNoticePage(NoticeDTO noticeDTO) {
        PageHelper.startPage(noticeDTO.getPageNum(), noticeDTO.getPageSize());
        PageInfo<NoticeDTO> page = new PageInfo<>(this.getNoticeList(noticeDTO));
        return page;
    }

    /**
     * 更新通知
     *
     * @param noticeDTO
     * @return
     */
    @Override
    public Boolean updateNotice(NoticeDTO noticeDTO) {
        String username = userInfoUtils.getUsername();
        noticeDTO.setUpdateBy(username);
        noticeDTO.setUpdateTime(DateTime.now());
        return noticeDAO.updateById(noticeDTO) > 0;
    }

    /**
     * 添加通知
     *
     * @param noticeDTO
     * @return
     */
    @Override
    public Boolean addNotice(NoticeDTO noticeDTO) {
        String username = userInfoUtils.getUsername();
        DateTime now = DateTime.now();
        noticeDTO.setNoticeId(idGeneratorUtil.nextStr());
        noticeDTO.setCreateBy(username);
        noticeDTO.setCreateTime(now);
        noticeDTO.setUpdateBy(username);
        noticeDTO.setUpdateTime(now);
        return noticeDAO.insert(noticeDTO) > 0;
    }

    /**
     * 删除
     *
     * @param noticeIds
     * @return
     */
    @Override
    public List<NoticeDTO> delNotice(String noticeIds) {
        List<NoticeDTO> noticeList = noticeDAO.selectBatchIds(Arrays.asList(noticeIds));
        return noticeList;
    }
}
