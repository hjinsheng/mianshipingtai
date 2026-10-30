package com.mianshipingtai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mianshipingtai.model.entity.UserSignIn;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserSignInMapper extends BaseMapper<UserSignIn> {

    /**
     * 批量插入签到记录（重复则忽略）
     */
    void batchInsert(@Param("list") List<UserSignIn> list);
}
