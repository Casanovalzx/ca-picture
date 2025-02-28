package com.ca.capicturebackend.manager;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ca.capicturebackend.exception.BusinessException;
import com.ca.capicturebackend.exception.ErrorCode;
import com.ca.capicturebackend.model.entity.Picture;
import com.ca.capicturebackend.model.enums.CommonKeyEnum;
import com.ca.capicturebackend.model.vo.PictureVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.K;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class CacheManager {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L)
            // 缓存 5 分钟移除
            .expireAfterWrite(5L, TimeUnit.MINUTES)
            .build();

    /**
     * 使用缓存查询数据
     *
     * @param cacheKey        缓存中的 Key
     * @param lockKey         互斥锁的 Key
     * @param clazz           查询数据类型的 class 对象
     * @param dbQueryFunction 查询数据库的函数
     * @param normalTtl       正常数据的缓存时间
     * @param emptyTtl        数据库中不存在的数据的缓存时间
     * @param timeUnit        缓存时间单位
     * @param <T>             查询的数据类型
     * @return 查询的数据
     */
    public <T> T queryWithCache(
            String cacheKey,
            String lockKey,
            Class<T> clazz,
            Supplier<T> dbQueryFunction,  // 传入真正查数据库的函数
            long normalTtl,
            long emptyTtl,
            TimeUnit timeUnit) {

        // 1. 先查本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            return JSONUtil.toBean(cachedValue, clazz);
        }

        // 2. 本地缓存未命中，再查Redis
        ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            LOCAL_CACHE.put(cacheKey, cachedValue);
            return JSONUtil.toBean(cachedValue, clazz);
        }

        // 3. 缓存未命中，尝试获取分布式锁
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired;
        try {
            lockAcquired = lock.tryLock(10, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Redisson获取锁被中断", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
        }

        if (!lockAcquired) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询繁忙，请稍后再试");
        }

        try {
            // 4. 查询数据库
            T dbData = dbQueryFunction.get();
            String cacheValue = JSONUtil.toJsonStr(dbData);
            int expireTime = Convert.toInt(RandomUtil.randomFloat(1, 2) * normalTtl);

            if (dbData != null) {
                // 有数据，正常缓存
                LOCAL_CACHE.put(cacheKey, cacheValue);
                opsForValue.set(cacheKey, cacheValue, expireTime, timeUnit);
            } else {
                // 无数据，缓存空值
                opsForValue.set(cacheKey, cacheValue, emptyTtl, timeUnit);
            }

            return dbData;
        } finally {
            lock.unlock();
        }
    }
}
