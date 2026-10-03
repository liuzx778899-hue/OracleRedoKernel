# W4 有界身份生命周期交付

源码`8859c7df0d648b44a50dca5b05c7502e30ccf953`，main；完整提交树独立构建46项测试0失败0跳过。[封存清单](evidence/DELIVERY-8859c7d.json)关联制品、原始输入、独立dump和全部正反例输出SHA。本轮无新DML、部署或目标写入。

## 结果与边界

`TransactionLifecycle`维护来源隔离的起点/undo/结束身份。跨归档要求DBID、activation、resetlogs ID/SCN、thread和compatibility一致，sequence连续、SCN区间首尾相接。起点沿用精确UBA→同record唯一5.1；状态键含PDB UID、CON_ID、segment DBA、slot和完整sequence。目标undo必须命中活跃完整XID，结束必须命中已验证起点。任何拒绝锁定实例，之后不能继续闭合或finish。

冻结19c seq8→seq9，目标dataObject73024：11个身份闭合（9个commit marker、2个整笔rollback marker），其中6.12.579跨归档；与独立Oracle dump零差异。交叉会话9.19.579先结束、10.32.1136后结束，没有按开始顺序误排提交。

起点SCN特意命名`beginRecordScn`，指包含begin undo的redo record SCN；不是ktubl里的原事务start SCN。闭合结果是`ClosedIdentity`，并非可发布的行事件。T007虽身份commit，discard修改仍须通过savepoint补偿链排除。本轮不能据9个commit宣称9个正确CDC事务。

研究驱动对整条record分阶段：收齐目标起点→校验目标undo→处理目标结束；全部归档、全部身份闭合且11个有界fixture计数通过后，才输出研究结果。生产`events`没有改动，仍rc1、CDC事件0。

## 拒绝验证

同件jar对真实冻结输入的三项反例全部rc1、stdout0 bytes：

| 反例 | 拒绝原因 |
|---|---|
| 只给seq9，缺跨归档起点 | UNDO_WITHOUT_VERIFIED_TRANSACTION_BEGIN |
| 只给seq8，缺跨归档结束 | INCOMPLETE_TRANSACTION_IDENTITIES |
| seq8重复回放后seq9 | ARCHIVE_SEQUENCE_OR_SCN_GAP |

新增10项状态机测试还覆盖来源漂移、SCN gap、孤儿end、未知已认领end状态、重复begin/end、slot复用不同sequence、record范围和end倒序等。三个真实反例只证明当前身份链研究输出安全，T009完整行事件/重启恢复门禁仍待证。

首轮失败`lifecycle.stderr.txt`保留：研究驱动对所有非目标5.4也做严格layout decode而拒绝。修订`knowsEnd`只读身份前缀选择已跟踪key，不能作为接受判据；命中者必须经过`end`严格decode，目标结束缺失最终finish拒绝。系统事务/其它对象不在本轮11个身份的支持声明中。

## 六列与接续

代码交付通过；针对性身份闭合/反例通过；完整事务集成进行中（行、savepoint、undo chain、W5待证）；部署未开始；现场验收待证；发布未开始。

下一步：解释5.1中的before行片段，对5.6/5.11追溯原undo身份/前驱和逆变化；必须证明不误取消保留修改，尤其重复修改、同slot跨事务、旧UBA/ITL、补偿跨block和缺chain。然后建立SCN字典与基线，用完整主键/行变化对账接入StreamHouse。
