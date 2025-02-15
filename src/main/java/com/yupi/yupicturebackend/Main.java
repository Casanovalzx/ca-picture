package com.yupi.yupicturebackend;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupicturebackend.model.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getUserAccount, "asd");
        System.out.println(queryWrapper);
    }

}
