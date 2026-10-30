package com.mianshipingtai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import lombok.Data;

@TableName(value = "user_sign_in")
@Data
public class UserSignIn implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private LocalDate signInDate;

    private Integer year;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
