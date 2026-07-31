package com.howe.web.controller.system;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.core.text.Convert;
import com.howe.common.enums.BusinessType;
import com.howe.system.domain.SysNotice;
import com.howe.system.service.ISysNoticeReadService;
import com.howe.system.service.ISysNoticeService;

/**
 * 公告 信息操作处理
 * 
 * @author howe
 */
@Tag(name = "通知公告", description = "通知公告的维护，以及顶部铃铛的已读标记与已读明细")
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController
{
    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private ISysNoticeReadService noticeReadService;

    /**
     * 获取通知公告列表
     */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @Operation(summary = "查询通知公告列表", description = "分页查询通知公告")
    @GetMapping("/list")
    public TableDataInfo list(SysNotice notice)
    {
        startPage();
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        return getDataTable(list);
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @Operation(summary = "获取通知公告详细信息", description = "根据公告编号查询公告详情")
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@Parameter(description = "公告编号") @PathVariable Long noticeId)
    {
        return success(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:add')")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @Operation(summary = "新增通知公告", description = "公告内容为富文本 HTML")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysNotice notice)
    {
        notice.setCreateBy(getUsername());
        return toAjax(noticeService.insertNotice(notice));
    }

    /**
     * 修改通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:edit')")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改通知公告", description = "修改公告标题、类型、状态与内容")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysNotice notice)
    {
        notice.setUpdateBy(getUsername());
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 首页顶部公告列表（返回全部正常公告，带当前用户已读标记，最多5条）
     */
    @Operation(summary = "首页顶部公告列表", description = "仅需登录即可调用。返回最多 5 条正常公告并带当前用户已读标记与未读数")
    @GetMapping("/listTop")
    @ResponseBody
    public AjaxResult listTop()
    {
        Long userId = getUserId();
        List<SysNotice> list = noticeReadService.selectNoticeListWithReadStatus(userId, 5);
        long unreadCount = list.stream().filter(n -> !n.getIsRead()).count();
        AjaxResult result = AjaxResult.success(list);
        result.put("unreadCount", unreadCount);
        return result;
    }

    /**
     * 标记公告已读
     */
    @Operation(summary = "标记公告已读", description = "仅需登录即可调用。为当前用户标记单条公告已读")
    @PostMapping("/markRead")
    @ResponseBody
    public AjaxResult markRead(@Parameter(description = "公告编号") Long noticeId)
    {
        Long userId = getUserId();
        noticeReadService.markRead(noticeId, userId);
        return success();
    }

    /**
     * 批量标记已读
     */
    @Operation(summary = "批量标记公告已读", description = "仅需登录即可调用。ids 为逗号分隔的公告编号")
    @PostMapping("/markReadAll")
    @ResponseBody
    public AjaxResult markReadAll(@Parameter(description = "逗号分隔的公告编号") String ids)
    {
        Long userId = getUserId();
        Long[] noticeIds = Convert.toLongArray(ids);
        noticeReadService.markReadBatch(userId, noticeIds);
        return success();
    }

    /**
     * 已读用户列表数据
     */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @Operation(summary = "查询公告已读用户列表", description = "分页查询指定公告的已读用户，支持按关键字检索")
    @GetMapping("/readUsers/list")
    @ResponseBody
    public TableDataInfo readUsersList(@Parameter(description = "公告编号") Long noticeId, @Parameter(description = "检索关键字") String searchValue)
    {
        startPage();
        List<?> list = noticeReadService.selectReadUsersByNoticeId(noticeId, searchValue);
        return getDataTable(list);
    }

    /**
     * 删除通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:remove')")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @Operation(summary = "删除通知公告", description = "同时清理这些公告的已读记录")
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@Parameter(description = "公告编号数组") @PathVariable Long[] noticeIds)
    {
        noticeReadService.deleteByNoticeIds(noticeIds);
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
