package com.howe.framework.tool.service;

import com.howe.framework.tool.dto.SystemHealthDtos;

/**
 * 服务器健康摘要读取服务。
 *
 * <p>只做本地采样：不写库、不发起外部探测、不构造包含主机信息的对象。
 * 采样失败必须抛出异常，不能伪造 0。</p>
 */
public interface ISystemHealthReadService {

    /**
     * 采集一次健康摘要。
     *
     * @return 健康摘要
     */
    SystemHealthDtos sample();
}
