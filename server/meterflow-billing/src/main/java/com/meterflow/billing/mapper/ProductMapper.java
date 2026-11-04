package com.meterflow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.billing.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {}
