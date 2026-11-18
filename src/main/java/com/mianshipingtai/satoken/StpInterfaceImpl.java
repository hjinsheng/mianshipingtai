package com.mianshipingtai.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.mianshipingtai.model.entity.User;
import com.mianshipingtai.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mianshipingtai.constant.UserConstant.USER_LOGIN_STATE;

@Component // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserService userService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String s) {
        return new ArrayList<>();
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String s) {
        // 优先从 Sa-Token Session 缓存中获取登录用户信息
        User user = null;
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session != null) {
            Object userObj = session.get(USER_LOGIN_STATE);
            if (userObj instanceof User) {
                user = (User) userObj;
            }
        }
        // 缓存缺失时降级查库（如微信登录、历史会话）
        if (user == null) {
            user = userService.getById(Long.valueOf(loginId.toString()));
        }
        // 用户不存在时返回空角色集合，避免 NPE
        if (user == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(user.getUserRole());
    }
}
