package com.tang.springbootinit.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.tang.springbootinit.common.ErrorCode;
import com.tang.springbootinit.constant.CommonConstant;
import com.tang.springbootinit.exception.BusinessException;
import com.tang.springbootinit.exception.ThrowUtils;
import com.tang.springbootinit.mapper.PostFavourMapper;
import com.tang.springbootinit.mapper.PostMapper;
import com.tang.springbootinit.mapper.PostThumbMapper;
import com.tang.springbootinit.model.dto.post.PostEsDTO;
import com.tang.springbootinit.model.dto.post.PostQueryRequest;
import com.tang.springbootinit.model.entity.Post;
import com.tang.springbootinit.model.entity.PostFavour;
import com.tang.springbootinit.model.entity.PostThumb;
import com.tang.springbootinit.model.entity.User;
import com.tang.springbootinit.model.vo.PostVO;
import com.tang.springbootinit.model.vo.UserVO;
import com.tang.springbootinit.service.PostService;
import com.tang.springbootinit.service.UserService;
import com.tang.springbootinit.utils.SqlUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 帖子服务实现
 *
 *  @author
 *  @from
 */
@Service
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private final static Gson GSON = new Gson();

    @Resource
    private UserService userService;

    @Resource
    private PostThumbMapper postThumbMapper;

    @Resource
    private PostFavourMapper postFavourMapper;

//    @Resource
//    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Override
    public void validPost(Post post, boolean add) {
        if (post == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = post.getTitle();
        String content = post.getContent();
        String tags = post.getTags();
        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
    }

    /**
     * 获取查询包装类
     *
     * @param postQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        if (postQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = postQueryRequest.getSearchText();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        Long id = postQueryRequest.getId();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tagList = postQueryRequest.getTags();
        Long userId = postQueryRequest.getUserId();
        Long notId = postQueryRequest.getNotId();
        // 拼接查询条件
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.like("title", searchText).or().like("content", searchText);
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        if (CollectionUtils.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public Page<Post> searchFromEs(PostQueryRequest postQueryRequest) {
        try {
            // 构建 BoolQuery.Builder
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            // filter: isDelete == false
            boolQueryBuilder.filter(q -> q.term(t -> t.field("isDelete").value(false)));

            if (postQueryRequest.getId() != null) {
                boolQueryBuilder.filter(q -> q.term(t -> t.field("id").value(postQueryRequest.getId())));
            }
            if (postQueryRequest.getNotId() != null) {
                boolQueryBuilder.mustNot(q -> q.term(t -> t.field("id").value(postQueryRequest.getNotId())));
            }
            if (postQueryRequest.getUserId() != null) {
                boolQueryBuilder.filter(q -> q.term(t -> t.field("userId").value(postQueryRequest.getUserId())));
            }
            // 必须包含所有标签
            if (postQueryRequest.getTags() != null && !postQueryRequest.getTags().isEmpty()) {
                for (String tag : postQueryRequest.getTags()) {
                    boolQueryBuilder.filter(q -> q.term(t -> t.field("tags").value(tag)));
                }
            }
            // 或标签
            if (postQueryRequest.getOrTags() != null && !postQueryRequest.getOrTags().isEmpty()) {
                List<Query> shouldQueries = postQueryRequest.getOrTags().stream()
                        .map(tag -> Query.of(q -> q.term(t -> t.field("tags").value(tag))))
                        .collect(Collectors.toList());
                boolQueryBuilder.filter(q -> q.bool(b -> b.should(shouldQueries).minimumShouldMatch("1")));
            }
            // 搜索文本（title, description, content）
            if (StringUtils.isNotBlank(postQueryRequest.getSearchText())) {
                List<Query> shouldQueries = new ArrayList<>();
                shouldQueries.add(Query.of(q -> q.match(m -> m.field("title").query(postQueryRequest.getSearchText()))));
                shouldQueries.add(Query.of(q -> q.match(m -> m.field("description").query(postQueryRequest.getSearchText()))));
                shouldQueries.add(Query.of(q -> q.match(m -> m.field("content").query(postQueryRequest.getSearchText()))));
                boolQueryBuilder.should(bq -> bq.bool(b -> b.should(shouldQueries).minimumShouldMatch("1")));
            }
            // 标题搜索
            if (StringUtils.isNotBlank(postQueryRequest.getTitle())) {
                boolQueryBuilder.should(q -> q.match(m -> m.field("title").query(postQueryRequest.getTitle())));
            }
            // 内容搜索
            if (StringUtils.isNotBlank(postQueryRequest.getContent())) {
                boolQueryBuilder.should(q -> q.match(m -> m.field("content").query(postQueryRequest.getContent())));
            }

            Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));

            // 构造分页和排序
            int page = (int) (postQueryRequest.getCurrent() > 0 ? postQueryRequest.getCurrent() - 1 : 0);
            int size = (int) postQueryRequest.getPageSize();

            SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                    .index("post")  // 请确认你的索引名
                    .query(query)
                    .from(page * size)
                    .size(size);

            if (StringUtils.isNotBlank(postQueryRequest.getSortField())) {
                requestBuilder.sort(s -> s.field(f -> f.field(postQueryRequest.getSortField())
                        .order(CommonConstant.SORT_ORDER_ASC.equals(postQueryRequest.getSortOrder()) ?
                                co.elastic.clients.elasticsearch._types.SortOrder.Asc :
                                co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
            } else {
                // 默认按相关度排序
                requestBuilder.sort(s -> s.score(ss -> ss.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
            }

            SearchResponse<PostEsDTO> response = elasticsearchClient.search(requestBuilder.build(), PostEsDTO.class);

            // 处理返回结果，转换为Page<Post>
            List<Long> postIdList = response.hits().hits().stream()
                    .map(hit -> hit.source().getId())
                    .collect(Collectors.toList());

            List<Post> postList = postIdList.isEmpty() ? new ArrayList<>() : baseMapper.selectBatchIds(postIdList);

            Page<Post> pageResult = new Page<>();
            pageResult.setCurrent(postQueryRequest.getCurrent());
            pageResult.setSize(postQueryRequest.getPageSize());
            pageResult.setTotal(response.hits().total() != null ? response.hits().total().value() : 0);
            pageResult.setRecords(postList);

            return pageResult;
        } catch (IOException e) {
            log.error("ES查询异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Elasticsearch查询失败");
        }
    }

    @Override
    public PostVO getPostVO(Post post, HttpServletRequest request) {
        PostVO postVO = PostVO.objToVo(post);
        long postId = post.getId();
        // 1. 关联查询用户信息
        Long userId = post.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        postVO.setUser(userVO);
        // 2. 已登录，获取用户点赞、收藏状态
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            // 获取点赞
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postId);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            PostThumb postThumb = postThumbMapper.selectOne(postThumbQueryWrapper);
            postVO.setHasThumb(postThumb != null);
            // 获取收藏
            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postId);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            PostFavour postFavour = postFavourMapper.selectOne(postFavourQueryWrapper);
            postVO.setHasFavour(postFavour != null);
        }
        return postVO;
    }

    @Override
    public Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request) {
        List<Post> postList = postPage.getRecords();
        Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        if (CollectionUtils.isEmpty(postList)) {
            return postVOPage;
        }
        // 1. 关联查询用户信息
        Set<Long> userIdSet = postList.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 已登录，获取用户点赞、收藏状态
        Map<Long, Boolean> postIdHasThumbMap = new HashMap<>();
        Map<Long, Boolean> postIdHasFavourMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            Set<Long> postIdSet = postList.stream().map(Post::getId).collect(Collectors.toSet());
            loginUser = userService.getLoginUser(request);
            // 获取点赞
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postIdSet);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            List<PostThumb> postPostThumbList = postThumbMapper.selectList(postThumbQueryWrapper);
            postPostThumbList.forEach(postPostThumb -> postIdHasThumbMap.put(postPostThumb.getPostId(), true));
            // 获取收藏
            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postIdSet);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            List<PostFavour> postFavourList = postFavourMapper.selectList(postFavourQueryWrapper);
            postFavourList.forEach(postFavour -> postIdHasFavourMap.put(postFavour.getPostId(), true));
        }
        // 填充信息
        List<PostVO> postVOList = postList.stream().map(post -> {
            PostVO postVO = PostVO.objToVo(post);
            Long userId = post.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            postVO.setUser(userService.getUserVO(user));
            postVO.setHasThumb(postIdHasThumbMap.getOrDefault(post.getId(), false));
            postVO.setHasFavour(postIdHasFavourMap.getOrDefault(post.getId(), false));
            return postVO;
        }).collect(Collectors.toList());
        postVOPage.setRecords(postVOList);
        return postVOPage;
    }

}

