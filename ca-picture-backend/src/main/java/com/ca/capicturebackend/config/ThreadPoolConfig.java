package com.ca.capicturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor customExecutor() {
        return new ThreadPoolExecutor(
                2,              // 核心线程数：匹配 CPU 核心数
                4,              // 最大线程数：控制内存使用
                60L,            // 空闲线程存活时间：60秒
                TimeUnit.SECONDS, // 时间单位
                new LinkedBlockingQueue<>(50), // 队列容量：减小到 50
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
    }
}