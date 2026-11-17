package com.mianshipingtai.blackfilter;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.mianshipingtai.utils.BlackIpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class NacosListener {

    @NacosInjected
    private ConfigService configService;

    @Value("${nacos.config.data-id:mianshi}")
    private String dataId;

    @Value("${nacos.config.group:DEFAULT_GROUP}")
    private String group;

    @Value("${blacklist.nacos-timeout-ms:3000}")
    private long timeoutMs;

    @Value("${blacklist.snapshot-path:blacklist/blackIpList.yml}")
    private String snapshotPath;

    @Value("${blacklist.retry-interval-ms:30000}")
    private long retryIntervalMs;

    /**
     * 是否已注册监听器，避免重复注册
     */
    private final AtomicBoolean listenerRegistered = new AtomicBoolean(false);

    /**
     * 是否已成功拉取过一次配置；成功后定时任务不再产生额外 Nacos 调用
     */
    private final AtomicBoolean initialConfigLoaded = new AtomicBoolean(false);

    private final Listener listener = new Listener() {
        /**
         * 返回 null 表示使用 Nacos 默认的通知线程池
         */
        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            try {
                applyConfig(configInfo, "nacos-push");
            } catch (Exception e) {
                // 监听线程内的异常不能让 Nacos 的调度线程中断
                log.error("处理 Nacos 黑名单变更失败，保留当前黑名单", e);
            }
        }
    };

    /**
     * 启动时优先加载本地快照，不发起任何 Nacos 网络调用，保证应用启动不受 Nacos 可用性影响。
     */
    @PostConstruct
    public void init() {
        log.info("黑名单初始化开始，优先读取本地快照：{}", snapshotPath);
        String snapshot = BlackIpSnapshotStore.load(snapshotPath);
        if (snapshot != null) {
            applyConfig(snapshot, "local-snapshot");
        } else {
            log.warn("未找到可用的本地黑名单快照，将以空黑名单启动（fail-open），等待 Nacos 同步");
        }
    }

    /**
     * 定时确保监听器已注册且已成功拉取过一次配置。
     * 订阅与拉取成功后直接返回；Nacos 不可用时只记录告警，不影响应用运行。
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${blacklist.retry-interval-ms:30000}")
    public void syncFromNacos() {
        if (listenerRegistered.get() && initialConfigLoaded.get()) {
            return;
        }
        ConfigService service = configService;
        if (service == null) {
            log.warn("ConfigService 未注入，跳过本次 Nacos 黑名单同步");
            return;
        }

        if (!listenerRegistered.get()) {
            try {
                service.addListener(dataId, group, listener);
                listenerRegistered.set(true);
                log.info("Nacos 黑名单监听器注册成功，dataId={}, group={}", dataId, group);
            } catch (Exception e) {
                // 注册失败也继续尝试拉取一次，部分场景下 getConfig 仍然可用
                log.warn("注册 Nacos 黑名单监听器失败，{} 毫秒后重试：{}", retryIntervalMs, e.getMessage());
            }
        }

        if (!initialConfigLoaded.get()) {
            try {
                String config = service.getConfig(dataId, group, timeoutMs);
                if (config == null) {
                    if (listenerRegistered.get()) {
                        log.warn("Nacos 中暂无黑名单配置，监听器已注册，后续发布将自动生效，dataId={}, group={}", dataId, group);
                        initialConfigLoaded.set(true);
                    } else {
                        log.warn("Nacos 中暂无黑名单配置，且监听器尚未注册，{} 毫秒后重试", retryIntervalMs);
                    }
                    return;
                }
                if (applyConfig(config, "nacos-pull")) {
                    initialConfigLoaded.set(true);
                }
            } catch (Exception e) {
                log.warn("拉取 Nacos 黑名单配置失败，保留当前黑名单，{} 毫秒后重试：{}", retryIntervalMs, e.getMessage());
            }
        }
    }

    /**
     * 应用配置：只有解析成功才更新内存黑名单并落本地快照；
     * 解析失败时保留上一份有效黑名单。
     */
    private synchronized boolean applyConfig(String configInfo, String source) {
        if (!BlackIpUtils.rebuildBlackIp(configInfo)) {
            log.warn("黑名单配置未生效，来源={}，保留当前黑名单（{} 条）", source, BlackIpUtils.getBlackIpCount());
            return false;
        }
        // 本地快照本身不需要重复落盘
        if (!"local-snapshot".equals(source)) {
            BlackIpSnapshotStore.save(snapshotPath, configInfo);
        }
        log.info("黑名单已更新，来源={}，共 {} 条", source, BlackIpUtils.getBlackIpCount());
        return true;
    }
}
