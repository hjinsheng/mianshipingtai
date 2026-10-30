package com.mianshipingtai.job.cycle;

import com.mianshipingtai.constant.RedisConstant;
import com.mianshipingtai.model.entity.UserSignIn;
import com.mianshipingtai.mapper.UserSignInMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;

// todo 取消注释开启任务
//@Component
@Slf4j
public class MonthlySyncSignInToMysql {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserSignInMapper userSignInMapper;

    /**
     * 每月 1 日凌晨 2 点，将上月 Redis 签到数据同步到 MySQL
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void run() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate monthStart = lastMonth.atDay(1);
        LocalDate monthEnd   = lastMonth.atEndOfMonth();
        int year = lastMonth.getYear();

        log.info("MonthlySyncSignInToMysql 开始，同步 {} 年 {} 月 的数据", year, lastMonth.getMonthValue());

        // 扫描上月所有用户的 Redis 签到 key
        RKeys rKeys = redissonClient.getKeys();
        String pattern = RedisConstant.getUserSignInRedisKey(year, -1).replace(":-1", ":*");
        // pattern 结果：user:signins:{year}:*
        Iterable<String> keys = rKeys.getKeysByPattern(pattern);

        int totalUsers = 0;
        int totalRecords = 0;

        for (String key : keys) {
            // key 格式：user:signins:{year}:{userId}
            String[] parts = key.split(":");
            long userId = Long.parseLong(parts[parts.length - 1]);

            RBitSet rBitSet = redissonClient.getBitSet(key);
            if (rBitSet == null || rBitSet.cardinality() == 0) {
                continue;
            }

            // 提取上月范围内的签到位，构造记录
            List<UserSignIn> monthRecords = new ArrayList<>();
            for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
                int dayOfYear = date.getDayOfYear();
                if (rBitSet.get(dayOfYear)) {
                    UserSignIn record = new UserSignIn();
                    record.setUserId(userId);
                    record.setSignInDate(date);
                    record.setYear(year);
                    record.setCreateTime(new Date());
                    monthRecords.add(record);
                }
            }

            if (!monthRecords.isEmpty()) {
                userSignInMapper.batchInsert(monthRecords);
                totalUsers++;
                totalRecords += monthRecords.size();
            }
        }

        log.info("MonthlySyncSignInToMysql 完成，共同步 {} 个用户，{} 条记录", totalUsers, totalRecords);
    }
}