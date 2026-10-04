# W4 已分配块head-update控制交付

savepoint回滚后继续写入会把事务生效head与原始分配尾分开。新增`UndoHeadUpdate`解码已观察的5.2/0x0002控制，`TransactionLifecycle.headUpdate`按同record精确新UBA、完整活跃XID及segment/slot绑定。它不要求新分配块record1，也不推断完整旧前驱。

生产源码main 6d30626；[封存清单](evidence/DELIVERY-6d30626.json)。完整Git archive树、Java17独立构建，同件jar SHA256 `465ac5ff6b23672f72056d9b7f0a48ff2b6f8cacee9a45485180e94fa835410c`。104项测试，0失败/错误/跳过。未部署，无新Oracle数据库动作；只读冻结seq13及既有样本，不重复fixture DML。

## 针对性结果

| 项目 | 证据 | 处置 |
|---|---|---|
| 5.2/0x0002控制身份 | RBA11153:352/vector1、segment37749008、slot2、新UBA37760278:242:3、XID10.2.1163、previous DBA37760270/rci0，与native零差异 | PASS有界解析/身份绑定 |
| 真实派生拒绝 | 缺原undo、重复原undo、低wrap碰撞的全sequence+65536、错segment、未知flags、缺previous DBA、错rci、control不在record、重复control、错CON_UID | 10项拒绝，10次生命周期锁定 |
| seq13 inventory/补偿 | 130节点、60补偿含8跨块、70保留patch，native零差异；SQL最终两行字节相同 | PASS原有有界能力回归 |
| 旧seq10/11/12回归 | 360节点、180补偿/180保留patch含23跨块，native零差异；旧18pair/4补偿/SQL row7回归通过 | PASS |
| 事件出口 | 四批`events` rc1、stdout空 | 继续拒绝 |
| 完整生效图/index/恢复/W5 | 没有完整证据 | HIGH待证 |

真实派生反例审计驱动总rc0，输出10行`REFUSED`诊断；每个案例要求预期拒绝原因，并确认后续`finish`返回`TRANSACTION_LIFECYCLE_ALREADY_REFUSED`。这不是输出CDC或把审计rc0当生产事件成功。

解码器只接受单一32字节field、sqn0、flags2、parent0及record>=2。原始undo仍须为已支持regular header、slot/wrap一致、previous DBA非零/rci0；匹配必须唯一。重复同新UBA的控制、无verified begin或完整身份/segment不一致均拒绝。可读的siz/fbi未赋予新增语义，不宣称其它field数量、flag组合或record1变体支持。

已有`UndoBlockAdvance`的0x000a/record1范围不变；新控制不是隐式扩展其分配含义。`UndoLink`仍拒绝未证跨块完整前驱。head-update返回的是原始undo身份，不授权保留/撤销行效果，不证明capture或事务完整性。

下一阶段组合archive-owned inventory、begin/end身份、原始分配历史及补偿后的生效head，证明rollback后新写入的分叉关系；再补第二次savepoint、完整rollback/index与恢复。W5字典/基线、StreamHouse接入和在线capture继续待验收。

同步说明：上轮4a860ce推送终止，保留错误`Operation timed out after 300043 milliseconds with 0 out of 0 bytes received`及原会话检查点。本轮交付先封存本地并重试同步，远端是否成功单独按git输出确认，不由本地测试代替。
