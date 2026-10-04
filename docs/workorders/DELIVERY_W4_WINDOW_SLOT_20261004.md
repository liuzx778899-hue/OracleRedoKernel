# W4 活动窗口slot完整sequence冲突拒绝

补偿候选覆盖器原来只登记目标begin，后续5.2控制中的另一个完整sequence可能被目标5.1筛选掩盖。新增`TransactionLifecycle.observeSlotControl`：在已验证活动身份对应的UID/CON_ID/segment范围内读取slot和完整32位sequence；同slot的非零sequence不等于活跃身份即拒绝并锁定。该segment控制身份不足8字节也拒绝。不同segment/CON_ID/slot不冒充复用，sqn0只允许继续身份排除审计，仍需独立advance/head-update解码与绑定，不能据此宣布控制支持。

`ArchivedCompensationInventory`在活动record窗口内调用此守卫，包含起止record，不依赖vector顺序判断阶段。此方法没有新增事件出口、数据源配置或完整事务组装器。

完整封存树9a2344b45afc1c9293b309f49fc57ccabf8e1dd1；[证据清单](evidence/DELIVERY-9a2344b.json)。完整Git archive树Java17独立构建，117项测试，失败/错误/跳过均0。jar SHA256 `388a0533936cd31dd73b98437c155215760fe11c11384feae711fc79a632575b`。

## 有界前后证据

从四批原生dump独立定位目标事务活动窗口内同segment/slot的首个sqn0控制，核对raw opcode、DBA、slot、flags与32字节field，再将该控制的sequence派生为目标+65536、目标+1或0xffffffff。仅改4个sequence字节并重封受影响块checksum；真实来源及派生文件分别保存SHA，未改原归档。Oracle没有执行这些冲突动作。

| 项目 | 结果 | 判定 |
|---|---|---|
| 四批三种冲突派生归档 | 共12个，新jar结构scan均rc0 | 反例到达身份门禁，不是结构损坏拒绝 |
| 旧封存覆盖读取器 | 同12个均rc0，旧jar SHA be003fdc… | 证明旧候选读取器未检查此冲突，不证明旧events曾误发 |
| 新封存覆盖读取器 | 同12个均rc1、stdout0，原因`TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE` | PASS有界身份冲突拒绝 |
| 四批原始归档正样本 | 240个候选native位置/字段零差异；490节点/240补偿/250 live回归通过 | PASS已有有界场景 |
| 覆盖选择拒绝 | 原20项漏/重/反序/外来选择继续rc1/stdout0 | 回归通过 |
| 旧pair、SQL与控制回归 | seq8/9 18pair/4补偿/row7；seq13最终两行SQL；15条独立页前驱；既有history/head-update拒绝 | 同件jar通过 |
| events | 四批rc1/stdout0 | 继续拒绝 |

新增四个单测：隐藏低wrap碰撞、不同segment/容器排除、同sequence/sqn0观察及冲突锁定、不可读活动segment控制及锁定。真实派生12例不宣称12次锁定测试；锁定由合成生命周期单测证明。

首次反例辅助器改写短record后留有旧record尾部，修复前检查得到`INVALID_RECORD_LENGTH`，未复现身份缺口；辅助器改为清空被写块后再构造。该首轮及中间失败原文保留，不把它们当生产身份拒绝证据。真正前后缺口由冻结旧jar对12份checksum有效派生输入的rc0及新jar的精确拒绝证明。

## 下一门禁

此为拒绝歧义的身份排除守卫，不支持真实slot提前复用，也不验证所有5.2布局或关联控制覆盖。完整事务相关opcode、并发候选精确归属、index、真实第二次savepoint/整事务rollback、恢复和W5继续待证。扫描前后SHA不证明online capture一致性。`UndoHistory`与覆盖清单仍须由完整生产组装器组合，lab显式调用不能替代集成验收。没有Oracle DDL/DML、配置变更、部署、现场验收或发布；严格零侵入路线继续。
