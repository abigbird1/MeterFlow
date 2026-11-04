package com.meterflow.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.platform.entity.PlatformUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformUserMapper extends BaseMapper<PlatformUser> {}
