# W4 5.6 heap补偿关联交付

源码`0c5ff4c600680812298d82df623ffb14702018e1`，main。完整提交树独立重建66项测试0失败0跳过；jar SHA256 `1c0dce2bed4614431d50f24306f9bdfae0deef87a4c2166e2cb6cc870da9415b`。[封存清单](evidence/DELIVERY-0c5ff4c.json)关联原始输入、独立dump/SQL真值、正反例与日志。本轮无Oracle DML/部署/目标写入。

## 关联实现

`HeapCompensation.match`只接受实证5.6 heap补偿，联合校验容器身份、undo block DBA、slot/wrap、object/dataObject/tablespace、previous DBA和rci，以及原undo逆向行变化的操作/DBA/slot/列状态/字节值。必须找到唯一原pair及唯一XR物理行；缺失/多义拒绝。忽略XR恢复后的旧XID/UBA作为当前操作身份，也不采用rci+1或邻接推断。

`TransactionLifecycle.requireActive`在匹配后校验完整XID仍活跃；slot未结束就出现新的sequence即拒绝并锁定实例。5.6的16位wrap不足以证明完整sequence，历史出现低wrap相同的多个原操作必须拒绝。

研究驱动按record完成起点、目标undo、正向pair、5.6匹配及结束检查，保持18原pair/11身份覆盖，再检查补偿不重复、T007恰好保留一个pair。整个归档链通过后才输出研究审计。

## 同件制品实证

| 项目 | 结果 |
|---|---|
| T007 INSERT8撤销 | marker RBA8/30718/128 → 原undo 0x02402cbe.00f2.24（十进制record36） |
| T007 discard UPDATE撤销 | marker RBA8/30718/304 → 原undo 0x02402cbe.00f2.23（record35） |
| T007保留 | 10.5.1133的原undo record33，INSERT7/keep/7/date |
| 独立Oracle dump | 两个补偿关联及保留pair零差异；原history18对 |
| 冻结SQL真值 | row7四列DUMP字节与保留原INSERT一致，row8不存在 |
| 只提供归档8 | rc1、stdout0 bytes，INCOMPLETE_TRANSACTION_IDENTITIES |
| 只提供归档9 | rc1、stdout0 bytes，UNDO_WITHOUT_VERIFIED_TRANSACTION_BEGIN |

新增7项matcher测试涵盖旧UBA、缺/重复原操作、错误rci、值/NULL状态错配、重复XR行、容器/slot wrap错配、begin补偿/正向行拒绝和低wrap碰撞。新增2项生命周期测试覆盖结束后的匹配、完整sequence及容器隔离、未结束slot复用；相关拒绝保持实例锁定。单元派生反例不冒充新增Oracle执行矩阵。

## 六列及门禁

代码交付与针对性T007匹配/保留真值通过；完整事务集成进行中；部署/发布未开始；现场验收待证。生产`events`仍rc1，CDC事件0。

本轮没有证明完整undo前驱链、index undo、5.11 begin补偿或重启恢复。matcher只返回关联的原pair，不直接取消或发布CDC事件；完整before/after、主键与SCN字典/基线仍待证。冻结row7一致不能替代全事务语义验收。

接续：5.11与活跃segment/slot完整XID关联；所有相关5.1（含index）的前驱链覆盖和唯一性；重复修改、slot复用、跨undo block、缺chain、重复补偿及恢复反例。随后把已验证事务行效应与W5字典/基线组合，进入真实无supplemental完整事件对账。
