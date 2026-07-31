package com.howe.blog.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章索引同步结果
 *
 * @author howe
 */
public class BlogSyncResult
{
    /** 仓库中扫描到的 markdown 文件数 */
    private int scanned;

    /** 新增索引数 */
    private int added;

    /** 更新索引数 */
    private int updated;

    /** 删除索引数（仓库里已不存在） */
    private int removed;

    /** 跳过数（sha 未变化，无需重新解析） */
    private int skipped;

    /** 解析失败的文件及原因 */
    private List<String> failures = new ArrayList<>();

    public void addFailure(String path, String reason)
    {
        failures.add(path + "：" + reason);
    }

    public int getScanned()
    {
        return scanned;
    }

    public void setScanned(int scanned)
    {
        this.scanned = scanned;
    }

    public int getAdded()
    {
        return added;
    }

    public void setAdded(int added)
    {
        this.added = added;
    }

    public void increaseAdded()
    {
        this.added++;
    }

    public int getUpdated()
    {
        return updated;
    }

    public void setUpdated(int updated)
    {
        this.updated = updated;
    }

    public void increaseUpdated()
    {
        this.updated++;
    }

    public int getRemoved()
    {
        return removed;
    }

    public void setRemoved(int removed)
    {
        this.removed = removed;
    }

    public void increaseRemoved()
    {
        this.removed++;
    }

    public int getSkipped()
    {
        return skipped;
    }

    public void setSkipped(int skipped)
    {
        this.skipped = skipped;
    }

    public void increaseSkipped()
    {
        this.skipped++;
    }

    public List<String> getFailures()
    {
        return failures;
    }

    public void setFailures(List<String> failures)
    {
        this.failures = failures;
    }
}
