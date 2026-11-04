package com.meterflow.equity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.equity.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {}
