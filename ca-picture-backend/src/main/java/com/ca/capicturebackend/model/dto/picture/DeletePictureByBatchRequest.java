package com.ca.capicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量删除请求
 */
@Data
public class DeletePictureByBatchRequest implements Serializable {

    /**
     * id 列表
     */
    private List<Long> idList;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}

