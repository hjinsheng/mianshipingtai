package com.mianshipingtai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mianshipingtai.model.entity.UserSignIn;
import java.util.List;

public interface UserSignInService extends IService<UserSignIn> {

    /**
     * 获取某用户某年的签到日列表（dayOfYear，已排序）
     */
    List<Integer> getSignInDaysOfYear(long userId, int year);
}