package com.mianshipingtai.constant;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public interface RedisConstant {

    /**
     * 用户签到记录的 Redis Key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * 获取用户签到记录的 Redis Key
     * @param year 年份
     * @param userId 用户 id
     * @return 拼接好的 Redis Key
     */
    static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }

    /**
     * 计算签到 Redis Key 的 TTL（当前日期到年底的剩余天数 + 2 天缓冲）
     */
    static long getSignInKeyTTLDays() {
        LocalDate now = LocalDate.now();
        LocalDate endOfYear = LocalDate.of(now.getYear(), 12, 31);
        return ChronoUnit.DAYS.between(now, endOfYear) + 1 + 2;
    }
}
