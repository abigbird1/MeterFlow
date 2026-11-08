package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meterflow.billing.entity.ExportTask;
import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.mapper.ExportTaskMapper;
import com.meterflow.billing.mapper.UsageEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 异步导出（AF2）：任务落库 → 生成 CSV（骨架阶段同步生成，标注由 XXL-Job 承接）→ 下载中心 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final UsageEventMapper usageEventMapper;

    @Value("${meterflow.export-dir:../deploy/data/exports}")
    private String exportDir;

    public ExportTask create(Long tenantId, String taskType, String paramsJson) {
        ExportTask t = new ExportTask();
        t.setTenantId(tenantId); t.setTaskType(taskType); t.setParams(paramsJson);
        t.setStatus("processing");
        exportTaskMapper.insert(t);
        generate(t);
        return t;
    }

    /** 骨架阶段同步生成（真实实现由 XXL-Job 异步执行，接口契约不变）；params 中 startDate/endDate 生效为时间过滤 */
    private void generate(ExportTask t) {
        try {
            var qw = new LambdaQueryWrapper<UsageEvent>()
                    .eq(UsageEvent::getTenantId, t.getTenantId());
            // params JSON 中的 startDate/endDate 作为时间过滤（报表按月导出）
            try {
                if (t.getParams() != null && !t.getParams().isBlank()) {
                    Map<String, Object> p = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(t.getParams(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    Object sd = p.get("startDate"), ed = p.get("endDate");
                    if (sd != null && !String.valueOf(sd).isBlank())
                        qw.ge(UsageEvent::getArriveTime, java.time.LocalDate.parse(String.valueOf(sd)).atStartOfDay());
                    if (ed != null && !String.valueOf(ed).isBlank())
                        qw.lt(UsageEvent::getArriveTime, java.time.LocalDate.parse(String.valueOf(ed)).plusDays(1).atStartOfDay());
                }
            } catch (Exception parseEx) {
                log.warn("[EXPORT] params 解析失败，按全量导出：{}", parseEx.getMessage());
            }
            List<UsageEvent> events = usageEventMapper.selectList(qw.orderByDesc(UsageEvent::getArriveTime));
            Path dir = Path.of(exportDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(t.getId() + ".csv");
            StringBuilder csv = new StringBuilder("事件ID,时间,产品,部门,指标摘要,版本,费用,状态\r\n");
            for (UsageEvent e : events) {
                csv.append(e.getId()).append(',')
                   .append(e.getArriveTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(',')
                   .append(e.getProductId()).append(',')
                   .append(e.getDeptName() == null ? "未分配" : e.getDeptName()).append(',')
                   .append('"').append(e.getMetrics() == null ? "" : e.getMetrics().replace("\"", "'")).append('"').append(',')
                   .append(e.getPriceVersion()).append(',')
                   .append(e.getCostAmount()).append(',')
                   .append(e.getStatus()).append("\r\n");
            }
            Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
            t.setStatus("done");
            t.setFileKey(file.getFileName().toString());
            t.setFileRows((long) events.size());
        } catch (IOException e) {
            t.setStatus("failed");
            t.setFailReason(e.getMessage());
        }
        t.setFinishedAt(LocalDateTime.now());
        exportTaskMapper.updateById(t);
    }

    public List<ExportTask> list(Long tenantId) {
        return exportTaskMapper.selectList(new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getTenantId, tenantId).orderByDesc(ExportTask::getId));
    }

    public byte[] download(Long tenantId, Long taskId) throws IOException {
        ExportTask t = exportTaskMapper.selectById(taskId);
        if (t == null || !t.getTenantId().equals(tenantId) || !"done".equals(t.getStatus())) {
            throw new IllegalArgumentException("导出任务不存在或未完成");
        }
        return Files.readAllBytes(Path.of(exportDir, t.getFileKey()));
    }
}
