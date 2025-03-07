package com.ca.capicturebackend.model.enums;

import java.util.StringJoiner;

public enum CommonKeyEnum {

    // 示例前缀定义
    PICTURE_LOCK_PREFIX("capicture:picture:lock:"),
    PICTURE_CACHE_PREFIX("capicture:picture:"),
    SPACE_LOCK_PREFIX("capicture:picture:lock");      // 用户缓存

    private final String prefix;

    CommonKeyEnum(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 生成完整的key (支持多个业务参数拼接)
     */
    public String key(String... parts) {
        StringJoiner joiner = new StringJoiner(":");
        joiner.add(prefix.substring(0, prefix.length() - 1)); // 去掉前缀末尾的冒号，后续自己拼
        for (String part : parts) {
            joiner.add(part);
        }
        return joiner.toString();
    }
}
