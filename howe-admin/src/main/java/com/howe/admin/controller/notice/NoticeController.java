package com.howe.admin.controller.notice;

import com.github.pagehelper.PageInfo;
import com.howe.admin.service.notice.impl.NoticeService;
import com.howe.common.dto.notice.NoticeDTO;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * (t_notice)表控制层
 *
 * @author xxxxx
 */
@RestController
@RequestMapping("/notice")
@Api(tags = "通知公告")
@RequiredArgsConstructor
public class NoticeController {
    /**
     * 服务对象
     */
    private final NoticeService noticeService;


    @GetMapping("/getNoticeList")
    @ApiOperation(value = "获取通知列表", httpMethod = "GET")
    public AjaxResult<List<NoticeDTO>> getNoticeList(NoticeDTO noticeDTO) {
        List<NoticeDTO> list = noticeService.getNoticeList(noticeDTO);
        return AjaxResult.success(list);
    }

    @GetMapping("/getNoticePage")
    @ApiOperation(value = "分页获取通知列表", httpMethod = "GET")
    public AjaxResult<PageInfo<NoticeDTO>> getNoticePage(NoticeDTO noticeDTO) {
        PageInfo<NoticeDTO> list = noticeService.getNoticePage(noticeDTO);
        return AjaxResult.success(list);
    }

    @GetMapping("/getNotice")
    @ApiOperation(value = "根据id获取通知", httpMethod = "GET")
    public AjaxResult<NoticeDTO> getNotice(String noticeId) {
        NoticeDTO noticeDTO = noticeService.getById(noticeId);
        return AjaxResult.success(noticeDTO);
    }

    @PutMapping("/updateNotice")
    @ApiOperation(value = "更新通知", httpMethod = "PUT")
    public AjaxResult<Boolean> updateNotice(@RequestBody NoticeDTO noticeDTO) {
        Boolean b = noticeService.updateNotice(noticeDTO);
        return AjaxResult.success(b);
    }

    @PutMapping("/addNotice")
    @ApiOperation(value = "添加通知", httpMethod = "PUT")
    public AjaxResult<Boolean> addNotice(@RequestBody NoticeDTO noticeDTO) {
        Boolean b = noticeService.addNotice(noticeDTO);
        return AjaxResult.success(b);
    }

    @DeleteMapping("/delNotice")
    @ApiOperation(value = "删除通知", httpMethod = "DELETE")
    public AjaxResult<List<NoticeDTO>> delNotice(@RequestBody String noticeIds) {
        List<NoticeDTO> noticeDTOList = noticeService.delNotice(noticeIds);
        return AjaxResult.success(noticeDTOList);
    }
}
