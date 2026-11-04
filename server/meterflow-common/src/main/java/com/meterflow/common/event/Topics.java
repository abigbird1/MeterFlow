package com.meterflow.common.event;

/** MQ 主题契约（04.数据模型设计 §7，payload 契约随 05 接口设计后续细化） */
public final class Topics {

    public static final String T_ORDER_PAID = "T_ORDER_PAID";                   // equity → billing：订单已支付→发放实例
    public static final String T_BILLING_COMPLETED = "T_BILLING_COMPLETED";     // billing → finance/platform/ES：计费完成
    public static final String T_BALANCE_CHANGED = "T_BALANCE_CHANGED";         // billing → finance：余额变动→催缴/账单联动
    public static final String T_AUDIT_LOG = "T_AUDIT_LOG";                     // 各服务 → platform：审计入 ES
    public static final String T_ADJUSTMENT_EFFECTED = "T_ADJUSTMENT_EFFECTED"; // finance → billing/platform：调整单生效
    public static final String T_DISPUTE_RESOLVED = "T_DISPUTE_RESOLVED";       // finance → platform：争议已解决
    public static final String T_BILL_LIFECYCLE = "T_BILL_LIFECYCLE";           // finance → platform：账单出账/确认/结清（评审 P-2）
    public static final String T_DISPUTE_LIFECYCLE = "T_DISPUTE_LIFECYCLE";     // finance → platform：争议受理/解决（评审 P-2）

    private Topics() {}
}
