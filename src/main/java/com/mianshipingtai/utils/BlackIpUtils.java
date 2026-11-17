package com.mianshipingtai.utils;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class BlackIpUtils {

    /**
     * IP 黑名单过滤器。
     * volatile 保证监听线程重建后，业务线程能及时看到新的过滤器引用。
     * 启动时即创建空过滤器，避免未初始化时 isBlackIp 抛 NPE。
     */
    private static volatile BitMapBloomFilter bloomFilter = new BitMapBloomFilter(100);

    /**
     * 当前黑名单条数，仅用于日志与排查
     */
    private static volatile int blackIpCount = 0;

    private BlackIpUtils() {
    }

    /**
     * 判断 ip 是否在黑名单内
     */
    public static boolean isBlackIp(String ip) {
        BitMapBloomFilter filter = bloomFilter;
        return StrUtil.isNotBlank(ip) && filter != null && filter.contains(ip);
    }

    /**
     * 重建 IP 黑名单。
     * 无论配置内容是否合法都不会向外抛异常：解析失败时保留上一份有效黑名单。
     *
     * @param configInfo Nacos 中的原始配置内容
     * @return 是否成功应用
     */
    public static synchronized boolean rebuildBlackIp(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
            log.warn("黑名单配置为空，保留当前黑名单（{} 条）", blackIpCount);
            return false;
        }

        Map<?, ?> map;
        try {
            Yaml yaml = new Yaml();
            map = yaml.loadAs(configInfo, Map.class);
        } catch (Exception e) {
            log.error("黑名单配置解析失败，保留当前黑名单（{} 条）", blackIpCount, e);
            return false;
        }

        if (map == null || !map.containsKey("blackIpList")) {
            log.warn("黑名单配置缺少 blackIpList 字段，保留当前黑名单（{} 条）", blackIpCount);
            return false;
        }

        Object rawList = map.get("blackIpList");
        if (rawList != null && !(rawList instanceof List)) {
            log.error("黑名单配置 blackIpList 不是列表，保留当前黑名单（{} 条）", blackIpCount);
            return false;
        }

        List<String> blackIpList = new ArrayList<>();
        if (rawList != null) {
            for (Object item : (List<?>) rawList) {
                if (item != null && StrUtil.isNotBlank(item.toString())) {
                    blackIpList.add(item.toString().trim());
                }
            }
        }

        // 位数组大小按黑名单数量计算，约 1% 误判率
        BitMapBloomFilter newFilter = new BitMapBloomFilter(Math.max(1024, blackIpList.size() * 10));
        for (String ip : blackIpList) {
            newFilter.add(ip);
        }
        // 一次性替换引用，读线程只会看到旧或新的完整过滤器
        bloomFilter = newFilter;
        blackIpCount = blackIpList.size();
        log.info("黑名单重建完成，共 {} 条", blackIpCount);
        return true;
    }

    /**
     * 当前黑名单条数
     */
    public static int getBlackIpCount() {
        return blackIpCount;
    }
}
