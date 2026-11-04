package com.meterflow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.billing.entity.AccountTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountTransactionMapper extends BaseMapper<AccountTransaction> {}
