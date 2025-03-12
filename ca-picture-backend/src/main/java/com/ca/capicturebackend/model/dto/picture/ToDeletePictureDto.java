package com.ca.capicturebackend.model.dto.picture;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.ca.capicturebackend.model.entity.Picture;
import com.ca.capicturebackend.model.vo.PictureVO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;

@Data
public class ToDeletePictureDto implements Serializable {

    /**
     * 图片 url
     */
    private String url;

    /**
     * 原始图片 url
     */
    private String originalUrl;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 实体转换为待删除删除图片对象
     *
     * @param picture
     * @return
     */
    public static ToDeletePictureDto pictureToDeletePicture(Picture picture) {
        if (picture == null) {
            return null;
        }
        ToDeletePictureDto toDeletePictureDto = new ToDeletePictureDto();
        BeanUtils.copyProperties(picture, toDeletePictureDto);
        // 类型不同，需要转换
        return toDeletePictureDto;
    }
}
