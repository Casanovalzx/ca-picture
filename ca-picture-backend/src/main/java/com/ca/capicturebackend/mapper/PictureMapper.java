package com.ca.capicturebackend.mapper;

import com.ca.capicturebackend.model.dto.picture.ToDeletePictureDto;
import com.ca.capicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author Casanova
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-02-17 17:28:29
* @Entity com.ca.cacturebackend.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    List<ToDeletePictureDto> getToDeletePicture();
}




