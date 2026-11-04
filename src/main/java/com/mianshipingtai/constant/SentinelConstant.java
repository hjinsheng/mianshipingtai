package com.mianshipingtai.constant;

/**
 * Sentinel 常量
 *
 *
 *
 */
public interface SentinelConstant {

    /**
     * 资源名：分页获取题目列表（封装类）
     */
    String RESOURCE_LIST_QUESTION_VO_BY_PAGE = "listQuestionVOByPage";

    /**
     * 资源名：分页获取题库列表（封装类）
     */
    String RESOURCE_LIST_QUESTION_BANK_VO_BY_PAGE = "listQuestionBankVOByPage";

    /**
     * 热点参数索引：第 0 个参数为客户端 IP
     */
    int PARAM_INDEX_REMOTE_IP = 0;

    /**
     * 资源名：用户登录
     */
    String RESOURCE_USER_LOGIN = "userLogin";

    /**
     * 资源名：用户注册
     */
    String RESOURCE_USER_REGISTER = "userRegister";

    /**
     * 资源名：用户登录（微信开放平台）
     */
    String RESOURCE_WX_OPEN_LOGIN = "wxOpenLogin";

    /**
     * 热点参数索引：第 1 个参数为登录账号
     */
    int PARAM_INDEX_USER_ACCOUNT = 1;
}