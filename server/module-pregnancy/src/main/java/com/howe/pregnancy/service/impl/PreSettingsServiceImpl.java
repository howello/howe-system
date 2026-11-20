package com.howe.pregnancy.service.impl;

import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.vo.SettingsVO;
import com.howe.pregnancy.service.IPreSettingsService;
import com.howe.pregnancy.util.GestationUtils;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 共享设置服务实现
 *
 * <p>单家庭、单次孕期：数据读端不传业务归属 ID。未配置预产期时孕周字段为空，
 * 首页显示待设置，各项录入与查询仍可用。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PreSettingsServiceImpl implements IPreSettingsService {

    private final PregnancyAccessGuard accessGuard;

    private final SysUserMapper sysUserMapper;

    @Override
    public SettingsVO getSettings() {
        Set<String> roles = accessGuard.requireReadable();
        LocalDate start = PregnancyConfig.lastMenstrualPeriod();
        LocalDate dueDate = start == null ? null : start.plusDays(PregnancyConstants.GESTATION_DAYS_AT_DUE);
        LocalDate today = PregnancyTimeUtils.businessToday();

        SettingsVO vo = new SettingsVO();
        vo.setLastMenstrualPeriod(PregnancyTimeUtils.formatDate(start));
        vo.setDueDate(PregnancyTimeUtils.formatDate(dueDate));
        vo.setBusinessToday(PregnancyTimeUtils.formatDate(today));
        vo.setTimezone(PregnancyConstants.TIMEZONE);
        vo.setGestationalDay(GestationUtils.gestationalDay(dueDate, today));
        vo.setGestationalAge(GestationUtils.formatGestation(dueDate, today));
        vo.setDaysUntilDue(GestationUtils.daysUntilDue(today, dueDate));
        vo.setDaysUntil37Weeks(GestationUtils.daysUntil37Weeks(today, dueDate));
        vo.setCanWrite(accessGuard.canWrite(roles));
        vo.setCanManageReminders(accessGuard.canManageReminders(roles));
        List<String> roleList = new ArrayList<>(roles);
        roleList.sort(String::compareTo);
        vo.setRoles(roleList);

        // 顶部展示的账号昵称与头像来自原账号，孕期端不提供头像上传
        try {
            SysUser user = sysUserMapper.selectUserById(SecurityUtils.getUserId());
            if (user != null) {
                vo.setNickName(user.getNickName());
                vo.setAvatar(user.getAvatar());
            }
        } catch (RuntimeException ignored) {
            // 账号读取失败不影响设置主体
        }
        return vo;
    }
}
