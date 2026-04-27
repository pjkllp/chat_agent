package org.example.travel_agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.travel_agent.dao.entity.UserEntity;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
