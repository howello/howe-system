package com.howe.book.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.book.config.BookWeReadSyncConfig;
import com.howe.book.domain.BookNode;
import com.howe.book.domain.BookWeReadStatus;
import com.howe.book.domain.BookWeReadStatus.Task;
import com.howe.book.service.BookWeReadSyncService;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.weread.service.WeReadService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 服务端拉取书架和笔记，比较本地节点后追加新增内容。 */
@Slf4j
@Service
public class BookWeReadSyncServiceImpl implements BookWeReadSyncService {
    private final WeReadService weRead;
    private final BookWeReadImport database;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final Executor executor;
    private volatile Task latest;

    /** 注入网关及同步专用的执行线程。 */
    public BookWeReadSyncServiceImpl(WeReadService weRead, BookWeReadImport database, ObjectMapper json,
        StringRedisTemplate redis, @Qualifier(BookWeReadSyncConfig.EXECUTOR) Executor executor) {
        this.weRead = weRead;
        this.database = database;
        this.json = json;
        this.redis = redis;
        this.executor = executor;
    }

    /** 返回共享书架的配置和任务状态。 */
    @Override
    public BookWeReadStatus status() {
        return new BookWeReadStatus(!key().isBlank(), latest);
    }

    /** 登录用户提交任务，新增节点记录触发人。 */
    @Override
    public BookWeReadStatus start(long userId) {
        String apiKey = key();
        if (apiKey.isBlank()) {
            throw new ServiceException("请在参数设置中配置微信读书 API Key");
        }
        return new BookWeReadStatus(true, submit("manual", userId, apiKey));
    }

    /** 每日按北京时间凌晨五点提交同一导入任务。 */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Shanghai")
    public void schedule() {
        String apiKey = key();
        if (!apiKey.isBlank()) {
            submit("scheduled", 0L, apiKey);
        }
    }

    /** 手动和定时请求同时到达时复用同一次任务。 */
    private synchronized Task submit(String trigger, long owner, String apiKey) {
        if (latest != null && "running".equals(latest.state())) {
            return latest;
        }
        var run = new Run(UUID.randomUUID().toString(), trigger, Instant.now());
        latest = run.snapshot("running", "正在读取微信读书书架和笔记本");
        try {
            executor.execute(() -> sync(run, owner, apiKey));
        } catch (RejectedExecutionException exception) {
            latest = run.snapshot("failed", "同步暂不可用，请稍后重试");
        }
        return latest;
    }

    /** 先取基础清单，后续完整处理每一本书，单书异常可在下次同步重试。 */
    private void sync(Run run, long owner, String apiKey) {
        try {
            JsonNode shelf = weRead.shelf(apiKey);
            requireArray(shelf, "books");
            Map<String, JsonNode> notebooks = listNotebooks(apiKey);
            run.batch = database.open(owner);
            var groups = new HashMap<String, List<Archive>>();
            for (JsonNode item : shelf.path("archive")) {
                String name = item.path("name").asText().strip();
                if (name.isEmpty() || flag(item.path("secret")) || flag(item.path("isPrivate"))) {
                    continue;
                }
                var archive = new Archive(name, run.batch.archive(name));
                for (JsonNode bookId : item.path("bookIds")) {
                    groups.computeIfAbsent(bookId.asText(), ignored -> new ArrayList<>()).add(archive);
                }
            }
            var books = new LinkedHashMap<String, JsonNode>();
            for (JsonNode item : shelf.path("books")) {
                String id = item.path("bookId").asText();
                if (!id.isBlank()) {
                    books.putIfAbsent(id, item);
                }
            }
            var bookIds = new java.util.LinkedHashSet<>(books.keySet());
            bookIds.addAll(notebooks.keySet());
            JsonNode albums = shelf.path("albums");
            run.total = bookIds.size() + albums.size();
            latest = run.snapshot("running", "正在比对书架、划线和想法");
            String cachePrefix = "book:weread:notes:v2:" + DigestUtil.sha256Hex(apiKey) + ":";
            for (String id : bookIds) {
                JsonNode onShelf = books.get(id);
                JsonNode notebook = notebooks.getOrDefault(id, json.missingNode());
                attempt(run, "书本", () -> syncBook(run.batch, apiKey, cachePrefix,
                    id, onShelf, notebook, groups.getOrDefault(id, List.of())));
            }
            for (JsonNode item : albums) {
                attempt(run, "有声书", () -> syncAlbum(run.batch, item, groups));
            }
            latest = run.snapshot(run.failed == 0 ? "succeeded" : "partial", run.failed == 0 ? "同步完成"
                : "部分书籍同步失败，可再次同步" + (run.failure == null ? "" : "（" + run.failure + "）"));
        } catch (Exception exception) {
            log.warn("微信读书同步失败，任务 ID={}", run.id, exception);
            latest = run.snapshot("failed", exception instanceof ServiceException
                ? exception.getMessage() : "同步失败，请稍后重试");
        }
    }

    /** 分页读取有笔记的书，保护异常游标导致的重复拉取。 */
    private Map<String, JsonNode> listNotebooks(String apiKey) {
        var result = new LinkedHashMap<String, JsonNode>();
        Set<Long> cursors = new HashSet<>();
        Long cursor = null;
        while (true) {
            JsonNode page = weRead.notebooks(apiKey, cursor);
            JsonNode books = requireArray(page, "books");
            for (JsonNode book : books) {
                String id = book.path("bookId").asText();
                if (!id.isBlank()) {
                    result.putIfAbsent(id, book);
                }
            }
            if (!flag(page.path("hasMore"))) {
                return result;
            }
            cursor = books.path(books.size() - 1).path("sort").asLong(0);
            if (cursor <= 0 || !cursors.add(cursor)) {
                throw new ServiceException("微信读书笔记本翻页游标无效");
            }
        }
    }

    /** 导入书籍详情和新增感悟，已有书保持本地标题、封面及归属。 */
    private void syncBook(BookWeReadImport.Batch batch, String apiKey, String cachePrefix,
        String id, JsonNode onShelf, JsonNode notebook, List<Archive> archives) {
        JsonNode overview = notebook.path("book");
        JsonNode shelfBook = onShelf == null ? json.missingNode() : onShelf;
        if (flag(shelfBook.path("secret")) || flag(overview.path("secret"))) {
            return;
        }
        var info = BookWeReadMetadata.merge(json.createObjectNode(), overview, shelfBook);
        BookNode book = batch.sourcedBook(id);
        if (book != null && book.getDeletedAt() != null) {
            return;
        }
        // 离架书籍的公开状态可能缺失，默认只收入当前或历史来源明确公开的书。
        if (!publicFlag(info.path("secret")) && !batch.knownPublic(book)) {
            return;
        }
        if (book == null) {
            BookWeReadMetadata.merge(info, weRead.bookInfo(apiKey, id));
            if (info.path("title").asText().isBlank() || flag(info.path("secret"))) {
                return;
            }
            book = batch.existingBook(id, info, false);
            if (book == null) {
                BookNode parent = archives.isEmpty() ? batch.catalog(info, false) : firstArchive(archives);
                book = batch.addBook(id, info, false, parent, archives.stream().map(Archive::name).toList(),
                    onShelf != null, notebook);
            }
        }
        if (book != null && book.getDeletedAt() == null) {
            syncNotes(batch, apiKey, cachePrefix, id, book, notebook);
        }
    }

    /** 有声书使用书架的专辑字段，并优先采用已有书单。 */
    private void syncAlbum(BookWeReadImport.Batch batch, JsonNode item,
        Map<String, List<Archive>> archiveGroups) {
        var info = BookWeReadMetadata.merge(json.createObjectNode(), item.path("albumInfo"));
        JsonNode secret = item.path("albumInfoExtra").path("secret");
        String id = info.path("albumId").asText();
        if (id.isBlank() || info.path("name").asText().isBlank() || !publicFlag(secret)
            || batch.existingBook(id, info, true) != null) {
            return;
        }
        if (!secret.isMissingNode() && !secret.isNull()) {
            info.set("secret", secret);
        }
        List<Archive> archives = archiveGroups.getOrDefault(id, List.of());
        BookNode parent = archives.isEmpty() ? batch.catalog(info, true) : firstArchive(archives);
        batch.addBook(id, info, true, parent, archives.stream().map(Archive::name).toList(), true, json.missingNode());
    }

    /** 一书只对应一个本地父分类，按微信读书书单顺序选择仍存在的分类。 */
    private BookNode firstArchive(List<Archive> archives) {
        return archives.stream().map(Archive::node).filter(node -> node.getDeletedAt() == null)
            .findFirst().orElse(archives.get(0).node());
    }

    /** 比较笔记本摘要，只对新增或变化的书查询笔记详情。 */
    private void syncNotes(BookWeReadImport.Batch batch, String apiKey, String cachePrefix,
        String id, BookNode book, JsonNode notebook) {
        int highlights = notebook.path("noteCount").asInt();
        int reviews = notebook.path("reviewCount").asInt();
        if (highlights <= 0 && reviews <= 0) {
            return;
        }
        long updated = notebook.path("sort").asLong();
        String summary = updated + ":" + highlights + ":" + reviews;
        String createdAt = book.getCreatedAt() == null ? "" : book.getCreatedAt().withNano(0).toString();
        String cacheKey = cachePrefix + DigestUtil.sha256Hex(id + ":" + book.getId() + ":" + createdAt);
        if (updated > 0 && summary.equals(readCheckpoint(cacheKey))) {
            return;
        }
        var notes = new ArrayList<BookWeReadImport.Note>();
        if (highlights > 0) {
            notes.addAll(listHighlights(apiKey, id));
        }
        if (reviews > 0) {
            notes.addAll(listReviews(apiKey, id));
        }
        batch.addNotes(id, book, notes);
        if (updated > 0) {
            saveCheckpoint(cacheKey, summary);
        }
    }

    /** 将微信读书划线映射到含原文和章节的感悟。 */
    private List<BookWeReadImport.Note> listHighlights(String apiKey, String bookId) {
        JsonNode data = weRead.highlights(apiKey, bookId);
        var chapters = new HashMap<String, String>();
        for (JsonNode chapter : data.path("chapters")) {
            chapters.put(chapter.path("chapterUid").asText(), chapter.path("title").asText());
        }
        var notes = new ArrayList<BookWeReadImport.Note>();
        for (JsonNode row : requireArray(data, "updated")) {
            String excerpt = row.path("markText").asText().strip();
            String chapter = row.path("chapterUid").asText();
            String range = row.path("range").asText();
            String legacyId = chapter.isBlank() || range.isBlank() ? "" : bookId + "_" + chapter + "_" + range;
            String id = row.path("bookmarkId").asText(legacyId);
            if (!id.isBlank() && !excerpt.isBlank() && row.path("type").asInt(1) == 1
                && !flag(row.path("secret")) && !flag(row.path("isPrivate"))) {
                notes.add(new BookWeReadImport.Note("highlight", id, legacyId,
                    chapters.getOrDefault(chapter, ""), excerpt, "", row));
            }
        }
        return notes;
    }

    /** 遍历个人想法和点评页，保留可定位到的划线原文。 */
    private List<BookWeReadImport.Note> listReviews(String apiKey, String bookId) {
        var notes = new ArrayList<BookWeReadImport.Note>();
        Set<Long> cursors = new HashSet<>();
        long cursor = 0;
        while (true) {
            JsonNode page = weRead.reviews(apiKey, bookId, cursor);
            for (JsonNode entry : requireArray(page, "reviews")) {
                JsonNode row = entry.path("review");
                String id = row.path("reviewId").asText();
                String text = row.path("content").asText().strip();
                String excerpt = row.path("abstract").asText().strip();
                if (id.isBlank() || (text.isBlank() && excerpt.isBlank())
                    || flag(row.path("isPrivate")) || flag(row.path("notVisibleToFriends"))) {
                    continue;
                }
                notes.add(new BookWeReadImport.Note("review", id, "", row.path("chapterName").asText(), excerpt, text, row));
            }
            if (!flag(page.path("hasMore"))) {
                return notes;
            }
            cursor = page.path("synckey").asLong(0);
            if (cursor <= 0 || !cursors.add(cursor)) {
                throw new ServiceException("微信读书想法翻页游标无效");
            }
        }
    }

    /** 单本书失败时记录进度，后续继续处理其他书。 */
    private void attempt(Run run, String type, Runnable operation) {
        try {
            operation.run();
        } catch (Exception exception) {
            run.failed++;
            if (run.failure == null && exception instanceof ServiceException) {
                run.failure = exception.getMessage();
            }
            log.warn("微信读书单项同步失败，任务 ID={}，类型={}，进度={}", run.id, type, run.completed, exception);
        }
        run.completed++;
        latest = run.snapshot("running", "正在比对书架、划线和想法");
    }

    /** 读取已处理的笔记摘要，缓存不可用时继续比较数据库。 */
    private String readCheckpoint(String cacheKey) {
        try {
            return redis.opsForValue().get(cacheKey);
        } catch (RuntimeException exception) {
            log.debug("读取微信读书同步摘要失败，异常类型={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /** 笔记成功处理后短期保存摘要，过期时可完整复查。 */
    private void saveCheckpoint(String cacheKey, String summary) {
        try {
            redis.opsForValue().set(cacheKey, summary, Duration.ofDays(7));
        } catch (RuntimeException exception) {
            log.debug("保存微信读书同步摘要失败，异常类型={}", exception.getClass().getSimpleName());
        }
    }

    /** 校验列表回包，避免把协议异常误认为空数据。 */
    private JsonNode requireArray(JsonNode response, String field) {
        JsonNode array = response.path(field);
        if (!array.isArray()) {
            throw new ServiceException("微信读书未返回有效的 " + field + " 列表");
        }
        return array;
    }

    /** 判断上游布尔或 0/1 标记。 */
    private boolean flag(JsonNode value) {
        return value.asBoolean(false) || value.asInt(0) == 1;
    }

    /** 只有显式为公开的状态才允许首次导入到共享书架。 */
    private boolean publicFlag(JsonNode value) {
        return value.isBoolean() ? !value.asBoolean() : value.asInt(-1) == 0;
    }

    /** 获取同步凭据。 */
    private String key() {
        return ConfigUtils.getString(ConfigConstants.BOOK_WEREAD_API_KEY);
    }

    /** 书单及其对应的本地分类，可以读取已删除的分类标记。 */
    private record Archive(String name, BookNode node) {}

    /** 工作线程上的进度计数，对外发布不可变快照。 */
    private static final class Run {
        private final String id;
        private final String trigger;
        private final Instant startedAt;
        private BookWeReadImport.Batch batch;
        private int total;
        private int completed;
        private int failed;
        private String failure;

        /** 创建一次任务。 */
        private Run(String id, String trigger, Instant startedAt) {
            this.id = id;
            this.trigger = trigger;
            this.startedAt = startedAt;
        }

        /** 生成当前任务进度。 */
        private Task snapshot(String state, String message) {
            var counts = batch == null ? new BookWeReadImport.Counts(0, 0, 0) : batch.counts();
            return new Task(id, state, trigger, total, completed, counts.categories(), counts.books(), counts.notes(),
                failed, message, startedAt, "running".equals(state) ? null : Instant.now());
        }
    }
}
