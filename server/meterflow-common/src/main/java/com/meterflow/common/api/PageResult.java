package com.meterflow.common.api;

import java.util.List;

/** 分页响应（meterflow.md #7.9）：data = {list, total, pageNum, pageSize} */
public record PageResult<T>(List<T> list, long total, int pageNum, int pageSize) {}
