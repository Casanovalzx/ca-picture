package com.ca.capicturebackend.model.dto.picture;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

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
}
