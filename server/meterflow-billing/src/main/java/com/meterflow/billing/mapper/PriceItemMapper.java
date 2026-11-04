package com.meterflow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.billing.entity.PriceItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PriceItemMapper extends BaseMapper<PriceItem> {}
