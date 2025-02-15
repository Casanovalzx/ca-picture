package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1881670318914633141L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

}
