package com.mianshipingtai.mapper;

import com.mianshipingtai.model.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
* @author 黄金盛
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2026-10-23 19:59:55
* @Entity com.mianshipingtai.model.entity.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 查询题目列表（包括已删除的数据）
     */
    @Select("select * from question where updateTime >= #{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date minUpdateTime);
}




