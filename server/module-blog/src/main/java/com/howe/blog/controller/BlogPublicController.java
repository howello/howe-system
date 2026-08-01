package com.howe.blog.controller;

import com.howe.blog.domain.BlogFeedItem;
import com.howe.blog.domain.BlogLink;
import com.howe.blog.domain.BlogTalk;
import com.howe.blog.domain.vo.BlogLinkGroupVo;
import com.howe.blog.domain.vo.BlogLinkPublicVo;
import com.howe.blog.domain.vo.BlogTalkPublicVo;
import com.howe.blog.service.IBlogFeedItemService;
import com.howe.blog.service.IBlogLinkService;
import com.howe.blog.service.IBlogTalkService;
import com.github.pagehelper.PageHelper;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.PageDomain;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.core.page.TableSupport;
import com.howe.common.enums.LimitType;
import com.howe.common.utils.DictUtils;
import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客公开只读接口
 *
 * <p>供 blog-ui 静态站取数：友链页、朋友圈页、说说页。三个接口都是<b>只读</b>的，
 * 与开放<b>写入</b>接口 {@code /blog/open/article} 明确分开——后者要令牌，这里不要。</p>
 *
 * <p>匿名放行一律用 {@code @Anonymous}，不改 {@code SecurityConfig} 的硬编码白名单。
 * 匿名接口必须自带防滥用，因此三个接口都挂了按 IP 维度的 {@code @RateLimiter}。</p>
 *
 * <p>三个接口只返回对外可见的数据：停用友链、停用订阅源的条目、隐藏说说都不外泄。</p>
 *
 * @author howe
 */
@Tag(name = "博客公开接口", description = "供 blog-ui 静态站匿名调用的只读接口")
@RestController
@RequestMapping("/blog/public")
@RequiredArgsConstructor
public class BlogPublicController extends BaseController {

    /** 类型：友链 */
    private static final String TYPE_LINK = "1";

    /** 状态：正常/发布 */
    private static final String STATUS_NORMAL = "0";

    /** 友链分组字典类型 */
    private static final String DICT_LINK_GROUP = "blog_link_group";

    /** 未分组或字典项已删除时的兜底分组名 */
    private static final String GROUP_OTHER_NAME = "其他";

    /**
     * 匿名分页接口的每页条数上限
     *
     * <p>通用的 {@code startPage()} 直接读请求参数里的 pageSize 且<b>没有上限</b>。
     * 在此之前分页接口都要登录，这个默认还说得过去；这三个是本项目第一批匿名分页接口，
     * 不设上限的话 {@code ?pageSize=1000000} 就能把整张表（说说正文还是 longtext）
     * 一次性读进堆——IP 限流每分钟 60 次，足够打爆内存了。</p>
     */
    private static final int MAX_PAGE_SIZE = 50;

    private final IBlogLinkService blogLinkService;
    private final IBlogFeedItemService blogFeedItemService;
    private final IBlogTalkService blogTalkService;

    /**
     * 按请求参数分页，但把每页条数钳在上限内
     */
    private void startPublicPage() {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        PageHelper.startPage(pageNum == null ? 1 : pageNum,
                Math.min(pageSize == null ? 10 : pageSize, MAX_PAGE_SIZE));
    }

    /**
     * 查询全部启用友链，按分组聚合
     *
     * <p>不分页：分页会把分组从中间切断，同一组的标题在相邻两页重复出现。
     * 单次访问只打一次，限流阈值取得比翻页型接口低。</p>
     *
     * @return 分组后的友链
     */
    @Operation(summary = "查询友链（按分组聚合）",
            description = "返回全部启用友链，不分页；分组名已由服务端按字典翻译好，可直接展示")
    @Anonymous
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/links")
    public AjaxResult links() {
        BlogLink query = new BlogLink();
        query.setLinkType(TYPE_LINK);
        query.setStatus(STATUS_NORMAL);
        List<BlogLink> links = blogLinkService.selectBlogLinkList(query);
        return success(groupLinks(links));
    }

    /**
     * 分页查询朋友圈条目
     *
     * @return 条目分页数据
     */
    @Operation(summary = "查询朋友圈条目", description = "分页返回启用中订阅源的条目，按发布时间倒序")
    @Anonymous
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/moments")
    public TableDataInfo moments() {
        startPublicPage();
        List<BlogFeedItem> list = blogFeedItemService.selectPublicFeedItemList();
        return getDataTable(list);
    }

    /**
     * 分页查询说说
     *
     * @return 说说分页数据
     */
    @Operation(summary = "查询说说", description = "分页返回已发布的说说，置顶优先、同级按发布时间倒序")
    @Anonymous
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/talks")
    public TableDataInfo talks() {
        BlogTalk query = new BlogTalk();
        // 固定只查已发布，隐藏的说说不外泄
        query.setStatus(STATUS_NORMAL);
        startPublicPage();
        List<BlogTalk> list = blogTalkService.selectBlogTalkList(query);
        // 先取分页结果的 total，再换成 VO——直接 map 会丢掉 PageHelper 的 Page 包装
        TableDataInfo table = getDataTable(list);
        table.setRows(list.stream()
                .map(t -> new BlogTalkPublicVo(t.getContent(), t.getTags(), t.getPubDate(), t.getIsTop()))
                .toList());
        return table;
    }

    /**
     * 按分组聚合友链，并把分组编码翻译成可展示的名称
     *
     * <p>分组为空、或字典项已被删除（翻译结果为空）的友链统一归入「其他」并<b>排在最后</b>——
     * 让友链因为一个字典项被删就从页面上消失是不可接受的。</p>
     *
     * <p>入参已按 {@code order_num asc, link_id asc} 排好序，这里用 {@link LinkedHashMap}
     * 保持分组的首次出现顺序，使分组次序同样受 order_num 控制。</p>
     *
     * @param links 已启用的友链
     * @return 分组集合
     */
    private List<BlogLinkGroupVo> groupLinks(List<BlogLink> links) {
        Map<String, List<BlogLinkPublicVo>> grouped = new LinkedHashMap<>();
        Map<String, String> groupNames = new LinkedHashMap<>();
        List<BlogLinkPublicVo> others = new ArrayList<>();

        for (BlogLink link : links) {
            BlogLinkPublicVo vo = toPublicVo(link);
            String code = StringUtils.trim(link.getGroupCode());
            if (StringUtils.isEmpty(code)) {
                others.add(vo);
                continue;
            }
            String name = DictUtils.getDictLabel(DICT_LINK_GROUP, code);
            if (StringUtils.isEmpty(name)) {
                // 字典项被删了，但友链本身还在，归入「其他」而不是丢弃
                others.add(vo);
                continue;
            }
            grouped.computeIfAbsent(code, k -> new ArrayList<>()).add(vo);
            groupNames.putIfAbsent(code, name);
        }

        List<BlogLinkGroupVo> result = new ArrayList<>();
        for (Map.Entry<String, List<BlogLinkPublicVo>> entry : grouped.entrySet()) {
            result.add(new BlogLinkGroupVo(entry.getKey(), groupNames.get(entry.getKey()), entry.getValue()));
        }
        if (!others.isEmpty()) {
            result.add(new BlogLinkGroupVo("", GROUP_OTHER_NAME, others));
        }
        return result;
    }

    /**
     * 实体转公开视图，剥掉 createBy/updateBy/remark 等后台审计字段
     *
     * @param link 站点
     * @return 公开视图
     */
    private BlogLinkPublicVo toPublicVo(BlogLink link) {
        return new BlogLinkPublicVo(link.getLinkName(), link.getLinkUrl(), link.getAvatar(), link.getDescr());
    }
}
