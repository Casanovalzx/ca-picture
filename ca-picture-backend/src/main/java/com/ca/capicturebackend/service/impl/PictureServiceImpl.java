package com.ca.capicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ca.capicturebackend.api.aliyunai.AliYunAiApi;
import com.ca.capicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ca.capicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ca.capicturebackend.manager.auth.SpaceUserAuthManager;
import com.ca.capicturebackend.manager.auth.StpKit;
import com.ca.capicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.ca.capicturebackend.model.dto.picture.DeletePictureByBatchRequest;
import com.ca.capicturebackend.exception.BusinessException;
import com.ca.capicturebackend.exception.ErrorCode;
import com.ca.capicturebackend.exception.ThrowUtils;
import com.ca.capicturebackend.manager.CacheManager;
import com.ca.capicturebackend.manager.CosManager;
import com.ca.capicturebackend.manager.upload.FilePictureUpload;
import com.ca.capicturebackend.manager.upload.PictureUploadTemplate;
import com.ca.capicturebackend.manager.upload.UrlPictureUpload;
import com.ca.capicturebackend.model.dto.file.UploadPictureResult;
import com.ca.capicturebackend.model.dto.picture.*;
import com.ca.capicturebackend.model.entity.Picture;
import com.ca.capicturebackend.model.entity.Space;
import com.ca.capicturebackend.model.entity.User;
import com.ca.capicturebackend.model.enums.PictureReviewStatusEnum;
import com.ca.capicturebackend.model.enums.CommonKeyEnum;
import com.ca.capicturebackend.model.vo.PictureVO;
import com.ca.capicturebackend.model.vo.UserVO;
import com.ca.capicturebackend.service.PictureService;
import com.ca.capicturebackend.mapper.PictureMapper;
import com.ca.capicturebackend.service.SpaceService;
import com.ca.capicturebackend.service.UserService;
import com.ca.capicturebackend.utils.ColorSimilarUtils;
import com.ca.capicturebackend.utils.ColorTransfromUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Casanova
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-02-17 17:28:28
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private CosManager cosManager;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ThreadPoolExecutor customExecutor;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 校验图片
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 上传图片
     *
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 已经改为使用注解鉴权，必须空间创建人（管理员）才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验空间额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间额度不足");
            }
        }
        Long pictureId = null;
        if (pictureUploadRequest.getId() != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 已经改为使用注解鉴权，仅本人或管理员可以编辑图片
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原有图片一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片
        // 按照该用户id划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据 inputSource 类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setOriginalUrl(uploadPictureResult.getOriginalUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setPicColor(ColorTransfromUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        Picture finalOldPicture = oldPicture;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            // oldPicture 不为空，表示更新，清理对象存储中的旧图片
            Long oldPictureSize = 0L;
            int oldPictureCount = 0;
            if (finalOldPicture != null) {
                this.clearPictureFile(finalOldPicture);
                oldPictureSize = finalOldPicture.getPicSize();
                oldPictureCount = 1;
            }
            // 如果更新的是空间内的图片，更新空间的使用额度
            if (finalSpaceId == null) {
                return true;
            }
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, finalSpaceId)
                    .setSql("totalSize = totalSize + " + picture.getPicSize() + " - " + oldPictureSize)
                    .setSql("totalCount = totalCount + " + 1 + " - " + oldPictureCount)
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
        // 只有当上传公共图库的图片时，才删除主页缓存
        if (spaceId == null) {
            String homePageCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
            cacheManager.deleteCacheByPrefix(homePageCacheKey);
        }
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取图片包装类（单条）
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 获取图片包装类（单条，有缓存）
     *
     * @param id
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVOWithCache(long id, HttpServletRequest request) {
        // 构建 key
        String queryCondition = String.valueOf(id);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
        String lockKey = CommonKeyEnum.PICTURE_LOCK_PREFIX.key("getPictureVOWithCache", hashKey);
        Picture picture = cacheManager.queryWithCache(
                cacheKey,
                lockKey,
                new TypeReference<Picture>() {
                }, // 这里动态指定类型
                () -> this.getById(id),
                300,
                120,
                TimeUnit.SECONDS
        );
        ThrowUtils.throwIf(ObjUtil.isEmpty(picture), ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            // 已经改为使用注解鉴权
            // User loginUser = userService.getLoginUser(request);
            // this.checkPictureAuth(loginUser, picture);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 获取权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        return pictureVO;
    }

    /**
     * 获取图片包装类（分页）
     *
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());
        // Map 中 key 为 userId，因为 gropingBy userId, value 是对应的用户，理论上只有一个用户对象
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 获取图片包装类（分页，有缓存）
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 构建 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", hashKey);
        String lockKey = CommonKeyEnum.PICTURE_LOCK_PREFIX.key("getPictureVOPageWithCache", hashKey);
        // 查询数据库
        Page<Picture> picturePage = cacheManager.queryWithCache(
                cacheKey,
                lockKey,
                new TypeReference<Page<Picture>>() {
                },
                () -> this.page(new Page<>(current, size), this.getQueryWrapper(pictureQueryRequest)),
                300,
                120,
                TimeUnit.SECONDS
        );
        return this.getPictureVOPage(picturePage, request);
    }

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%searchText%" or introduction like "%searchText%")
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Tag1\"%" and like "%\"Tag2\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum enumByValue = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        // 不能从通过或拒绝改回待审核
        if (id == null || reviewStatus == null || PictureReviewStatusEnum.REVIEWING.equals(enumByValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = getById(pictureReviewRequest.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 3. 校验审核状态是否重复（已经是该状态）
        ThrowUtils.throwIf(oldPicture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR, "请勿重复审核");
        // 4. 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 删除主页缓存
        String cacheKeyPrefix = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
        cacheManager.deleteCacheByPrefix(cacheKeyPrefix);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewMessage("管理员自动过审");
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        // 名称前缀默认等于搜索关键词
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "一次性最多 30 条");
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败：", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (div == null || ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        // Elements imgElementList = div.select("img.mimg");
        Elements imgElementList = div.select(".iusc");  // 修改选择器，获取包含完整数据的元素
        Integer uploadCount = 0;
        // 遍历元素，依次上传图片
        for (Element imgElement : imgElementList) {
            // String fileUrl = imgElement.attr("src");
            // 获取 data-m 属性中的JSON字符串
            String dataM = imgElement.attr("m");
            String fileUrl;
            String originPicName;
            try {
                // 解析 JSON 字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取 murl 字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }

            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片地址，防止转义或者和对象存储冲突的问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 清理对象图片文件
     *
     * @param oldPicture
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        ToDeletePictureDto toDeletePictureDto = ToDeletePictureDto.pictureToDeletePicture(oldPicture);
        List<String> toDeleteUrlList = this.pictureUrlToKey(Collections.singletonList(toDeletePictureDto));
        cosManager.deleteObjectByBatch(toDeleteUrlList);
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已经改为使用注解鉴权，校验权限
        // checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 删除对应图片缓存和首页缓存
        String queryCondition = String.valueOf(pictureId);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String pictureCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
        cacheManager.delete(pictureCacheKey);
        // 只有当删除公共图库的图片时，才删除主页缓存
        if (oldPicture.getSpaceId() == null) {
            String homePageCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
            cacheManager.deleteCacheByPrefix(homePageCacheKey);
        }
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    /**
     * 删除图片
     *
     * @param deletePictureByBatchRequest
     * @param loginUser
     */
    @Override
    public void deletePictureByBatch(DeletePictureByBatchRequest deletePictureByBatchRequest, User loginUser) {
        List<Long> pictureIdList = deletePictureByBatchRequest.getIdList();
        Long spaceId = deletePictureByBatchRequest.getSpaceId();

        // 1.参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.权限校验
        if (spaceId == null) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        } else {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
            }
        }
        // 3.图片查询
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "userId", "picSize", "url", "originalUrl", "thumbnailUrl");
        queryWrapper.in("id", pictureIdList);
        if (spaceId == null) {
            queryWrapper.isNull("spaceId");
        } else {
            queryWrapper.eq("spaceId", spaceId);
        }
        List<Picture> pictureList = this.list(queryWrapper);
        if (pictureList == null || pictureList.isEmpty()) {
            return;
        }
        // 开启事务
        transactionTemplate.execute(status -> {
            // 4.操作数据库
            boolean result = this.removeByIds(pictureIdList);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 5.释放额度
            if (spaceId == null) {
                return true;
            }
            long totalPicSize = pictureList.stream().mapToLong(Picture::getPicSize).sum();
            long totalPicCount = pictureList.size();
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, spaceId)
                    .setSql("totalSize = totalSize - " + totalPicSize)
                    .setSql("totalCount = totalCount - " + totalPicCount)
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
        for (Picture picture : pictureList) {
            // 删除对应图片缓存
            String queryCondition = String.valueOf(picture.getId());
            String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
            String pictureCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
            cacheManager.delete(pictureCacheKey);
            // 异步清理文件
            clearPictureFile(picture);
        }
        // 只有更新公共空间图库时，删除主页缓存
        if (spaceId == null) {
            String homePageCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
            cacheManager.deleteCacheByPrefix(homePageCacheKey);
        }

    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已经改为使用注解鉴权，校验权限
        // checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 删除对应图片缓存和首页缓存
        String queryCondition = String.valueOf(id);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String pictureCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
        cacheManager.delete(pictureCacheKey);
        // 只有当编辑公共图库的图片时，才删除主页缓存
        if (oldPicture.getSpaceId() == null) {
            String homePageCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
            cacheManager.deleteCacheByPrefix(homePageCacheKey);
        }
    }

    /**
     * 通过颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1.参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.权限校验
        if (spaceId == null) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        } else {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
            }
        }
        // 3.图片查询
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id");
        queryWrapper.in("id", pictureIdList);
        if (spaceId == null) {
            queryWrapper.isNull("spaceId");
        } else {
            queryWrapper.eq("spaceId", spaceId);
        }
        List<Picture> pictureList = this.list(queryWrapper);
        if (pictureList == null || pictureList.isEmpty()) {
            return;
        }
        // 4.批量更新
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
        // 5.删除对应图片缓存
        for (Long pictureId : pictureIdList) {
            String queryCondition = String.valueOf(pictureId);
            String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
            String pictureCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
            cacheManager.delete(pictureCacheKey);
        }
        // 只有更新公共空间图库时，删除主页缓存
        if (spaceId == null) {
            String homePageCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOPageWithCache", "");
            cacheManager.deleteCacheByPrefix(homePageCacheKey);
        }
    }

    /**
     * 异步批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatchAsync(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1.参数校验
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.空间权限校验
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3.图片查询
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList == null || pictureList.isEmpty()) {
            return;
        }
        // 分批处理避免长事务
        int batchSize = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < pictureList.size(); i += batchSize) {
            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));
            // 异步处理每批数据
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                batch.forEach(picture -> {
                    // 编辑分类和标签
                    if (StrUtil.isNotBlank(category)) {
                        picture.setCategory(category);
                    }
                    if (CollUtil.isNotEmpty(tags)) {
                        picture.setTags(JSONUtil.toJsonStr(tags));
                    }
                });
                // 批量重命名
                String nameRule = pictureEditByBatchRequest.getNameRule();
                fillPictureWithNameRule(pictureList, nameRule);
                boolean result = this.updateBatchById(batch);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
                }
                // 删除对应图片缓存
                batch.forEach(picture -> {
                    String queryCondition = String.valueOf(picture.getId());
                    String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
                    String pictureCacheKey = CommonKeyEnum.PICTURE_CACHE_PREFIX.key("getPictureVOWithCache", hashKey);
                    cacheManager.delete(pictureCacheKey);
                });
            }, customExecutor);
            futures.add(future);
        }
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 已经改为使用注解鉴权，权限校验
        // checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

    /**
     * 校验空间图片的权限
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 定时清理无用的图片文件（每天凌晨3点清理）
     */
    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void regularClearPictureFile() {
        List<ToDeletePictureDto> toDeletePicture = pictureMapper.getToDeletePicture();
        List<String> toDeleteUrlList = pictureUrlToKey(toDeletePicture);
        cosManager.deleteObjectByBatch(toDeleteUrlList);
    }

    /**
     * 从待删除的图片 URL 中提取出对象存储路径（key）
     *
     * @param toDeletePictureList
     * @return
     */
    @Override
    public List<String> pictureUrlToKey(List<ToDeletePictureDto> toDeletePictureList) {
        if (toDeletePictureList == null) return Collections.emptyList();
        List<String> keys = new ArrayList<>();
        for (ToDeletePictureDto toDeletePicture : toDeletePictureList) {
            if (toDeletePicture == null) {
                continue;
            }
            keys.addAll(Stream.of(toDeletePicture.getUrl(),
                            toDeletePicture.getOriginalUrl(),
                            toDeletePicture.getThumbnailUrl())
                    .filter(Objects::nonNull)
                    .map(url -> url.replaceFirst("https?://[^/]+/", ""))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList()));
        }
        return keys;
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

}
