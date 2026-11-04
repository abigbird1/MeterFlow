package com.meterflow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meterflow.billing.entity.DailySnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailySnapshotMapper extends BaseMapper<DailySnapshot> {}
