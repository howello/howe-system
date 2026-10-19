package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 企业微信群机器人响应
 *
 * <p>发送与上传接口共用。{@code errcode = 0} 表示成功；常见错误码：
 * 45009 频率超限、93000 webhook 无效、93017 消息内容为空。</p>
 *
 * @author howe
 */
@Data
public class WeComRobotResponse {

    /** 是否成功 */
    private boolean success;

    /** 企业微信错误码，0 表示成功 */
    private Integer errcode;

    /** 企业微信错误信息 */
    private String errmsg;

    /** 上传文件返回的 media_id，仅上传接口有值 */
    @JSONField(name = "media_id")
    private String mediaId;

    /** 上传文件返回的创建时间，仅上传接口有值 */
    @JSONField(name = "created_at")
    private String createdAt;

    /** 上传文件类型，仅上传接口有值 */
    private String type;

    /**
     * 构造成功响应
     *
     * @param errcode 错误码
     * @param errmsg  错误信息
     * @return 响应
     */
    public static WeComRobotResponse ok(Integer errcode, String errmsg) {
        WeComRobotResponse response = new WeComRobotResponse();
        response.setSuccess(true);
        response.setErrcode(errcode);
        response.setErrmsg(errmsg);
        return response;
    }

    /**
     * 构造失败响应
     *
     * @param errcode 错误码；本地参数错误使用负数
     * @param errmsg  可读错误信息
     * @return 响应
     */
    public static WeComRobotResponse fail(Integer errcode, String errmsg) {
        WeComRobotResponse response = new WeComRobotResponse();
        response.setSuccess(false);
        response.setErrcode(errcode);
        response.setErrmsg(errmsg);
        return response;
    }

    /**
     * 根据企业微信返回的 errcode 刷新成功标记
     */
    public void refreshSuccess() {
        this.success = errcode != null && errcode == 0;
    }
}
