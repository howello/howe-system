package com.howe.pregnancy.service.impl;

import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.domain.PregTodo;
import com.howe.pregnancy.domain.dto.TodoCreateBody;
import com.howe.pregnancy.domain.dto.TodoUpdateBody;
import com.howe.pregnancy.domain.vo.TodoVO;
import com.howe.pregnancy.mapper.PregTodoMapper;
import com.howe.pregnancy.service.IPregTodoService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 待办服务实现
 *
 * <p>待办允许未来日期，也允许提前勾选完成；完成人与完成时刻由服务端填充。
 * 待办不算实际记录，不参与指标统计和「当天已有记录」的判定。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PregTodoServiceImpl implements IPregTodoService {

    private final PregTodoMapper todoMapper;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public List<TodoVO> list(Boolean done, LocalDate from, LocalDate to) {
        accessGuard.requireReadable();
        return todoMapper.selectList(done, from, to).stream().map(this::toVO).toList();
    }

    @Override
    public List<TodoVO> listByDay(LocalDate date) {
        return todoMapper.selectByDay(date).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO create(TodoCreateBody body) {
        accessGuard.requireWritable();
        String requestId = body.getRequestId() == null ? null : body.getRequestId().trim();
        LocalDate dueDate = PregnancyTimeUtils.parseDate(body.getDueDate(), "待办日期");
        LocalTime dueTime = PregnancyTimeUtils.parseOptionalTime(body.getDueTime(), "待办时刻");
        boolean repeatDaily = Boolean.TRUE.equals(body.getRepeatDaily());
        requireReminderTime(repeatDaily, dueTime);
        // 保留单次待办的请求摘要，兼容旧客户端及尚未完成的请求重试。
        String hash = repeatDaily
                ? RequestHashUtils.hash(body.getTitle(), body.getDueDate(), body.getDueTime(), body.getNote(), true)
                : RequestHashUtils.hash(body.getTitle(), body.getDueDate(), body.getDueTime(), body.getNote());

        if (requestId != null && !requestId.isEmpty()) {
            PregTodo existing = todoMapper.selectByRequest(requestId);
            if (existing != null) {
                if (!hash.equals(existing.getRequestHash())) {
                    throw PregnancyException.idempotencyConflict(requestId);
                }
                if ("2".equals(existing.getDelFlag())) {
                    throw PregnancyException.requestConsumed(requestId);
                }
                return toVO(existing);
            }
        }

        PregTodo todo = new PregTodo();
        todo.setTitle(body.getTitle().trim());
        todo.setDueDate(dueDate);
        todo.setDueTime(dueTime);
        todo.setRepeatDaily(repeatDaily);
        todo.setNote(body.getNote());
        todo.setDone(Boolean.FALSE);
        todo.setRequestId(requestId == null || requestId.isEmpty() ? null : requestId);
        todo.setRequestHash(hash);
        todo.setCreateBy(SecurityUtils.getUsername());
        todoMapper.insert(todo);
        return toVO(todoMapper.selectById(todo.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO update(Long id, TodoUpdateBody body) {
        accessGuard.requireWritable();
        PregTodo existing = todoMapper.selectById(id);
        if (existing == null) {
            throw PregnancyException.notFound();
        }
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (body.getTitle() != null) {
            if (body.getTitle().isBlank()) {
                throw PregnancyException.invalid("待办标题不能为空");
            }
            existing.setTitle(body.getTitle().trim());
        }
        if (body.getDueDate() != null && !body.getDueDate().isBlank()) {
            existing.setDueDate(PregnancyTimeUtils.parseDate(body.getDueDate(), "待办日期"));
        }
        if (body.getDueTime() != null) {
            existing.setDueTime(body.getDueTime().isBlank() ? null
                    : PregnancyTimeUtils.parseOptionalTime(body.getDueTime(), "待办时刻"));
        }
        if (body.getRepeatDaily() != null) {
            existing.setRepeatDaily(body.getRepeatDaily());
        }
        requireReminderTime(Boolean.TRUE.equals(existing.getRepeatDaily()), existing.getDueTime());
        if (body.getNote() != null) {
            existing.setNote(body.getNote());
        }
        if (body.getDone() != null) {
            boolean wasDone = Boolean.TRUE.equals(existing.getDone());
            if (body.getDone() && !wasDone) {
                existing.setDone(Boolean.TRUE);
                existing.setDoneAt(com.howe.pregnancy.util.PregnancyTimeUtils.now());
                existing.setDoneBy(SecurityUtils.getUserId());
            } else if (!body.getDone() && wasDone) {
                existing.setDone(Boolean.FALSE);
                existing.setDoneAt(null);
                existing.setDoneBy(null);
            }
        }
        existing.setVersion(body.getVersion());
        existing.setUpdateBy(SecurityUtils.getUsername());
        if (todoMapper.update(existing) == 0) {
            throw PregnancyException.versionConflict();
        }
        return toVO(todoMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        accessGuard.requireWritable();
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (todoMapper.logicalDelete(id, version, SecurityUtils.getUsername()) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    /** 开启每日提醒时必须有发送时刻。 */
    private void requireReminderTime(boolean repeatDaily, LocalTime dueTime) {
        if (repeatDaily && dueTime == null) {
            throw PregnancyException.invalid("开启每日提醒时请选择时间");
        }
    }

    /**
     * 转换展示对象
     *
     * @param todo 待办
     * @return 展示对象
     */
    private TodoVO toVO(PregTodo todo) {
        TodoVO vo = new TodoVO();
        vo.setId(String.valueOf(todo.getId()));
        vo.setTitle(todo.getTitle());
        vo.setDueDate(PregnancyTimeUtils.formatDate(todo.getDueDate()));
        vo.setDueTime(todo.getDueTime() == null ? null : todo.getDueTime().toString());
        vo.setRepeatDaily(todo.getRepeatDaily());
        vo.setNote(todo.getNote());
        vo.setDone(todo.getDone());
        vo.setDoneAt(PregnancyTimeUtils.formatOffset(todo.getDoneAt() == null ? null : todo.getDoneAt().toInstant()));
        vo.setDoneBy(todo.getDoneBy() == null ? null : String.valueOf(todo.getDoneBy()));
        vo.setVersion(todo.getVersion());
        return vo;
    }
}
