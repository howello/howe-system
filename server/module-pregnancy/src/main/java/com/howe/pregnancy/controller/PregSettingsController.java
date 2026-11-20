package com.howe.pregnancy.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.pregnancy.domain.vo.SettingsVO;
import com.howe.pregnancy.service.IPreSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 孕期共享设置 控制层
 *
 * <p>单家庭、单次孕期：路径中不含业务归属 ID，预产期与业务日期统一从共享设置读取。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-共享设置", description = "预产期、业务日期、孕周与当前账号能力")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregSettingsController extends BaseController {

    private final IPreSettingsService settingsService;

    /**
     * 读取共享设置
     *
     * @return 共享设置
     */
    @Operation(summary = "读取共享设置", description = "返回预产期、业务今天、孕周、倒计时与角色能力")
    @GetMapping("/settings")
    public AjaxResult settings() {
        SettingsVO vo = settingsService.getSettings();
        return success(vo);
    }
}
