package com.mianshipingtai.sentinel;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.TypeReference;
import com.alibaba.csp.sentinel.datasource.*;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.mianshipingtai.constant.SentinelConstant.PARAM_INDEX_REMOTE_IP;
import static com.mianshipingtai.constant.SentinelConstant.PARAM_INDEX_USER_ACCOUNT;
import static com.mianshipingtai.constant.SentinelConstant.RESOURCE_LIST_QUESTION_VO_BY_PAGE;
import static com.mianshipingtai.constant.SentinelConstant.RESOURCE_USER_LOGIN;
import static com.mianshipingtai.constant.SentinelConstant.RESOURCE_USER_REGISTER;
import static com.mianshipingtai.constant.SentinelConstant.RESOURCE_WX_OPEN_LOGIN;

@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() throws Exception {
        initFlowRules();
        initDegradeRules();
        listenRules();
    }

    // 限流规则
    public void initFlowRules() {
        // 单 IP 查看题目列表限流规则
        ParamFlowRule questionIpRule = new ParamFlowRule(RESOURCE_LIST_QUESTION_VO_BY_PAGE)
                .setParamIdx(PARAM_INDEX_REMOTE_IP) // 对第 0 个参数限流，即 IP 地址
                .setCount(60) // 每分钟最多 60 次
                .setDurationInSec(60); // 规则的统计周期为 60 秒

        // 登录-单 IP 限流规则：防密码喷洒，每分钟最多 10 次
        ParamFlowRule loginIpRule = new ParamFlowRule(RESOURCE_USER_LOGIN)
                .setParamIdx(PARAM_INDEX_REMOTE_IP)
                .setCount(10)
                .setDurationInSec(60);

        // 登录-单账号限流规则：防定向爆破，每分钟最多 5 次
        ParamFlowRule loginAccountRule = new ParamFlowRule(RESOURCE_USER_LOGIN)
                .setParamIdx(PARAM_INDEX_USER_ACCOUNT)
                .setCount(5)
                .setDurationInSec(60);

        // 注册-单 IP 限流规则：防恶意批量注册，每分钟最多 3 次
        ParamFlowRule registerIpRule = new ParamFlowRule(RESOURCE_USER_REGISTER)
                .setParamIdx(PARAM_INDEX_REMOTE_IP)
                .setCount(3)
                .setDurationInSec(60);

        ParamFlowRuleManager.loadRules(Arrays.asList(questionIpRule, loginIpRule, loginAccountRule, registerIpRule));

        // 全局 QPS 流控规则：注册接口洪峰兜底，保护 user 表写入链路
        FlowRule registerQpsRule = new FlowRule(RESOURCE_USER_REGISTER)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(50);
        FlowRuleManager.loadRules(Collections.singletonList(registerQpsRule));
    }

    // 降级规则
    public void initDegradeRules() {
        // 单 IP 查看题目列表熔断规则
        DegradeRule slowCallRule = new DegradeRule(RESOURCE_LIST_QUESTION_VO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 慢调用比例大于 20%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10) // 最小请求数
                .setSlowRatioThreshold(3000); // 响应时间超过 3 秒（单位为毫秒）

        DegradeRule errorRateRule = new DegradeRule(RESOURCE_LIST_QUESTION_VO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1) // 异常率大于 10%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10); // 最小请求数

        // 微信登录熔断规则（外部依赖）：慢调用比例，超过 2 秒算慢调用
        DegradeRule wxSlowCallRule = new DegradeRule(RESOURCE_WX_OPEN_LOGIN)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.5) // 慢调用比例大于 50%
                .setTimeWindow(30) // 熔断持续时间 30 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(3) // 最小请求数（微信登录流量低，门槛设小才能触发）
                .setSlowRatioThreshold(2000); // 响应时间超过 2 秒

        // 微信登录熔断规则（外部依赖）：异常比例
        DegradeRule wxErrorRule = new DegradeRule(RESOURCE_WX_OPEN_LOGIN)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.5) // 异常率大于 50%
                .setTimeWindow(30) // 熔断持续时间 30 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(3); // 最小请求数

        // 加载规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, errorRateRule, wxSlowCallRule, wxErrorRule));
    }

    /**
     * 持久化配置为本地文件
     */
    public void listenRules() throws Exception {
        // 获取项目根目录
        String rootPath = System.getProperty("user.dir");
        // sentinel 目录路径
        File sentinelDir = new File(rootPath, "sentinel");
        // 目录不存在则创建
        if (!FileUtil.exist(sentinelDir)) {
            FileUtil.mkdir(sentinelDir);
        }
        // 规则文件路径
        String flowRulePath = new File(sentinelDir, "FlowRule.json").getAbsolutePath();
        String degradeRulePath = new File(sentinelDir, "DegradeRule.json").getAbsolutePath();

        // Data source for FlowRule
        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new FileRefreshableDataSource<>(flowRulePath, flowRuleListParser);
        // Register to flow rule manager.
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
        WritableDataSource<List<FlowRule>> flowWds = new FileWritableDataSource<>(flowRulePath, this::encodeJson);
        // Register to writable data source registry so that rules can be updated to file
        WritableDataSourceRegistry.registerFlowDataSource(flowWds);

        // Data source for DegradeRule
        FileRefreshableDataSource<List<DegradeRule>> degradeRuleDataSource
                = new FileRefreshableDataSource<>(
                degradeRulePath, degradeRuleListParser);
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());
        WritableDataSource<List<DegradeRule>> degradeWds = new FileWritableDataSource<>(degradeRulePath, this::encodeJson);
        // Register to writable data source registry so that rules can be updated to file
        WritableDataSourceRegistry.registerDegradeDataSource(degradeWds);
    }

    private Converter<String, List<FlowRule>> flowRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<FlowRule>>() {
            });
    private Converter<String, List<DegradeRule>> degradeRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<DegradeRule>>() {
            });

    private <T> String encodeJson(T t) {
        return JSON.toJSONString(t);
    }

}
