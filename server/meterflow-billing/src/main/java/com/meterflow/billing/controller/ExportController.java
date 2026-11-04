package com.meterflow.billing.controller;

import com.meterflow.billing.entity.ExportTask;
import com.meterflow.billing.service.ExportService;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.MeContext;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 异步导出（AF2）：创建任务 / 任务列表 / 下载（文件保留 7 天） */
@RestController
@RequestMapping("/t/exports")
@RequiredArgsConstructor
public class ExportController {

    private final KeyCacheService keyCacheService;
    private final ExportService exportService;

    public record CreateRequest(String taskType, String billNo, String startDate, String endDate) {}

    private Long tenant(String authorization) {
        MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null || !"t".equals(me.portal())) {
            throw new com.meterflow.common.exception.BusinessException(
                    new com.meterflow.common.exception.ErrorCode() {
                        @Override public String getCode() { return "GWY-0401"; }
                        @Override public String getMessage() { return "登录已失效，请重新登录"; }
                    });
        }
        return me.tenantId();
    }

    @PostMapping
    public Result<ExportTask> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody CreateRequest req) {
        String params = "{\"taskType\":\"" + (req.taskType() == null ? "events" : req.taskType())
                + "\",\"billNo\":\"" + (req.billNo() == null ? "" : req.billNo())
                + "\",\"startDate\":\"" + (req.startDate() == null ? "" : req.startDate())
                + "\",\"endDate\":\"" + (req.endDate() == null ? "" : req.endDate()) + "\"}";
        return Results.ok(exportService.create(tenant(authorization),
                req.taskType() == null ? "events" : req.taskType(), params));
    }

    @GetMapping
    public Result<List<ExportTask>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Results.ok(exportService.list(tenant(authorization)));
    }

    @GetMapping("/{taskId}/download")
    public ResponseEntity<byte[]> download(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable Long taskId) throws Exception {
        byte[] bytes = exportService.download(tenant(authorization), taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export-" + taskId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}
