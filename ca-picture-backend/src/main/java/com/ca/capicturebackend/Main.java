package com.ca.capicturebackend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ca.capicturebackend.model.entity.User;

public class Main {
    public static void main(String[] args) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getUserAccount, "asd");
        System.out.println(queryWrapper);
    }

}
