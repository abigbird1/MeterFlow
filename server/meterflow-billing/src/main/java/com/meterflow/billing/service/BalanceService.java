package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.AccountTransaction;
import com.meterflow.billing.entity.BalanceAccount;
import com.meterflow.billing.entity.RechargeRecord;
import com.meterflow.billing.entity.SuspensionLog;
import com.meterflow.billing.exception.BillingErrorCode;
import com.meterflow.billing.mapper.AccountTransactionMapper;
import com.meterflow.billing.mapper.BalanceAccountMapper;
import com.meterflow.billing.mapper.RechargeRecordMapper;
import com.meterflow.billing.mapper.SuspensionLogMapper;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.billing.exception.BillingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.UUID;

/** 余额域（M5）：充值（模拟支付）· 有限透支（R16/R40）· 回正即时恢复（R23） */
@Service
@RequiredArgsConstructor
public class BalanceService {

    /** 充值单笔限额（分）：10 万元；接入 platform_config 远程配置前的本地基线（R40） */
    private static final long RECHARGE_MAX_CENTS = 10_000_000L;
    private static final long RECHARGE_MIN_CENTS = 100L;

    private final BalanceAccountMapper accountMapper;
    private final AccountTransactionMapper transactionMapper;
    private final RechargeRecordMapper rechargeMapper;
    private final SuspensionLogMapper suspensionLogMapper;

    public record RechargeResult(Long balanceCents, String stage, boolean recovered) {}

    @Transactional
    public RechargeResult recharge(Long tenantId, long amountCents, String method) {
        if (amountCents < RECHARGE_MIN_CENTS || amountCents > RECHARGE_MAX_CENTS) {
            throw new BusinessException(BillingErrorCode.RECHARGE_LIMIT,
                    "单笔限 " + RECHARGE_MIN_CENTS / 100 + " ~ " + RECHARGE_MAX_CENTS / 100 + " 元");
        }
        BalanceAccount acc = lockAccount(tenantId);
        long before = acc.getBalanceCents();
        acc.setBalanceCents(before + amountCents);

        RechargeRecord r = new RechargeRecord();
        r.setTenantId(tenantId); r.setAmountCents(amountCents); r.setMethod(method);
        r.setStatus("success"); r.setPayNo("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        rechargeMapper.insert(r);

        AccountTransaction tx = new AccountTransaction();
        tx.setTenantId(tenantId); tx.setTxType("recharge"); tx.setRefType("recharge_record");
        tx.setRefId(r.getId()); tx.setAmountCents(amountCents);
        tx.setBalanceAfterCents(acc.getBalanceCents()); tx.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(tx);

        boolean recovered = before < 0 && acc.getBalanceCents() >= 0;
        String stage = acc.getStatus();
        if (recovered) {
            stage = "normal";
            acc.setStatus(stage);
            suspensionLogMapper.insert(log(tenantId, "restore", "充值回正，全部产品恢复（R23）"));
        }
        accountMapper.updateById(acc);
        return new RechargeResult(acc.getBalanceCents(), stage, recovered);
    }

    public BalanceAccount wallet(Long tenantId) {
        BalanceAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, tenantId).last("limit 1"));
        if (acc == null) {
            acc = new BalanceAccount();
            acc.setTenantId(tenantId); acc.setBalanceCents(0L);
            acc.setOverdraftCents(-10000L); acc.setStatus("normal"); acc.setVersion(0L);
            accountMapper.insert(acc);
        }
        return acc;
    }

    public java.util.List<RechargeRecord> recharges(Long tenantId) {
        return rechargeMapper.selectList(new LambdaQueryWrapper<RechargeRecord>()
                .eq(RechargeRecord::getTenantId, tenantId)
                .orderByDesc(RechargeRecord::getId));
    }

    private BalanceAccount lockAccount(Long tenantId) {
        BalanceAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, tenantId).last("FOR UPDATE"));
        if (acc == null) {
            acc = new BalanceAccount();
            acc.setTenantId(tenantId); acc.setBalanceCents(0L);
            acc.setOverdraftCents(-10000L); acc.setStatus("normal"); acc.setVersion(0L);
            accountMapper.insert(acc);
        }
        return acc;
    }

    private SuspensionLog log(Long tenantId, String action, String detail) {
        SuspensionLog s = new SuspensionLog();
        s.setTenantId(tenantId); s.setAction(action); s.setDetail(detail);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }
}
