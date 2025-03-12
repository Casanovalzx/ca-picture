package com.ca.capicturebackend.manager;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.ca.capicturebackend.exception.BusinessException;
import com.ca.capicturebackend.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CacheManager {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final String EMPTY_VALUE = "EMPTY_VALUE";

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
     * @param typeReference   查询数据类型的 class 对象
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
            TypeReference<T> typeReference,  // 改为 TypeReference<T> 以支持泛型
            Supplier<T> dbQueryFunction,  // 传入真正查数据库的函数
            long normalTtl,
            long emptyTtl,
            TimeUnit timeUnit) {

        // 1. 先查本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            if (EMPTY_VALUE.equals(cachedValue)) {
                return getEmptyObject(typeReference);
            }
            return JSONUtil.toBean(cachedValue, typeReference, false);
        }

        // 2. 本地缓存未命中，再查Redis
        ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            if (EMPTY_VALUE.equals(cachedValue)) {
                return getEmptyObject(typeReference);
            }
            LOCAL_CACHE.put(cacheKey, cachedValue);
            return JSONUtil.toBean(cachedValue, typeReference, false);
        }

        // 3. 缓存未命中，尝试获取分布式锁
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired;
        try {
            lockAcquired = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if (lockAcquired) {
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
                    opsForValue.set(cacheKey, EMPTY_VALUE, emptyTtl, timeUnit);
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                }
                return dbData;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
        } catch (InterruptedException e) {
            log.warn("Redisson获取锁被中断", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 删除本地缓存和 Redis 缓存
     *
     * @param cacheKey
     */
    public void delete(String cacheKey) {
        LOCAL_CACHE.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
    }

    /**
     * 异步删除本地缓存和 Redis 缓存
     *
     * @param cacheKey
     */
    @Async
    public void asyncDelayedDelete(String cacheKey) {
        try {
            Thread.sleep(500); // 缩短到 500ms
            LOCAL_CACHE.invalidate(cacheKey);
            redisTemplate.delete(cacheKey);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 批量删除符合前缀的 Key 的缓存
     *
     * @param prefix
     */
    public void deleteRedisCacheByPrefix(String prefix) {
        try {
            // 使用 SCAN 命令以提高性能，适合大数据量场景
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(1000)
                    .build();

            Set<String> keysToDelete = new HashSet<>();
            Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection().scan(scanOptions);

            while (cursor.hasNext()) {
                byte[] key = cursor.next();
                String keyStr = new String(key, StandardCharsets.UTF_8);
                if (keyStr.startsWith(prefix)) {
                    keysToDelete.add(keyStr);
                }
            }

            try {
                cursor.close();
            } catch (Exception e) {
                log.warn("关闭 SCAN 游标失败", e);
            }

            // 批量删除
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.error("批量删除 Redis 缓存失败，前缀: {}", prefix, e);
        }
    }

    public void deleteLocalCacheByPrefix(String prefix) {
        // 获取所有键
        Set<String> keys = LOCAL_CACHE.asMap().keySet();

        // 过滤出匹配前缀的键
        List<String> keysToDelete = keys.stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList());

        // 批量删除
        LOCAL_CACHE.invalidateAll(keysToDelete);
    }

    private <T> T getEmptyObject(TypeReference<T> typeReference) {
        try {
            Class<T> clazz = (Class<T>) ((ParameterizedType) typeReference.getType()).getRawType();
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("创建空对象失败", e);
            return null; // 兜底策略
        }
    }

}
