package com.meterflow.billing.service;

import com.meterflow.billing.entity.PackageInstance;
import com.meterflow.billing.feign.EquityClient;
import com.meterflow.billing.mapper.PackageInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GrantService 单测：来源商品删除跳过（毒消息治理）/ 字段映射 / 幂等（R34 幂等键=orderNo） */
@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

    @Mock private PackageInstanceMapper packageMapper;
    @Mock private EquityClient equityClient;

    private GrantService service;

    @BeforeEach
    void setUp() {
        service = new GrantService(packageMapper, equityClient);
    }

    private Map<String, Object> payload(String orderNo, long goodsId) {
        return Map.of("orderNo", orderNo, "tenantId", 1001L, "goodsId", goodsId,
                "qty", 1, "quantity", 500000, "unit", "Token", "days", 30);
    }

    private Map<String, Object> goodsResp(long goodsId) {
        // Feign goods 返回完整响应 Map（data 内为商品字段）
        return Map.of("code", "00000",
                "data", Map.of("goodsId", goodsId, "name", "FT out pack", "productId", 1001L,
                        "metricKey", "out_tok", "quantity", 500000, "unit", "Token", "validDays", 30));
    }

    @Test
    @DisplayName("来源商品已删除 → 业务性跳过（不落库，防 MQ 毒消息无限重试）")
    void skipWhenGoodsDeleted() {
        Map<String, Object> nullDataResp = new java.util.HashMap<>();
        nullDataResp.put("code", "00000");
        nullDataResp.put("data", null);
        when(equityClient.goods(9999L)).thenReturn(nullDataResp);

        Map<String, Object> r = service.grant(payload("PO-SKIP", 9999L));

        assertThat(r.get("granted")).isEqualTo(false);
        assertThat(String.valueOf(r.get("reason"))).contains("已删除");
        verify(packageMapper, never()).insert(any(PackageInstance.class));
    }

    @Test
    @DisplayName("正常发放：实例字段映射正确（总量/余量/有效期=今天+validDays/状态 active）")
    void grantMapsFields() {
        when(packageMapper.selectCount(any())).thenReturn(0L);
        // 模拟 MyBatis-Plus 雪花 ID 赋值（真实 insert 由 MP 填充）
        when(packageMapper.insert(org.mockito.ArgumentMatchers.<PackageInstance>any())).thenAnswer(inv -> {
            inv.getArgument(0, PackageInstance.class).setId(8888L);
            return 1;
        });
        when(equityClient.goods(1001L)).thenReturn(goodsResp(1001L));

        Map<String, Object> r = service.grant(payload("PO-G1", 1001L));

        assertThat(r.get("granted")).isEqualTo(true);
        ArgumentCaptor<PackageInstance> cap = ArgumentCaptor.forClass(PackageInstance.class);
        verify(packageMapper).insert(cap.capture());
        PackageInstance p = cap.getValue();
        assertThat(p.getSourceOrderNo()).isEqualTo("PO-G1");
        assertThat(p.getTenantId()).isEqualTo(1001L);
        assertThat(p.getTotal().toBigInteger()).isEqualTo(java.math.BigInteger.valueOf(500000));
        assertThat(p.getRemaining()).isEqualTo(p.getTotal());
        assertThat(p.getStatus()).isEqualTo("active");
        assertThat(p.getExpireDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(p.getMetricKey()).isEqualTo("out_tok");
    }

    @Test
    @DisplayName("幂等：同 orderNo 已发放 → 跳过且不重复 insert、不回查商品（R34）")
    void idempotentByOrderNo() {
        when(packageMapper.selectCount(any())).thenReturn(1L);   // 已存在

        Map<String, Object> r = service.grant(payload("PO-DUP", 1001L));

        assertThat(r.get("granted")).isEqualTo(false);
        verify(packageMapper, never()).insert(any(PackageInstance.class));
        verify(equityClient, never()).goods(any());
    }

    @Test
    @DisplayName("goods 回查异常向上抛出（由调用方决定重试/跳过策略，不落半截数据）")
    void goodsLookupFailurePropagates() {
        when(equityClient.goods(1001L)).thenThrow(new RuntimeException("equity down"));

        assertThatThrownBy(() -> service.grant(payload("PO-FAIL", 1001L)))
                .hasMessageContaining("equity down");
        verify(packageMapper, never()).insert(any(PackageInstance.class));
    }

    @Test
    @DisplayName("金额类字段使用 BigDecimal 精确映射（过程量不受浮点影响）")
    void totalUsesBigDecimal() {
        when(packageMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.insert(org.mockito.ArgumentMatchers.<PackageInstance>any())).thenAnswer(inv -> {
            inv.getArgument(0, PackageInstance.class).setId(9999L);
            return 1;
        });
        Map<String, Object> resp = Map.of("code", "00000",
                "data", Map.of("goodsId", 1001L, "name", "P", "productId", 1001L,
                        "metricKey", "out_tok", "quantity", 12345.6789, "unit", "Token", "validDays", 30));
        when(equityClient.goods(1001L)).thenReturn(resp);

        service.grant(payload("PO-BD", 1001L));
        ArgumentCaptor<PackageInstance> cap = ArgumentCaptor.forClass(PackageInstance.class);
        verify(packageMapper).insert(cap.capture());
        assertThat(cap.getValue().getTotal()).isEqualByComparingTo("12345.6789");
    }
}
