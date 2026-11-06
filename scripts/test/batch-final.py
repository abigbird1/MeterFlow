# 收尾批次补丁：D6/A3 通知联动 + D5 转人工处置 + D1 前端注册
import os
BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'server')  # server/

def patch(path, pairs):
    full = os.path.join(BASE, path)
    s = open(full, encoding='utf-8').read()
    changed = False
    for old, new in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            changed = True
    if changed:
        open(full, 'w', encoding='utf-8').write(s)
    return changed

# 1) platform: /feign/notify 内部通知接口
p = 'meterflow-platform/src/main/java/com/meterflow/platform/controller/FeignController.java'
patch(p, [(
    '    /** 按 Key 散列取接入上下文',
    '''    /** 站内通知落库（A3/D6：finance 事件联动触达租户） */
    @PostMapping("/notify")
    public Result<com.meterflow.platform.entity.Notification> notify(@RequestBody Map<String, Object> body) {
        Long tenantId = Long.valueOf(String.valueOf(body.get("tenantId")));
        com.meterflow.platform.entity.Notification n = new com.meterflow.platform.entity.Notification();
        n.setTenantId(tenantId);
        n.setType(String.valueOf(body.getOrDefault("type", "info")));
        n.setTitle(String.valueOf(body.getOrDefault("title", "平台通知")));
        n.setContent(String.valueOf(body.getOrDefault("content", "")));
        n.setIsRead(false);
        notificationMapper.insert(n);
        return Results.ok(n);
    }

    /** 按 Key 散列取接入上下文'''
)])

# 2) finance BillingClient: notify 通道
p = 'meterflow-finance/src/main/java/com/meterflow/finance/feign/BillingClient.java'
patch(p, [(
    '    /** 赠送额度单生效后的落地发放（复用 billing /a/grants 内部能力） */',
    '''    /** 站内通知触达（A3/D6：经 platform /feign/notify 落库） */
    @org.springframework.web.bind.annotation.PostMapping("/feign/notify")
    Map<String, Object> notify(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload);

    /** 赠送额度单生效后的落地发放（复用 billing /a/grants 内部能力） */'''
)])

# 3) finance DisputeService: 生命周期通知
p = 'meterflow-finance/src/main/java/com/meterflow/finance/service/DisputeService.java'
patch(p, [
('    private final AdjustmentOrderMapper adjustmentMapper;',
 '''    private final AdjustmentOrderMapper adjustmentMapper;
    private final com.meterflow.finance.feign.BillingClient billingClient;'''),
('''        disputeMapper.updateById(d);
        reply(d, operator + "（客服）", "工单已受理，开始核验相关流水。");
        return d;''',
 '''        disputeMapper.updateById(d);
        reply(d, operator + "（客服）", "工单已受理，开始核验相关流水。");
        try {
            billingClient.notify(Map.of("tenantId", d.getTenantId(), "type", "info",
                    "title", "争议已受理", "content", "工单 " + d.getDisputeNo() + " 已受理，处理中。"));
        } catch (Exception ignored) { }
        return d;'''),
('''        d.setStatus("solved");
        d.setCompensation((refund ? "退费调整单 " : "赠送额度单 ") + a.getAdjNo() + "（审批中）");
        disputeMapper.updateById(d);''',
 '''        d.setStatus("solved");
        d.setCompensation((refund ? "退费调整单 " : "赠送额度单 ") + a.getAdjNo() + "（审批中）");
        disputeMapper.updateById(d);
        try {
            billingClient.notify(Map.of("tenantId", d.getTenantId(), "type", "success",
                    "title", "争议已解决", "content", "工单 " + d.getDisputeNo() + " 已结案，" + d.getCompensation()));
        } catch (Exception ignored) { }'''),
])

# 4) finance: D5 转人工处置闭环
p = 'meterflow-finance/src/main/java/com/meterflow/finance/service/ReconService.java'
patch(p, [(
    '    private boolean isReviewer(String role) { return "reviewer".equals(role); }',
    '''    private boolean isReviewer(String role) { return "reviewer".equals(role); }

    /** D5：转人工处置完成（人工核验后关闭差错单） */
    @Transactional
    public void closeManual(String errNo, String operator) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, errNo).last("limit 1"));
        if (e == null || !"manual".equals(e.getStatus())) throw new BusinessException("FIN-4001", "差错单不在转人工状态");
        e.setStatus("closed");
        errorMapper.updateById(e);
    }'''
)])
# 5) ReconController: 补处置接口
p = 'meterflow-finance/src/main/java/com/meterflow/finance/controller/ReconController.java'
s = open(p, encoding='utf-8').read()
if 'closeManual' not in s:
    recon_ctrl_add = '''
    /** D5：转人工处置完成（人工核验后关闭） */
    @PostMapping("/recon/errors/{errNo}/close-manual")
    public Result<Void> closeManual(@PathVariable String errNo) {
        reconService.closeManual(errNo, "运营");
        return Results.ok();
    }
'''
    # 找到类结尾插入——用最后一个 } 前插入
    idx = s.rstrip().rfind('\n}')
    s = s[:idx] + recon_ctrl_add + s[idx:]
    open(p, 'w', encoding='utf-8').write(s)
print('finance-ok')

# 6) web: LoginView 注册入口
p = os.path.join(BASE, '..', 'web', 'src', 'views', 'LoginView.vue')
p = os.path.normpath(p)
s = open(p, encoding='utf-8').read()
if '注册新租户' not in s:
    s = s.replace('''      <el-divider>演示账号（点击填充）</el-divider>''',
'''      <el-collapse style="margin-top: 12px">
        <el-collapse-item title="注册新租户（注册即开通）">
          <el-form label-width="70" size="small">
            <el-form-item label="租户名"><el-input v-model="reg.name" placeholder="如 Acme Corp" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="reg.email" placeholder="admin@new.com" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="reg.password" type="password" placeholder="≥8位含字母数字" /></el-form-item>
            <el-button type="success" style="width: 100%" @click="doRegister">注册并登录</el-button>
          </el-form>
        </el-collapse-item>
      </el-collapse>
      <el-divider>演示账号（点击填充）</el-divider>''')
    s = s.replace('''const demoAccounts = [''',
'''const reg = reactive({ name: '', email: '', password: '' })
async function doRegister() {
  const data: any = await request.post('/auth/register', { ...reg })
  auth.saveSession(data)
  ElMessage.success(`注册成功，欢迎 ${data.user.name}`)
  router.push('/t/overview')
}

const demoAccounts = [''')
    s = s.replace("import { reactive } from 'vue'", "import { reactive } from 'vue'")
    s = s.replace("import request from '@/api/request'", "import request from '@/api/request'\nimport { useAuthStore } from '@/stores/auth'")
    s = s.replace("const router = useRouter()", "const router = useRouter()\nconst auth = useAuthStore()")
    open(p, 'w', encoding='utf-8').write(s)
print('login-ok')
