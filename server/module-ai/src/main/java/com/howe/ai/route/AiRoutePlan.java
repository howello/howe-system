package com.howe.ai.route;

import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * 路由解析结果
 *
 * <p>{@code targets} 的第一项是主模型，其余是降级链（按顺序）。
 * 只有命中场景路由时才有降级链；显式指定与全局默认都只有一项。</p>
 *
 * @param scene      场景标识
 * @param capability 能力类型
 * @param targets    候选目标，第一项为主模型
 * @param params     场景级参数覆盖，可能为空对象
 * @author howe
 */
public record AiRoutePlan(String scene, String capability, List<AiRouteTarget> targets, JSONObject params)
{
    /**
     * 取主目标
     *
     * @return 主目标
     */
    public AiRouteTarget primary()
    {
        return targets.get(0);
    }

    /**
     * 是否存在降级候选
     *
     * @return 有备用模型时为 true
     */
    public boolean hasFallback()
    {
        return targets.size() > 1;
    }
}
