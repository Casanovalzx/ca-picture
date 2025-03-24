package com.ca.capicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ca.capicturebackend.model.dto.space.SpaceAddRequest;
import com.ca.capicturebackend.model.dto.space.SpaceQueryRequest;
import com.ca.capicturebackend.model.entity.Space;
import com.ca.capicturebackend.model.entity.User;
import com.ca.capicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author Casanova
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-03-04 13:01:10
*/
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 删除空间
     *
     * @param spaceId
     * @param loginUser
     */
    void deleteSpace(long spaceId, User loginUser);

    /**
     * 校验空间
     *
     * @param space
     * @param add 是否为创建
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类（单条）
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间级别填充对象
     *
     * @param space
     */
    public void fillSpaceBySpaceLevel(Space space);

    void checkSpaceAuth(Space space, User loginUser);
}
