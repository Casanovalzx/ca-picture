package com.ca.capicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ca.capicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ca.capicturebackend.model.dto.picture.*;
import com.ca.capicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ca.capicturebackend.model.entity.User;
import com.ca.capicturebackend.model.vo.PictureVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Casanova
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-02-17 17:28:29
 */
public interface PictureService extends IService<Picture> {

    /**
     * 校验图片
     *
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 上传图片
     *
     * @param inputSource          文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取图片包装类（单条）
     *
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取图片包装类（单条，有缓存）
     *
     * @param id
     * @param request
     * @return
     */
    PictureVO getPictureVOWithCache(long id, HttpServletRequest request);

    /**
     * 获取图片包装类（分页）
     *
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 获取图片包装类（分页，有缓存）
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功抓取的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                 User loginUser);

    /**
     * 清理图片文件
     *
     * @param oldPicture
     */
    @Async
    void clearPictureFile(Picture oldPicture);

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 批量删除图片
     *
     * @param deletePictureByBatchRequest
     * @param loginUser
     */
    void deletePictureByBatch(DeletePictureByBatchRequest deletePictureByBatchRequest, User loginUser);

    /**
     * 编辑图片
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 通过颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    void editPictureByBatch(@Param("pictureEditByBatchRequest") PictureEditByBatchRequest pictureEditByBatchRequest, @Param("loginUser") User loginUser);

    /**
     * 异步批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    void editPictureByBatchAsync(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 创建扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

    /**
     * 校验空间图片的权限（已改为注解鉴权）
     *
     * @param loginUser
     * @param picture
     */
    @Deprecated
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 定时清理无用的图片文件（每天凌晨3点清理）
     */
    void regularClearPictureFile();

    /**
     * 将待删除图片列表中的所有 URL 转换为 KEY
     *
     * @param toDeletePictureList
     * @return
     */
    List<String> pictureUrlToKey(List<ToDeletePictureDto> toDeletePictureList);
}
