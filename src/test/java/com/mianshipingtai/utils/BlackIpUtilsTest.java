package com.mianshipingtai.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackIpUtilsTest {

    @AfterEach
    void tearDown() {
        // 清理静态状态，避免用例之间相互影响
        BlackIpUtils.rebuildBlackIp("blackIpList: []");
    }

    @Test
    void shouldRecognizeBlackIp() {
        String config = "blackIpList:\n  - 1.2.3.4\n  - 5.6.7.8\n";
        assertTrue(BlackIpUtils.rebuildBlackIp(config));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));
        assertTrue(BlackIpUtils.isBlackIp("5.6.7.8"));
        assertFalse(BlackIpUtils.isBlackIp("9.9.9.9"));
        assertFalse(BlackIpUtils.isBlackIp(null));
        assertFalse(BlackIpUtils.isBlackIp(""));
    }

    @Test
    void shouldClearBlackListWithExplicitEmptyList() {
        assertTrue(BlackIpUtils.rebuildBlackIp("blackIpList:\n  - 1.2.3.4\n"));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));

        assertTrue(BlackIpUtils.rebuildBlackIp("blackIpList: []"));
        assertFalse(BlackIpUtils.isBlackIp("1.2.3.4"));
    }

    @Test
    void shouldKeepOldBlackListWhenConfigInvalid() {
        assertTrue(BlackIpUtils.rebuildBlackIp("blackIpList:\n  - 1.2.3.4\n"));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));

        // 非法 YAML
        assertFalse(BlackIpUtils.rebuildBlackIp("blackIpList: ["));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));

        // 空白配置
        assertFalse(BlackIpUtils.rebuildBlackIp("   "));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));

        // 缺少 blackIpList 字段
        assertFalse(BlackIpUtils.rebuildBlackIp("otherKey: 1\n"));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));

        // blackIpList 类型错误
        assertFalse(BlackIpUtils.rebuildBlackIp("blackIpList: not-a-list\n"));
        assertTrue(BlackIpUtils.isBlackIp("1.2.3.4"));
    }

    @Test
    void shouldNotThrowOnEmptyFilter() {
        assertTrue(BlackIpUtils.rebuildBlackIp("blackIpList: []"));
        assertFalse(BlackIpUtils.isBlackIp("1.2.3.4"));
        assertFalse(BlackIpUtils.isBlackIp(null));
    }
}
