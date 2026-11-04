package com.mianshipingtai.utils;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.mianshipingtai.common.BaseResponse;
import com.mianshipingtai.common.ErrorCode;
import com.mianshipingtai.common.ResultUtils;
import com.mianshipingtai.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sentinel 限流/熔断工具类
 * 异常语义约定：
 * 1. BlockException：限流返回 42900；熔断（DegradeException）走 degradeHandler
 * 2. BusinessException：原样透传给 GlobalExceptionHandler，保留原错误码，不计入熔断统计
 * 3. 其他异常：Tracer.trace 计入熔断统计后原样抛出，由 GlobalExceptionHandler 统一记录
 */
@Slf4j
public class SentinelUtils {

    /**
     * 被保护的业务逻辑
     */
    @FunctionalInterface
    public interface SentinelBiz<T> {
        BaseResponse<T> doBiz() throws Exception;
    }

    /**
     * 带限流/熔断保护地执行业务逻辑
     *
     * @param resource       资源名（见 SentinelConstant）
     * @param hotParams      热点参数列表（无热点参数规则时传空集合）
     * @param biz            业务逻辑
     * @param degradeHandler 熔断降级处理，未配置熔断规则时可传 null
     * @return 业务响应或限流/降级响应
     */
    public static <T> BaseResponse<T> protect(String resource, List<Object> hotParams,
                                              SentinelBiz<T> biz,
                                              Supplier<BaseResponse<T>> degradeHandler) throws Exception {
        Object[] params = (hotParams == null) ? new Object[0] : hotParams.toArray();
        // 1. 限流/熔断检查（BlockException 只可能来自这里）
        Entry entry;
        try {
            entry = SphU.entry(resource, EntryType.IN, 1, params);
        } catch (BlockException ex) {
            if (ex instanceof DegradeException) {
                log.warn("Sentinel 熔断触发, resource: {}, hotParams: {}", resource, hotParams);
                if (degradeHandler != null) {
                    return degradeHandler.get();
                }
                return ResultUtils.error(ErrorCode.TOO_MANY_REQUESTS, "服务繁忙，请稍后再试");
            }
            log.warn("Sentinel 限流触发, resource: {}, hotParams: {}, blockType: {}",
                    resource, hotParams, ex.getClass().getSimpleName());
            return ResultUtils.error(ErrorCode.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        }
        // 2. 执行业务逻辑
        try {
            return biz.doBiz();
        } catch (BusinessException e) {
            // 业务异常：保留原错误码透出，不计入熔断统计
            throw e;
        } catch (Exception e) {
            // 系统异常：计入熔断统计后抛出，由 GlobalExceptionHandler 统一记录并返回
            Tracer.trace(e);
            throw e;
        } finally {
            // exit 参数必须与 entry 严格一致
            entry.exit(1, params);
        }
    }
}