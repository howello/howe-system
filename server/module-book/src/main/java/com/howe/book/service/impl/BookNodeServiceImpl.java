package com.howe.book.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.book.domain.BookNode;
import com.howe.book.domain.BookScopeDelta;
import com.howe.book.mapper.BookNodeMapper;
import com.howe.book.service.BookNodeService;
import com.howe.common.exception.ServiceException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 共享书架节点的层级与版本校验。 */
@Service
@RequiredArgsConstructor
public class BookNodeServiceImpl implements BookNodeService {
    private final BookNodeMapper mapper;
    private final ObjectMapper json;

    /** 查找有效节点，可限定父节点或按标题筛选。 */
    @Override
    public List<BookNode> list(Long creator, String parent, boolean children, String type, String title) {
        return list(creator, parent, children, type, title, false);
    }

    /** 查找有效节点，可选是否带正文。 */
    @Override
    public List<BookNode> list(Long creator, String parent, boolean children, String type, String title, boolean withContent) {
        var query = new LambdaQueryWrapper<BookNode>()
            .eq(creator != null, BookNode::getUserId, creator)
            .isNull(BookNode::getDeletedAt)
            .eq(type != null && !type.isBlank(), BookNode::getNodeType, type)
            .like(title != null && !title.isBlank(), BookNode::getTitle, title);
        if (withContent) {
            // 列表页要展示摘要与封面配色，正文随摘要一起返回可省一轮详情请求
            query.select(BookNode::getId, BookNode::getUserId, BookNode::getParentId,
                BookNode::getNodeType, BookNode::getTitle, BookNode::getSortOrder,
                BookNode::getCoverKind, BookNode::getCoverRef, BookNode::getContentJson,
                BookNode::getVersion, BookNode::getCreatedAt, BookNode::getUpdatedAt);
        } else {
            query.select(BookNode::getId, BookNode::getUserId, BookNode::getParentId,
                BookNode::getNodeType, BookNode::getTitle, BookNode::getSortOrder,
                BookNode::getCoverKind, BookNode::getCoverRef, BookNode::getVersion, BookNode::getUpdatedAt);
        }
        if (children) {
            if (parent == null || parent.isBlank()) {
                query.isNull(BookNode::getParentId);
            } else {
                query.eq(BookNode::getParentId, parent);
            }
        }
        return mapper.selectList(query.orderByAsc(BookNode::getSortOrder, BookNode::getId));
    }

    /** 比对指定父节点下的直接子节点。 */
    @Override
    @Transactional(readOnly = true)
    public BookScopeDelta syncScope(String parent, Map<String, String> known, String parentVersion) {
        String scope = parent == null || parent.isBlank() ? null : uuid(parent);
        Map<String, String> baseline = known == null ? Map.of() : known;
        BookNode current = scope == null ? null : get(scope);
        List<BookNode> rows = list(null, scope, true, null, null, true);
        if (current != null) {
            current.setChildCount(rows.size());
            current.setCacheVersion(SecureUtil.sha256(json.valueToTree(current).toString()));
            if (current.getCacheVersion().equals(parentVersion)) {
                current = null;
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        if (!rows.isEmpty()) {
            mapper.selectMaps(new QueryWrapper<BookNode>().select("parent_id", "COUNT(*) AS child_count")
                .in("parent_id", rows.stream().map(BookNode::getId).toList())
                .isNull("deleted_at").groupBy("parent_id"))
                .forEach(row -> counts.put((String) row.get("parent_id"), ((Number) row.get("child_count")).intValue()));
        }
        List<String> order = new ArrayList<>();
        List<BookNode> upserts = new ArrayList<>();
        Set<String> live = new HashSet<>();
        for (BookNode node : rows) {
            node.setChildCount(counts.getOrDefault(node.getId(), 0));
            String version = SecureUtil.sha256(json.valueToTree(node).toString());
            node.setCacheVersion(version);
            order.add(node.getId());
            live.add(node.getId());
            if (!version.equals(baseline.get(node.getId()))) {
                upserts.add(node);
            }
        }
        return new BookScopeDelta(upserts, baseline.keySet().stream().filter(id -> !live.contains(id)).toList(), order, current);
    }

    /** 沿父链读取当前节点的路径，不读取兄弟及后代。 */
    @Override
    public List<BookNode> path(String id) {
        List<BookNode> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BookNode node = get(id);
        while (seen.add(node.getId())) {
            chain.add(node);
            if (node.getParentId() == null) {
                break;
            }
            node = get(node.getParentId());
        }
        Collections.reverse(chain);
        return chain;
    }

    /** 聚合当前共享书架的统计。 */
    @Override
    public Map<String, Long> stats() {
        return Map.of("categories", countType("category", null), "books", countType("book", null),
            "notes", countType("note", null), "weekNotes", countType("note", LocalDateTime.now().minusDays(7)));
    }

    /** 统计指定类型的有效节点。 */
    private long countType(String type, LocalDateTime since) {
        return mapper.selectCount(new LambdaQueryWrapper<BookNode>().eq(BookNode::getNodeType, type)
            .isNull(BookNode::getDeletedAt).ge(since != null, BookNode::getCreatedAt, since));
    }

    /** 读取共享书架的有效节点。 */
    @Override
    public BookNode get(String id) {
        BookNode node = mapper.selectById(id);
        if (node == null || node.getDeletedAt() != null) {
            throw new ServiceException("节点不存在", 404);
        }
        return node;
    }

    /** 新增共享节点并记录创建人。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public BookNode create(long creator, BookNode input) {
        input.setUserId(creator);
        input.setId(input.getId() == null ? UUID.randomUUID().toString() : uuid(input.getId()));
        validate(input);
        BookNode existing = mapper.selectById(input.getId());
        if (existing != null) {
            if (existing.getDeletedAt() == null && existing.getUserId().equals(creator)
                && existing.getNodeType().equals(input.getNodeType())
                && java.util.Objects.equals(existing.getParentId(), input.getParentId())
                && existing.getTitle().equals(input.getTitle())
                && java.util.Objects.equals(existing.getCoverKind(), input.getCoverKind())
                && java.util.Objects.equals(existing.getCoverRef(), input.getCoverRef())
                && sameContent(existing.getContentJson(), input.getContentJson())) {
                return existing;
            }
            throw new ServiceException("节点 ID 已使用，请检查保存结果", 409);
        }
        input.setDeletedAt(null);
        input.setVersion(1L);
        input.setCreatedAt(LocalDateTime.now());
        input.setUpdatedAt(input.getCreatedAt());
        mapper.insert(input);
        return input;
    }

    /** 更新节点或移动子树，按已有节点版本进行比较。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public BookNode update(String id, BookNode input) {
        BookNode current = get(id);
        if (!current.getVersion().equals(input.getVersion())) {
            throw new ServiceException("节点已更新，请重新加载后保存", 409);
        }
        input.setId(id);
        input.setUserId(current.getUserId());
        input.setNodeType(current.getNodeType());
        validate(input);
        int changed = mapper.update(null, new LambdaUpdateWrapper<BookNode>()
            .eq(BookNode::getId, id).eq(BookNode::getVersion, input.getVersion())
            .isNull(BookNode::getDeletedAt)
            .set(BookNode::getParentId, input.getParentId()).set(BookNode::getTitle, input.getTitle())
            .set(BookNode::getSortOrder, input.getSortOrder()).set(BookNode::getCoverKind, input.getCoverKind())
            .set(BookNode::getCoverRef, input.getCoverRef()).set(BookNode::getContentJson, input.getContentJson())
            .set(BookNode::getVersion, input.getVersion() + 1).set(BookNode::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new ServiceException("节点已更新，请重新加载后保存", 409);
        }
        return get(id);
    }

    /** 按批遍历并软删除整棵子树。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public List<String> remove(String id) {
        get(id);
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        pending.add(id);
        var now = LocalDateTime.now();
        while (!pending.isEmpty()) {
            var batch = new java.util.ArrayList<String>();
            while (!pending.isEmpty() && batch.size() < 200) {
                String next = pending.remove();
                if (visited.add(next)) {
                    batch.add(next);
                }
            }
            if (batch.isEmpty()) {
                continue;
            }
            mapper.selectList(new LambdaQueryWrapper<BookNode>().select(BookNode::getId)
                .in(BookNode::getParentId, batch)
                .isNull(BookNode::getDeletedAt)).forEach(node -> pending.add(node.getId()));
            mapper.update(null, new LambdaUpdateWrapper<BookNode>().in(BookNode::getId, batch)
                .isNull(BookNode::getDeletedAt).set(BookNode::getDeletedAt, now)
                .set(BookNode::getUpdatedAt, now).setSql("version = version + 1"));
        }
        return List.copyOf(visited);
    }

    /** 规范客户端 UUID。 */
    private String uuid(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("节点 ID 无效");
        }
    }

    /** 比较数据库格式化前后的正文。 */
    private boolean sameContent(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        try {
            return json.readTree(left).equals(json.readTree(right));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return false;
        }
    }

    /** 验证基本字段、父节点和循环引用。 */
    private void validate(BookNode input) {
        String type = input.getNodeType();
        if (type == null || !Set.of("category", "book", "note", "remark").contains(type)
            || input.getTitle() == null || input.getTitle().isBlank() || input.getTitle().length() > 200) {
            throw new ServiceException("节点类型或标题无效");
        }
        input.setTitle(input.getTitle().strip());
        if (input.getSortOrder() == null) {
            input.setSortOrder(0);
        }
        if (input.getContentJson() != null) {
            try {
                if (input.getContentJson().length() > 262144 || !json.readTree(input.getContentJson().strip().isEmpty() ? "null" : input.getContentJson()).isObject()) {
                    throw new ServiceException("正文必须是有效 JSON 对象");
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new ServiceException("正文必须是有效 JSON 对象");
            }
        }
        if (input.getParentId() != null && input.getParentId().isBlank()) {
            input.setParentId(null);
        }
        if ("category".equals(type) && input.getParentId() == null) {
            return;
        }
        if (input.getParentId() == null) {
            throw new ServiceException("请选择合法的父节点");
        }
        BookNode parent = get(input.getParentId());
        boolean valid = switch (type) {
            case "category", "book" -> "category".equals(parent.getNodeType());
            case "note" -> "book".equals(parent.getNodeType());
            default -> Set.of("note", "remark").contains(parent.getNodeType());
        };
        if (!valid) {
            throw new ServiceException("父节点类型不匹配");
        }
        var seen = new HashSet<String>();
        seen.add(input.getId());
        while (parent != null) {
            if (!seen.add(parent.getId())) {
                throw new ServiceException("不能移动到当前节点或其后代");
            }
            parent = parent.getParentId() == null ? null : get(parent.getParentId());
        }
    }
}
