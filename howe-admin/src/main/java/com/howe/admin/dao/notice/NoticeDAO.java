package com.howe.admin.dao.notice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.notice.NoticeDTO;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/28 10:22 星期三
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Mapper
public interface NoticeDAO extends BaseMapper<NoticeDTO> {
}
