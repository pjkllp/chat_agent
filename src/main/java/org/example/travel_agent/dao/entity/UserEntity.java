package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

/**
 * 用户表实体。
 * <p>若实际库表名不同，请修改 {@link TableName#value()}。</p>
 */
@Data
@TableName("t_user")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String password;

    private String email;

    @TableField("create_time")
    private Date createTime;

    /**
     * 是否为管理员：0 否，1 是。
     */
    @TableField("is_admin")
    private Integer isAdmin;
}
