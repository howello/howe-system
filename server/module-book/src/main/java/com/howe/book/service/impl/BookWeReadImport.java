package com.howe.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.howe.book.domain.BookNode;
import com.howe.book.mapper.BookNodeMapper;
import com.howe.book.service.BookNodeService;
import com.howe.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** 每次同步建立一个本地索引，复用既有节点，只插入真正新增的数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookWeReadImport {
    private final BookNodeMapper mapper;
    private final BookNodeService nodes;
    private final ObjectMapper json;

    /** 为一次同步准备本地索引。 */
    Batch open(long owner) {
        return new Batch(owner);
    }

    /** 保存一次同步过程中新增节点的数量。 */
    record Counts(int categories, int books, int notes) {}

    /** 从上游整理后的单条感悟。 */
    record Note(String kind, String id, String legacyId, String chapter, String excerpt, String text,
        JsonNode source) {}

    /** 保存本次任务用到的索引，包含本地软删除记录。 */
    final class Batch {
        private final long owner;
        private final Map<String, BookNode> byId = new HashMap<>();
        private final Map<String, BookNode> origins = new HashMap<>();
        private final Map<String, String> aliases = new HashMap<>();
        private final Map<Placement, BookNode> categories = new HashMap<>();
        private final Map<Siblings, Integer> sortPositions = new HashMap<>();
        private final Map<String, List<LocalBook>> byIsbn = new HashMap<>();
        private final Map<Work, List<LocalBook>> byWork = new HashMap<>();
        private int categoryCount;
        private int bookCount;
        private int noteCount;

        /** 建立分类、书目和历史导入来源的索引。 */
        private Batch(long owner) {
            this.owner = owner;
            for (var binding : mapper.selectWeReadBindings()) {
                parse(binding.sourceKeys()).fieldNames().forEachRemaining(key -> aliases.put(key, binding.nodeId()));
            }
            for (BookNode node : mapper.selectList(new LambdaQueryWrapper<BookNode>()
                .in(BookNode::getNodeType, "category", "book").orderByAsc(BookNode::getId))) {
                byId.put(node.getId(), node);
                indexSortPosition(node);
                JsonNode content = parse(node.getContentJson());
                JsonNode source = content.path("weread");
                if ("category".equals(node.getNodeType())) {
                    categories.merge(new Placement(node.getParentId(), normalized(node.getTitle())), node,
                        (left, right) -> left.getDeletedAt() == null ? left : right);
                    if ("archive".equals(source.path("source").asText())) {
                        index(sourceKey("archive", source.path("sourceName").asText(node.getTitle())), node);
                    }
                } else {
                    indexBook(node, content, source);
                }
            }
            for (BookNode node : mapper.selectWeReadNoteOrigins()) {
                JsonNode source = parse(node.getContentJson());
                byId.put(node.getId(), node);
                index(sourceKey("note", source.path("bookId").asText(), source.path("kind").asText(),
                    source.path("sourceId").asText()), node);
            }
        }

        /** 返回目前已写入的数量。 */
        Counts counts() {
            return new Counts(categoryCount, bookCount, noteCount);
        }

        /** 保留或创建对应微信读书书单的分类。 */
        BookNode archive(String name) {
            return category(sourceKey("archive", name), null, name, "archive");
        }

        /** 查找或创建书籍所属的详情分类，可以逐层扩展。 */
        BookNode catalog(JsonNode info, boolean album) {
            String rootName = album ? "有声书" : "其他书籍";
            BookNode parent = category(sourceKey("catalog", rootName), null, rootName, "catalog");
            if (!album) {
                for (String segment : BookWeReadMetadata.categories(info)) {
                    if (parent.getDeletedAt() != null) {
                        break;
                    }
                    parent = category(sourceKey("catalog", parent.getId(), segment), parent.getId(), segment, "catalog");
                }
            }
            return parent;
        }

        /** 查找来源完全相同或可明确匹配的本地书籍，包含已删除书籍。 */
        BookNode existingBook(String bookId, JsonNode info, boolean album) {
            String key = sourceKey(album ? "album" : "book", bookId);
            BookNode exact = find(key);
            if (exact != null) {
                return exact;
            }
            String isbn = isbn(info.path("isbn").asText());
            List<LocalBook> matching = album ? List.of() : byIsbn.getOrDefault(isbn, List.of());
            if (matching.isEmpty()) {
                Work work = work(info.path(album ? "name" : "title").asText(),
                    info.path(album ? "authorName" : "author").asText(), album);
                if (work != null) {
                    matching = byWork.getOrDefault(work, List.of()).stream()
                        .filter(book -> isbn.isEmpty() || book.isbn().isEmpty() || isbn.equals(book.isbn())).toList();
                }
            }
            if (matching.size() == 1 && matching.get(0).origin().isEmpty()) {
                BookNode node = matching.get(0).node();
                bind(key, node);
                return node;
            }
            return null;
        }

        /** 按来源查找电子书，不读取或建立模糊关联。 */
        BookNode sourcedBook(String bookId) {
            return find(sourceKey("book", bookId));
        }

        /** 来源曾提供明确公开标志时，离架后仍能继续导入其笔记。 */
        boolean knownPublic(BookNode node) {
            if (node == null) {
                return false;
            }
            JsonNode secret = parse(node.getContentJson()).path("weread").path("secret");
            return secret.isBoolean() ? !secret.asBoolean() : secret.asInt(-1) == 0;
        }

        /** 把新书或者有声书加入指定分类。 */
        BookNode addBook(String bookId, JsonNode info, boolean album, BookNode parent, List<String> archives,
            boolean onShelf, JsonNode notebook) {
            if (parent.getDeletedAt() != null) {
                return null;
            }
            String kind = album ? "album" : "book";
            ObjectNode content = json.createObjectNode();
            content.put("author", info.path(album ? "authorName" : "author").asText());
            content.put("translator", info.path("translator").asText());
            content.put("publisher", info.path("publisher").asText());
            content.put("year", BookWeReadMetadata.year(info));
            content.put("isbn", info.path("isbn").asText());
            content.put("intro", clip(info.path("intro").asText(), 30000));
            content.put("rating", BookWeReadMetadata.rating(info.path("newRating")));
            var tags = content.putArray("tags");
            BookWeReadMetadata.categories(info).forEach(tags::add);
            int progress = Math.max(0, Math.min(100, notebook.path("readingProgress").asInt()));
            boolean finished = info.path("finishReading").asInt() == 1 || notebook.path("markedStatus").asInt() == 1;
            content.put("status", finished ? "已读完" : progress > 0 ? "在读" : "想读");
            content.put("progress", finished ? 100 : progress);
            var source = content.putObject("weread").put("kind", kind)
                .put(album ? "albumId" : "bookId", bookId).put("onShelf", onShelf);
            archives.forEach(source.putArray("archive")::add);
            for (String field : List.of("category", "readerLink", "deepLink", "wordCount", "secret", "trackCount", "finishStatus")) {
                if (info.hasNonNull(field)) {
                    source.set(field, info.get(field));
                }
            }
            String key = sourceKey(kind, bookId);
            BookNode node = insert(key, parent.getId(), "book", info.path(album ? "name" : "title").asText(),
                content, info.path("cover").asText());
            if (node.getDeletedAt() == null && !origins.containsKey(key)) {
                indexBook(node, content, source);
            }
            return node;
        }

        /** 批量导入一本书的感悟，按来源及同书中完全相同的本地笔记去重。 */
        void addNotes(String bookId, BookNode book, List<Note> incoming) {
            if (incoming.stream().allMatch(note -> find(sourceKey("note", bookId, note.kind(), note.id())) != null)) {
                return;
            }
            var local = new HashMap<Passage, List<BookNode>>();
            for (BookNode node : mapper.selectList(new LambdaQueryWrapper<BookNode>()
                .eq(BookNode::getNodeType, "note").eq(BookNode::getParentId, book.getId()))) {
                indexSortPosition(node);
                JsonNode content = parse(node.getContentJson());
                if (content.path("weread").hasNonNull("sourceId")) {
                    continue;
                }
                var excerpts = new ArrayList<String>();
                var body = new ArrayList<String>();
                for (JsonNode block : content.path("blocks")) {
                    if ("quote".equals(block.path("type").asText())) {
                        excerpts.add(block.path("text").asText());
                    } else if ("text".equals(block.path("type").asText())) {
                        body.add(block.path("text").asText());
                    }
                }
                String text = body.isEmpty() && excerpts.isEmpty() ? content.path("text").asText() : String.join("\n", body);
                String excerpt = String.join("\n", excerpts);
                String chapter = content.path("chapter").asText();
                if (!text.isBlank() || !excerpt.isBlank()) {
                    local.computeIfAbsent(passage(chapter, excerpt, text), ignored -> new ArrayList<>()).add(node);
                    if (!text.isBlank() && excerpt.isBlank()) {
                        local.computeIfAbsent(passage(chapter, text, ""), ignored -> new ArrayList<>()).add(node);
                    }
                }
            }
            for (Note note : incoming) {
                addNote(bookId, book.getId(), note, local);
            }
        }

        /** 查找已有来源，按需兼容旧版划线标识和手工录入的同一感悟。 */
        private void addNote(String bookId, String parentId, Note note, Map<Passage, List<BookNode>> local) {
            String key = sourceKey("note", bookId, note.kind(), note.id());
            if (find(key) != null) {
                return;
            }
            if (!note.legacyId().isBlank() && !note.legacyId().equals(note.id())) {
                BookNode legacy = find(sourceKey("note", bookId, note.kind(), note.legacyId()));
                if (legacy != null) {
                    bind(key, legacy);
                    return;
                }
            }
            List<BookNode> same = local.getOrDefault(passage(note.chapter(), note.excerpt(), note.text()), List.of());
            if (same.size() == 1) {
                bind(key, same.get(0));
                return;
            }
            String body = note.text().isBlank() ? note.excerpt() : note.text();
            var content = json.createObjectNode().put("chapter", note.chapter()).put("text", clip(body, 30000))
                .put("summary", clip(body, 500));
            var blocks = content.putArray("blocks");
            if (!note.excerpt().isBlank()) {
                blocks.addObject().put("id", key + "-quote").put("type", "quote").put("text", clip(note.excerpt(), 30000));
            }
            if (!note.text().isBlank()) {
                blocks.addObject().put("id", key + "-text").put("type", "text").put("text", clip(note.text(), 30000));
            }
            var source = content.putObject("weread").put("kind", note.kind()).put("sourceId", note.id()).put("bookId", bookId);
            for (String field : List.of("chapterUid", "range", "createTime", "colorStyle", "isPrivate", "notVisibleToFriends", "star")) {
                if (note.source().hasNonNull(field)) {
                    source.set(field, note.source().get(field));
                }
            }
            insert(key, parentId, "note", clip(body.replaceAll("\\s+", " "), 80), content, "");
        }

        /** 优先匹配来源，其次匹配同一父级下的现有分类。 */
        private BookNode category(String key, String parent, String name, String source) {
            BookNode known = find(key);
            if (known != null) {
                return known;
            }
            var placement = new Placement(parent, normalized(clip(name, 200)));
            BookNode existing = categories.get(placement);
            if (existing != null) {
                bind(key, existing);
                return existing;
            }
            var content = json.createObjectNode().put("color", "navy").put("art", "plain");
            content.putObject("weread").put("kind", "category").put("source", source).put("sourceName", name);
            BookNode node = insert(key, parent, "category", name, content, "");
            categories.put(placement, node);
            return node;
        }

        /** 使用确定性 UUID 让重试安全，并继续通过现有节点服务校验父级。 */
        private BookNode insert(String key, String parent, String type, String title, ObjectNode content, String cover) {
            var node = new BookNode();
            node.setId(key);
            node.setParentId(parent);
            node.setNodeType(type);
            node.setTitle(clip(title.strip(), 200));
            node.setContentJson(content.toString());
            var siblings = new Siblings(parent, type);
            int last = sortPositions.getOrDefault(siblings, 0);
            node.setSortOrder(last == Integer.MAX_VALUE ? last : last + 1);
            if (cover.startsWith("https://") && cover.length() <= 512) {
                node.setCoverKind("external");
                node.setCoverRef(cover);
            }
            try {
                node = nodes.createImported(owner, node, createdAt(content.path("weread").path("createTime")));
                switch (type) {
                    case "category" -> categoryCount++;
                    case "book" -> bookCount++;
                    case "note" -> noteCount++;
                    default -> throw new IllegalArgumentException("微信读书节点类型无效");
                }
            } catch (DuplicateKeyException exception) {
                node = mapper.selectById(key);
                if (node == null) {
                    throw exception;
                }
            } catch (ServiceException exception) {
                if (!Integer.valueOf(409).equals(exception.getCode()) || (node = mapper.selectById(key)) == null) {
                    throw exception;
                }
            }
            byId.put(node.getId(), node);
            indexSortPosition(node);
            return node;
        }

        /** 新节点追加在同级已有节点后面。 */
        private void indexSortPosition(BookNode node) {
            if (node.getDeletedAt() == null && node.getSortOrder() != null) {
                sortPositions.merge(new Siblings(node.getParentId(), node.getNodeType()), node.getSortOrder(), Math::max);
            }
        }

        /** 导入记录优先走持久关联、历史来源和确定性主键。 */
        private BookNode find(String key) {
            String alias = aliases.get(key);
            if (alias != null) {
                BookNode linked = byId.computeIfAbsent(alias, mapper::selectById);
                if (linked != null) {
                    return linked;
                }
            }
            return origins.getOrDefault(key, byId.get(key));
        }

        /** 关联已有节点，不修改用户写入的书名、分类、正文或版本。 */
        private void bind(String key, BookNode node) {
            mapper.bindWeReadSource(node.getId(), key);
            aliases.put(key, node.getId());
            byId.put(node.getId(), node);
        }

        /** 对历史来源建立首次匹配索引。 */
        private void index(String key, BookNode node) {
            origins.putIfAbsent(key, node);
        }

        /** 记录一本书的来源、ISBN 和书名作者组合。 */
        private void indexBook(BookNode node, JsonNode content, JsonNode source) {
            String albumId = source.path("albumId").asText();
            String bookId = source.path("bookId").asText();
            String origin = albumId.isEmpty() ? bookId : albumId;
            boolean album = !albumId.isEmpty();
            if (!origin.isEmpty()) {
                index(sourceKey(album ? "album" : "book", origin), node);
            }
            String isbn = isbn(content.path("isbn").asText());
            var record = new LocalBook(node, origin, isbn);
            if (!isbn.isEmpty()) {
                byIsbn.computeIfAbsent(isbn, ignored -> new ArrayList<>()).add(record);
            }
            Work work = work(node.getTitle(), content.path("author").asText(), album);
            if (work != null) {
                byWork.computeIfAbsent(work, ignored -> new ArrayList<>()).add(record);
            }
        }
    }

    /** 分类在本地树中的位置。 */
    private record Placement(String parentId, String name) {}

    /** 同一个父节点下独立排序的一组节点。 */
    private record Siblings(String parentId, String type) {}

    /** 书名作者索引键。 */
    private record Work(String title, String author, boolean album) {}

    /** 可用来匹配手工书目的本地记录。 */
    private record LocalBook(BookNode node, String origin, String isbn) {}

    /** 同一本书中本地感悟的可比较内容。 */
    private record Passage(String chapter, String excerpt, String text) {}

    /** 用不包含用户凭据的业务标识生成确定性主键。 */
    private static String sourceKey(String... parts) {
        return UUID.nameUUIDFromBytes(("book:weread:v1\0" + String.join("\0", parts))
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 将有效的原始笔记时间转换为数据库使用的本地时间。 */
    private static LocalDateTime createdAt(JsonNode value) {
        long seconds = value.asLong();
        return seconds > 0 && seconds <= Instant.now().getEpochSecond()
            ? Instant.ofEpochSecond(seconds).atZone(BookWeReadMetadata.TIME_ZONE).toLocalDateTime() : null;
    }

    /** 解析节点 JSON，忽略空正文。 */
    private JsonNode parse(String value) {
        if (value == null) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ServiceException("读书节点的正文格式无效");
        }
    }

    /** 仅使用明确有效的 ISBN-10 或 ISBN-13。 */
    private static String isbn(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return normalized.matches("[0-9]{9}[0-9X]|[0-9]{13}") ? normalized : "";
    }

    /** 合并全半角、大小写和两端空白。 */
    private static String normalized(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    /** 组合书名和作者，缺失任一字段时不用模糊匹配。 */
    private static Work work(String title, String author, boolean album) {
        String name = normalized(title);
        String writer = normalized(author);
        return name.isEmpty() || writer.isEmpty() ? null : new Work(name, writer, album);
    }

    /** 按章节、原文和想法的完整内容进行匹配。 */
    private static Passage passage(String chapter, String excerpt, String text) {
        return new Passage(normalized(chapter), normalized(excerpt), normalized(text));
    }

    /** 按字符截断，避免切开 Unicode 代理对。 */
    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0,
            Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max);
    }
}
