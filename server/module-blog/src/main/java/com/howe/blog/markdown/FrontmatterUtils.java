package com.howe.blog.markdown;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 文章 frontmatter 解析与生成
 *
 * <p>
 * 没有用 snakeyaml：它把 {@code date: 2026-03-30 11:26:55} 这种无时区标记的时间戳按 UTC
 * 解析，服务器在 +08:00 时区时会平移 8 小时——每同步一次日期就偏一次。frontmatter 结构很简单
 * （顶层标量 + 块状列表，无嵌套），自己解析反而更可控。
 * </p>
 *
 * <p>
 * 生成的字段顺序与仓库现有文章保持一致：title、categories、tags、id、date、updated，
 * 其后是可选字段，最后是 schema 之外原样保留的字段。
 * </p>
 *
 * @author howe
 */
public final class FrontmatterUtils
{
    /** frontmatter 分隔线 */
    private static final String DELIMITER = "---";

    /** UTF-8 BOM 码点，写成数值避免源码里出现不可见字符 */
    private static final char BOM = 65279;

    private static final String PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm:ss";

    private static final String PATTERN_DATE = "yyyy-MM-dd";

    /** 解析时依次尝试的日期格式，全部按服务器本地时区解析 */
    private static final String[] PARSE_PATTERNS = { PATTERN_DATE_TIME, "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", PATTERN_DATE, "yyyy/MM/dd" };

    private FrontmatterUtils()
    {
    }

    /**
     * 解析 markdown 文件
     *
     * @param raw 文件原文
     * @return frontmatter 与正文
     */
    public static MarkdownDocument parse(String raw)
    {
        if (raw == null)
        {
            return new MarkdownDocument(new Frontmatter(), "");
        }
        // 去掉 UTF-8 BOM，否则第一行匹配不上分隔线
        String text = (!raw.isEmpty() && raw.charAt(0) == BOM) ? raw.substring(1) : raw;
        // 统一换行符，避免 CRLF 文件解析出带 \r 的值
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (lines.isEmpty() || !DELIMITER.equals(lines.get(0).trim()))
        {
            // 没有 frontmatter，整体当正文
            return new MarkdownDocument(new Frontmatter(), text);
        }

        int end = -1;
        for (int i = 1; i < lines.size(); i++)
        {
            if (DELIMITER.equals(lines.get(i).trim()))
            {
                end = i;
                break;
            }
        }
        if (end < 0)
        {
            throw new ServiceException("文章 frontmatter 缺少结束的 --- 分隔线");
        }

        Frontmatter frontmatter = parseFields(lines.subList(1, end));
        String body = String.join("\n", lines.subList(end + 1, lines.size()));
        // 去掉 frontmatter 与正文之间的空行，正文以实际内容开头
        return new MarkdownDocument(frontmatter, StringUtils.stripStart(body, "\n"));
    }

    /**
     * 生成 markdown 文件原文
     *
     * @param frontmatter frontmatter
     * @param body 正文
     * @return 完整文件内容
     */
    public static String build(Frontmatter frontmatter, String body)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append('\n');
        appendScalar(sb, "title", frontmatter.getTitle());
        appendScalar(sb, "categories", frontmatter.getCategories());
        appendList(sb, "tags", frontmatter.getTags());
        appendScalar(sb, "id", frontmatter.getId());
        appendDate(sb, "date", frontmatter.getDate());
        appendDate(sb, "updated", frontmatter.getUpdated());
        appendScalar(sb, "cover", frontmatter.getCover());
        appendBoolean(sb, "recommend", frontmatter.getRecommend());
        appendBoolean(sb, "hide", frontmatter.getHide());
        appendBoolean(sb, "top", frontmatter.getTop());
        for (Map.Entry<String, String> entry : frontmatter.getExtras().entrySet())
        {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        sb.append(DELIMITER).append('\n').append('\n');
        if (StringUtils.isNotEmpty(body))
        {
            sb.append(StringUtils.stripStart(body.replace("\r\n", "\n").replace('\r', '\n'), "\n"));
            if (!body.endsWith("\n"))
            {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 解析 frontmatter 各字段
     */
    private static Frontmatter parseFields(List<String> lines)
    {
        Frontmatter frontmatter = new Frontmatter();
        Map<String, String> extras = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#"))
            {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0)
            {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();

            // 值为空时，后续缩进的 "- item" 行构成块状列表
            List<String> listValues = null;
            if (value.isEmpty())
            {
                listValues = new ArrayList<>();
                while (i + 1 < lines.size())
                {
                    String next = lines.get(i + 1);
                    String trimmed = next.trim();
                    if (next.isBlank())
                    {
                        i++;
                        continue;
                    }
                    if (!Character.isWhitespace(next.charAt(0)) || !trimmed.startsWith("-"))
                    {
                        break;
                    }
                    listValues.add(unquote(trimmed.substring(1).trim()));
                    i++;
                }
            }

            switch (key)
            {
                case "title":
                    frontmatter.setTitle(unquote(value));
                    break;
                case "categories":
                    // schema 是单个字符串；若手写成了列表则取第一项
                    frontmatter.setCategories(listValues != null && !listValues.isEmpty() ? listValues.get(0)
                            : unquote(value));
                    break;
                case "tags":
                    frontmatter.setTags(listValues != null ? listValues : parseInlineList(value));
                    break;
                case "id":
                    frontmatter.setId(unquote(value));
                    break;
                case "date":
                    frontmatter.setDate(parseDate(unquote(value), key));
                    break;
                case "updated":
                    frontmatter.setUpdated(parseDate(unquote(value), key));
                    break;
                case "cover":
                    frontmatter.setCover(unquote(value));
                    break;
                case "recommend":
                    frontmatter.setRecommend(parseBoolean(unquote(value)));
                    break;
                case "hide":
                    frontmatter.setHide(parseBoolean(unquote(value)));
                    break;
                case "top":
                    frontmatter.setTop(parseBoolean(unquote(value)));
                    break;
                default:
                    // schema 之外的字段原样留着，编辑保存时不丢
                    extras.put(key, value);
                    break;
            }
        }
        frontmatter.setExtras(extras);
        return frontmatter;
    }

    /**
     * 解析行内数组写法 [a, b, c]
     */
    private static List<String> parseInlineList(String value)
    {
        List<String> result = new ArrayList<>();
        if (StringUtils.isEmpty(value))
        {
            return result;
        }
        String inner = value;
        if (inner.startsWith("[") && inner.endsWith("]"))
        {
            inner = inner.substring(1, inner.length() - 1);
        }
        for (String part : inner.split(","))
        {
            String item = unquote(part.trim());
            if (StringUtils.isNotEmpty(item))
            {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 去掉包裹的引号并还原转义
     */
    private static String unquote(String value)
    {
        if (value == null || value.length() < 2)
        {
            return value == null ? null : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (first == '\'' && last == '\'')
        {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (first == '"' && last == '"')
        {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    /**
     * 按本地时区解析日期
     */
    private static Date parseDate(String value, String field)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        for (String pattern : PARSE_PATTERNS)
        {
            try
            {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(value);
            }
            catch (ParseException ignored)
            {
                // 换下一个格式继续尝试
            }
        }
        throw new ServiceException("无法解析 frontmatter 的 " + field + " 字段：" + value);
    }

    private static Boolean parseBoolean(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static void appendScalar(StringBuilder sb, String key, String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return;
        }
        sb.append(key).append(": ").append(quoteIfNeeded(value)).append('\n');
    }

    private static void appendList(StringBuilder sb, String key, List<String> values)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }
        sb.append(key).append(":").append('\n');
        for (String value : values)
        {
            sb.append("  - ").append(quoteIfNeeded(value)).append('\n');
        }
    }

    private static void appendBoolean(StringBuilder sb, String key, Boolean value)
    {
        if (value == null)
        {
            return;
        }
        sb.append(key).append(": ").append(value ? "true" : "false").append('\n');
    }

    /**
     * 输出日期
     *
     * <p>
     * 时间部分为零点时只写日期，与仓库里 {@code updated: 2026-07-29} 的写法一致；
     * 带时刻的则写完整时间，保证读出来再写回去内容不变。
     * </p>
     */
    private static void appendDate(StringBuilder sb, String key, Date value)
    {
        if (value == null)
        {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        boolean midnight = calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.SECOND) == 0;
        String pattern = midnight ? PATTERN_DATE : PATTERN_DATE_TIME;
        sb.append(key).append(": ").append(new SimpleDateFormat(pattern).format(value)).append('\n');
    }

    /**
     * 判断标量值是否需要加引号
     *
     * <p>
     * 仓库现有文章的标题含全角竖线也没加引号，所以只在 YAML 真会误解析时才加，
     * 避免生成的 diff 与手写风格不一致。
     * </p>
     */
    static String quoteIfNeeded(String value)
    {
        if (value.isEmpty())
        {
            return "''";
        }
        boolean needQuote = value.contains(": ") || value.endsWith(":") || value.contains(" #")
                || value.contains("\n") || !value.equals(value.strip())
                || "~".equals(value) || isReservedWord(value) || looksLikeNumber(value)
                || "-?:,[]{}#&*!|>'\"%@`".indexOf(value.charAt(0)) >= 0;
        if (!needQuote)
        {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static boolean isReservedWord(String value)
    {
        String lower = value.toLowerCase();
        return "true".equals(lower) || "false".equals(lower) || "null".equals(lower) || "yes".equals(lower)
                || "no".equals(lower) || "on".equals(lower) || "off".equals(lower);
    }

    private static boolean looksLikeNumber(String value)
    {
        return value.matches("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    }
}
