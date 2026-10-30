package com.mianshipingtai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mianshipingtai.mapper.UserSignInMapper;
import com.mianshipingtai.model.entity.UserSignIn;
import com.mianshipingtai.service.UserSignInService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserSignInServiceImpl extends ServiceImpl<UserSignInMapper, UserSignIn>
        implements UserSignInService {

    @Override
    public List<Integer> getSignInDaysOfYear(long userId, int year) {
        List<UserSignIn> signInList = this.list(
                new QueryWrapper<UserSignIn>()
                        .eq("userId", userId)
                        .eq("year", year)
        );
        return signInList.stream()
                .map(signIn -> signIn.getSignInDate().getDayOfYear())
                .sorted()
                .collect(Collectors.toList());
    }
}