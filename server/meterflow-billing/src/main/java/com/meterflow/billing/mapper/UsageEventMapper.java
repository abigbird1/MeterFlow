package com.meterflow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.billing.entity.UsageEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UsageEventMapper extends BaseMapper<UsageEvent> {}
