package com.meterflow.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.finance.entity.Bill;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {}
