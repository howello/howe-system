package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.SettingsVO;

/**
 * 共享设置服务
 *
 * @author howe
 */
public interface IPreSettingsService {

    /**
     * 读取共享设置
     *
     * <p>预产期来自 {@code sys_config}，业务今天与孕周按业务时区计算，
     * 能力来自当前账号的有效孕期角色。</p>
     *
     * @return 共享设置
     */
    SettingsVO getSettings();
}
