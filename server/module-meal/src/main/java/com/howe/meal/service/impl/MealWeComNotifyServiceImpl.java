package com.howe.meal.service.impl;

import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.notify.wecom.WeComRobotClient;
import com.howe.common.notify.wecom.WeComRobotResponse;
import com.howe.common.notify.wecom.WeComTextMessage;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.spring.SpringUtils;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.MealWeComConfig;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.mapper.MealWeComConfigMapper;
import com.howe.meal.service.IMealWeComNotifyService;
import com.howe.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 点餐-企业微信通知发送服务实现
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>按 {@code dept_id} 读取家庭配置并检查总开关与事件开关；</li>
 *   <li>把订单 / AI 事件组装成纯 text 文案；</li>
 *   <li>按 {@code sys_user.phonenumber} 组装 {@code mentioned_mobile_list}；</li>
 *   <li>调用公共客户端发送，失败只记日志、不重试、不抛异常。</li>
 * </ul>
 *
 * <p>订单事件由调用方放在事务提交后的回调中执行；本类不访问登录态。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealWeComNotifyServiceImpl implements IMealWeComNotifyService {

    /** 评价内容展示上限（字符） */
    private static final int REVIEW_CONTENT_MAX_LENGTH = 100;

    /** 新版本更新说明展示上限（字符），避免企微消息过长 */
    private static final int RELEASE_LOG_MAX_LENGTH = 300;

    /** 强制更新标记 */
    private static final String FORCE_UPDATE_YES = "1";

    /** 订单号缺失时的占位 */
    private static final String UNKNOWN_ORDER_NO = "-";

    /** 业务线程池的 bean 名，由 module-framework 提供 */
    private static final String EXECUTOR_BEAN = "threadPoolTaskExecutor";

    private final MealWeComConfigMapper mealWeComConfigMapper;
    private final MealOrderMapper mealOrderMapper;
    private final ISysUserService sysUserService;
    private final WeComRobotClient weComRobotClient;

    /** 线程池，延迟解析：容器未就绪或模块单独使用时退化为同步执行 */
    private volatile Executor executor;

    @Override
    public void notifyNewOrder(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return;
        }
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
        StringBuilder content = new StringBuilder("🍽️ 有新订单啦！\n\n");
        content.append("👤 ").append(defaultText(order.getUserName())).append(" 想吃：\n");
        content.append(formatItemLines(items));
        content.append("\n\n📦 一共 ").append(totalCount(order, items)).append(" 份");
        content.append("\n🕒 ").append(friendlyTime(order.getCreateTime()));
        if (StringUtils.isNotEmpty(order.getOrderRemark())) {
            content.append("\n📝 整单备注：").append(order.getOrderRemark());
        }
        content.append("\n\n订单号：").append(orderNo(order));
        List<String> mobiles = mobilesOf(mealOrderMapper.selectChefUserIdsByDeptId(order.getDeptId()));
        sendOrderMessage(order.getDeptId(), true, content.toString(), mobiles, "新订单");
    }

    @Override
    public void notifyOrderAccepted(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return;
        }
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
        String content = "👨‍🍳 " + defaultText(order.getAcceptBy()) + " 接单啦！\n\n"
                + defaultText(order.getUserName()) + "，你点的这些已经在准备中，稍等一会儿就好～\n"
                + formatDishSummary(items) + "\n\n"
                + "🕒 " + friendlyTime(order.getAcceptTime()) + "\n"
                + "订单号：" + orderNo(order);
        sendOrderMessage(order.getDeptId(), true, content, mobilesOf(order.getUserId()), "接单");
    }

    @Override
    public void notifyOrderCompleted(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return;
        }
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
        String content = "🎉 开饭啦！\n\n"
                + defaultText(order.getUserName()) + "，你点的菜已经做好，趁热吃～\n"
                + formatDishSummary(items) + "\n"
                + "👨‍🍳 掌勺：" + defaultText(order.getFinishBy()) + "\n"
                + "🕒 " + friendlyTime(order.getFinishTime()) + "\n"
                + "订单号：" + orderNo(order);
        sendOrderMessage(order.getDeptId(), true, content, mobilesOf(order.getUserId()), "完成");
    }

    @Override
    public void notifyOrderUnaccepted(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return;
        }
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
        String reason = MealOrder.CLOSE_REASON_TIMEOUT.equals(order.getCloseReason())
                ? "等了很久都没人接单" : "大厨们都暂时腾不开手";
        String content = "😅 这单没接上\n\n"
                + defaultText(order.getUserName()) + "，" + reason + "，可以换个时间再点，或者直接喊一声～\n"
                + formatDishSummary(items) + "\n"
                + "🕒 " + friendlyTime(order.getCreateTime()) + "\n"
                + "订单号：" + orderNo(order);
        sendOrderMessage(order.getDeptId(), true, content, mobilesOf(order.getUserId()), "无人接单");
    }

    @Override
    public void notifyOrderRated(Long orderId, Integer score, String content) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return;
        }
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
        StringBuilder text = new StringBuilder("⭐ 收到新评价啦！\n\n");
        text.append(defaultText(order.getUserName())).append(" 给这顿饭打了 ")
                .append(scoreText(score)).append("\n");
        text.append(formatDishSummary(items)).append("\n");
        if (StringUtils.isNotEmpty(content)) {
            text.append("💬 “").append(truncateForDisplay(content)).append("”\n");
        }
        text.append("\n订单号：").append(orderNo(order));
        List<String> mobiles = order.getAcceptUserId() == null
                ? new ArrayList<>() : mobilesOf(order.getAcceptUserId());
        sendOrderMessage(order.getDeptId(), true, text.toString(), mobiles, "评价");
    }

    @Override
    public void notifyDishAiUpdate(Long deptId, Long dishId, String dishName, Long userId, boolean success,
            String detail, String model) {
        try {
            StringBuilder text = new StringBuilder();
            if (success) {
                text.append("✨ 「").append(defaultText(dishName)).append("」的资料更新好啦！\n\n");
                text.append("这次补充了：").append(defaultText(detail)).append("\n");
                text.append("有空可以看一眼，不合适随时手动改～");
            } else {
                text.append("😅 「").append(defaultText(dishName)).append("」这次没能自动整理成功\n\n");
                text.append("原因：").append(defaultText(detail)).append("\n");
                text.append("可以稍后再试一次，或者手动补充一下～");
            }
            sendAiMessage(deptId, text.toString(), mobilesOf(userId), "菜品 AI 更新");
        } catch (Exception e) {
            log.warn("组装菜品 AI 更新企业微信通知失败，dishId={}", dishId, e);
        }
    }

    @Override
    public void notifyProposalAiAudit(Long deptId, Long proposalId, String name, Long userId, String result,
            String reason, String model) {
        try {
            StringBuilder text = new StringBuilder();
            if ("已通过".equals(result)) {
                text.append("🎉 新菜「").append(defaultText(name)).append("」审核通过啦！\n\n");
                text.append("已经加进菜单，想吃的时候随时点～");
            } else if ("已驳回".equals(result)) {
                text.append("🙂 新菜「").append(defaultText(name)).append("」这次没有通过\n\n");
                text.append("原因：").append(defaultText(reason)).append("\n");
                text.append("可以改一改再提交～");
            } else {
                text.append("😅 新菜「").append(defaultText(name)).append("」的自动审核没有完成\n\n");
                text.append("原因：").append(defaultText(reason)).append("\n");
                text.append("稍后会有家人手动看看～");
            }
            sendAiMessage(deptId, text.toString(), mobilesOf(userId), "提案 AI 审核");
        } catch (Exception e) {
            log.warn("组装提案 AI 审核企业微信通知失败，proposalId={}", proposalId, e);
        }
    }

    /**
     * 发送 App 新版本发布通知
     *
     * <p>平台级事件，没有单一家庭归属：遍历所有启用且配置了 Webhook Key 的家庭群，
     * 每个群各发一条。只受家庭级总开关约束，不受订单 / AI 事件开关影响。
     * 每个家庭独立提交到线程池，单个群发送失败不影响其他群。</p>
     *
     * @param version 本次发布的版本记录
     */
    @Override
    public void notifyAppRelease(MealAppVersion version) {
        if (version == null) {
            return;
        }
        try {
            MealWeComConfig query = new MealWeComConfig();
            query.setEnabled(MealWeComConfig.ENABLED_YES);
            List<MealWeComConfig> configs = mealWeComConfigMapper.selectMealWeComConfigList(query);
            if (configs == null || configs.isEmpty()) {
                return;
            }
            String content = buildReleaseContent(version);
            for (MealWeComConfig config : configs) {
                if (config == null || StringUtils.isEmpty(config.getWebhookKey())) {
                    continue;
                }
                submitRelease(config.getWebhookKey(), content);
            }
        } catch (Exception e) {
            log.warn("组装 App 新版本企业微信通知失败，versionId={}", version.getVersionId(), e);
        }
    }

    /**
     * 组装新版本通知文案
     *
     * @param version 版本记录
     * @return 通知文案
     */
    private String buildReleaseContent(MealAppVersion version) {
        StringBuilder text = new StringBuilder("🎉 点餐 App 有新版本啦！\n\n");
        text.append("版本：v").append(defaultText(version.getVersionName()))
                .append(" (").append(version.getVersionCode() == null ? "-" : version.getVersionCode()).append(")\n");
        if (StringUtils.isNotEmpty(version.getUpdateLog())) {
            text.append("更新说明：\n").append(truncate(version.getUpdateLog().trim(), RELEASE_LOG_MAX_LENGTH)).append("\n");
        }
        if (FORCE_UPDATE_YES.equals(version.getForceUpdate())) {
            text.append("\n⚠️ 本次为强制更新，需要更新后才能继续使用。");
        } else {
            text.append("\n打开 App 即可收到更新提示。");
        }
        return text.toString();
    }

    /**
     * 提交单个家庭群的新版本通知任务
     *
     * @param webhookKey webhook key
     * @param content    通知文案
     */
    private void submitRelease(String webhookKey, String content) {
        Runnable task = () -> doSendRelease(webhookKey, content);
        Executor target = resolveExecutor();
        if (target == null) {
            task.run();
            return;
        }
        try {
            target.execute(task);
        } catch (Exception e) {
            log.warn("提交 App 新版本企业微信通知任务失败，改为同步发送", e);
            task.run();
        }
    }

    /**
     * 真正发送新版本通知
     *
     * @param webhookKey webhook key
     * @param content    通知文案
     */
    private void doSendRelease(String webhookKey, String content) {
        try {
            WeComRobotResponse response = weComRobotClient.sendText(webhookKey, content);
            if (!response.isSuccess()) {
                log.warn("App 新版本企业微信通知发送失败：errcode={}, errmsg={}",
                        response.getErrcode(), response.getErrmsg());
            }
        } catch (Exception e) {
            log.warn("App 新版本企业微信通知发送异常", e);
        }
    }

    /**
     * 发送订单类消息：检查家庭级开关与订单开关
     *
     * @param deptId  家庭ID
     * @param order   是否订单事件（true 时检查 notify_order）
     * @param content 消息内容
     * @param mobiles @ 的手机号
     * @param event   事件名称，用于日志
     */
    private void sendOrderMessage(Long deptId, boolean order, String content, List<String> mobiles, String event) {
        send(deptId, order, content, mobiles, event);
    }

    /**
     * 发送 AI 类消息：检查家庭级开关与 AI 开关
     *
     * @param deptId  家庭ID
     * @param content 消息内容
     * @param mobiles @ 的手机号
     * @param event   事件名称，用于日志
     */
    private void sendAiMessage(Long deptId, String content, List<String> mobiles, String event) {
        send(deptId, false, content, mobiles, event);
    }

    /**
     * 统一的发送入口：解析配置、检查开关、构造 text、异步调用客户端
     *
     * <p>订单事件在事务提交后的回调中调用本方法，AI 任务在线程池中调用；这里再提交到
     * 业务线程池，保证 HTTP 调用不占用请求线程，也不影响 AI 任务本身。</p>
     *
     * @param deptId  家庭ID
     * @param order   是否订单事件
     * @param content 消息内容
     * @param mobiles @ 的手机号
     * @param event   事件名称，用于日志
     */
    private void send(Long deptId, boolean order, String content, List<String> mobiles, String event) {
        if (deptId == null) {
            return;
        }
        List<String> mentionedMobiles = WeComTextMessage.normalizeMobiles(mobiles);
        Runnable task = () -> doSend(deptId, order, content, mentionedMobiles, event);
        Executor target = resolveExecutor();
        if (target == null) {
            task.run();
            return;
        }
        try {
            target.execute(task);
        } catch (Exception e) {
            log.warn("提交企业微信通知任务失败，改为同步发送：deptId={}, event={}", deptId, event, e);
            task.run();
        }
    }

    /**
     * 真正发送：解析配置、检查开关、调用客户端
     *
     * @param deptId           家庭ID
     * @param order            是否订单事件
     * @param content          消息内容
     * @param mentionedMobiles @ 的手机号
     * @param event            事件名称，用于日志
     */
    private void doSend(Long deptId, boolean order, String content, List<String> mentionedMobiles, String event) {
        try {
            MealWeComConfig config = mealWeComConfigMapper.selectByDeptId(deptId);
            if (config == null || !MealWeComConfig.ENABLED_YES.equals(config.getEnabled())) {
                return;
            }
            String switchValue = order ? config.getNotifyOrder() : config.getNotifyAi();
            if (!MealWeComConfig.ENABLED_YES.equals(switchValue)) {
                return;
            }
            if (StringUtils.isEmpty(config.getWebhookKey())) {
                return;
            }
            WeComRobotResponse response = weComRobotClient.sendText(config.getWebhookKey(), content,
                    null, mentionedMobiles);
            if (!response.isSuccess()) {
                log.warn("企业微信通知发送失败：deptId={}, event={}, errcode={}, errmsg={}",
                        deptId, event, response.getErrcode(), response.getErrmsg());
            }
        } catch (Exception e) {
            log.warn("企业微信通知发送异常：deptId={}, event={}", deptId, event, e);
        }
    }

    /**
     * 解析业务线程池；容器未就绪或没有该 bean 时返回 null
     *
     * @return 线程池，可能为 null
     */
    private Executor resolveExecutor() {
        Executor current = this.executor;
        if (current != null) {
            return current;
        }
        try {
            Object bean = SpringUtils.getBean(EXECUTOR_BEAN);
            if (bean instanceof Executor target) {
                this.executor = target;
                return target;
            }
        } catch (Exception e) {
            log.debug("未找到 {} 线程池，企业微信通知改为同步发送", EXECUTOR_BEAN);
        }
        return null;
    }

    /**
     * 按用户ID集合查询手机号，空值与非 11 位号码会被过滤
     *
     * @param userIds 用户ID集合
     * @return 手机号列表
     */
    private List<String> mobilesOf(List<Long> userIds) {
        List<String> mobiles = new ArrayList<>();
        if (userIds == null || userIds.isEmpty()) {
            return mobiles;
        }
        for (Long userId : userIds) {
            mobiles.addAll(mobilesOf(userId));
        }
        return WeComTextMessage.normalizeMobiles(mobiles);
    }

    /**
     * 按用户ID查询手机号
     *
     * @param userId 用户ID
     * @return 手机号列表，为 0 或 1 个元素
     */
    private List<String> mobilesOf(Long userId) {
        List<String> mobiles = new ArrayList<>();
        if (userId == null) {
            return mobiles;
        }
        try {
            SysUser user = sysUserService.selectUserById(userId);
            if (user != null && "0".equals(user.getStatus()) && StringUtils.isNotEmpty(user.getPhonenumber())) {
                String mobile = user.getPhonenumber().trim();
                if (mobile.length() == 11) {
                    mobiles.add(mobile);
                }
            }
        } catch (Exception e) {
            log.debug("查询企业微信通知接收人手机号失败，userId={}", userId, e);
        }
        return mobiles;
    }

    /**
     * 格式化订单明细为多行「菜名 ×份数 + 单项备注」
     *
     * @param items 明细
     * @return 每道菜一行；没有明细时返回「暂未记录菜品」
     */
    private String formatItemLines(List<MealOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "・暂未记录菜品";
        }
        List<String> lines = new ArrayList<>();
        for (MealOrderItem item : items) {
            int count = item.getCount() == null ? 1 : item.getCount();
            StringBuilder line = new StringBuilder("・")
                    .append(defaultText(item.getDishName())).append(" ×").append(count);
            if (StringUtils.isNotEmpty(item.getRemark())) {
                line.append("（").append(item.getRemark().trim()).append("）");
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    /**
     * 格式化菜品摘要为单行「A、B、C 共 N 份」
     *
     * @param items 明细
     * @return 摘要文案
     */
    private String formatDishSummary(List<MealOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "🍲 菜品：暂未记录";
        }
        List<String> names = new ArrayList<>();
        int total = 0;
        for (MealOrderItem item : items) {
            int count = item.getCount() == null ? 1 : item.getCount();
            names.add(defaultText(item.getDishName()) + " ×" + count);
            total += count;
        }
        return "🍲 " + String.join("、", names) + "，共 " + total + " 份";
    }

    /**
     * 订单总份数：优先取订单字段，缺失时按明细累加
     *
     * @param order 订单
     * @param items 明细
     * @return 总份数
     */
    private int totalCount(MealOrder order, List<MealOrderItem> items) {
        if (order.getTotalCount() != null) {
            return order.getTotalCount();
        }
        int total = 0;
        if (items != null) {
            for (MealOrderItem item : items) {
                total += item.getCount() == null ? 1 : item.getCount();
            }
        }
        return total;
    }

    /**
     * 把评分转成更直观的表达
     *
     * @param score 评分
     * @return 如「5 分 ⭐⭐⭐⭐⭐」
     */
    private String scoreText(Integer score) {
        if (score == null) {
            return "一个评分";
        }
        int stars = Math.max(0, Math.min(5, score));
        return score + " 分 " + "⭐".repeat(stars);
    }

    /**
     * 把时间转成家庭场景更好读的表达，并附上具体时间
     *
     * @param date 时间
     * @return 如「今天 18:32」；跨天时返回「昨天 18:32」「10月18日 18:32」
     */
    private String friendlyTime(java.util.Date date) {
        if (date == null) {
            return "刚刚";
        }
        java.time.LocalDateTime target = date.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate day = target.toLocalDate();
        String time = String.format("%02d:%02d", target.getHour(), target.getMinute());
        if (day.equals(today)) {
            return "今天 " + time;
        }
        if (day.equals(today.minusDays(1))) {
            return "昨天 " + time;
        }
        return target.getMonthValue() + "月" + target.getDayOfMonth() + "日 " + time;
    }

    /**
     * 订单号展示
     *
     * @param order 订单
     * @return 订单号
     */
    private String orderNo(MealOrder order) {
        return StringUtils.isNotEmpty(order.getOrderNo()) ? order.getOrderNo() : UNKNOWN_ORDER_NO;
    }

    /**
     * 文本默认值
     *
     * @param text 文本
     * @return 空值时返回 "-"
     */
    private String defaultText(String text) {
        return StringUtils.isNotEmpty(text) ? text : "-";
    }

    /**
     * 截断评价内容，避免消息过长
     *
     * @param content 评价内容
     * @return 截断后的内容
     */
    private String truncateForDisplay(String content) {
        return truncate(content, REVIEW_CONTENT_MAX_LENGTH);
    }

    /**
     * 按字符数截断文本，超出时追加省略号
     *
     * @param content 原始文本
     * @param max     最大字符数
     * @return 截断后的文本；空值返回 "-"
     */
    private String truncate(String content, int max) {
        if (StringUtils.isEmpty(content)) {
            return "-";
        }
        String trimmed = content.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
