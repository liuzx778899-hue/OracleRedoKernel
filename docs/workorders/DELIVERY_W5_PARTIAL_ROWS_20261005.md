# W5 真实SAVEPOINT部分回滚存活完整行

状态：专属19c固定schema，两笔串行提交中包含一次真实SAVEPOINT部分回滚的有限完整行链路通过。并发/初始恢复/通用字典、完整CDC及发布未验收。

## 制品与一次性来源

源脚本9d4ff98，布局修复0e502ec，最终运行时1bfa736，独立验证/派生器dfcc2ad。两新冻结树分别Java17 clean verify，227测试0失败/错误/跳过。最终jar SHA256 `e22834cf9f8af248d6f12c3170c088689f3b3e9ae48d04d0eebc3df5efdfd0f3`。

专属现有项目表核source/角色generation/单行11/rollback-proof/new-note/无supplemental，再实际获取snapshot2348260，SHA256 `c68474d0c55aea41e83805e1075b22fcabb2067f5c409752131cbd72ac0689e4`。一笔事务INSERT新ID13/partial-open/partial-base，SAVEPOINT后UPDATE TXT partial-cancel，再ROLLBACK TO该savepoint。前后完整XID均10.6.1643；随后UPDATE NOTE partial-note并commit，保存两行SQL真值。另笔XID10.26.1642只CAS DELETE该新行并commit，最终原ID11未改。无DDL/config/supplemental变化或重启，已提交动作没有重放。

seq32归档522240字节，SHA256 `357ee00bc9877b1eb89f9e6d7c2d5f80d1fd1300c78261fb199435422c712dc8`，first2346802/next2348277覆盖基线至两次提交。原件/独立真值在`lab/out/oracle19-partial-rows-20261005`；首轮/r2/r3在`lab/out/release-partial-rows*`，默认不入Git。

## 真实解析与重放

首轮1930b16遇新的KDOM2控制字0x10、rc1/stdout0，物理尚未闭合，不能计为部分回滚拒绝验收。原始32:1014:108的forward/inverse均XA packed、KDO28字节、count1/map2/控制字0x10、payload`0c`加12字节。native分别是NOTE partial-base/partial-note，size0。0e502ec只接受列索引2+控制字0x10的XA短正长度精确组合，核整字段长度；其它map/控制字/XR仍拒绝，sizeDelta继续未知，不推断任意控制位公式。

r2完整物理图闭合后，行入口UNSUPPORTED_ROW_REPLAY_PARTIAL_ROLLBACK、rc1/stdout0，原文保留。1bfa736改为选择已闭合commit图的live原始heap位置/身份；全部原始和补偿仍必须先完成物理门禁，不根据slot/DBA/列或最近值猜被取消操作。完整rollback仍先证明全取消/零live后不产行；初始跨snapshot/并发及整批before/extent/forward顺序/覆盖检查仍保留。

| 存活完整逻辑操作 | before | after |
|---|---|---|
| INSERT，data73076/DBA03000193/slot2 | 不存在 | 13/partial-open/partial-base |
| NOTE UPDATE，同位置 | 13/partial-open/partial-base | 13/partial-open/partial-note |
| 后续DELETE，同位置 | 13/partial-open/partial-note | 不存在 |

- 两自主物理图4原始/1取消/3保留及2/0/2，共6/1/5，与native完整有序图零差异。被取消TXT UPDATE对应UBA37751219:263:27，live heap为25/28；index live和DELETE仍完整验证。
- 独立native比较器先发现完整XID、闭合图与live UBA集合，再根据原始forward/inverse字节构建存活行，不从Java/SQL反推liveness。三次完整行与native及旧ASOF、partial commit后的两行、最终一行三SQL快照零差异。
- 自有会话SAVEPOINT后状态与完整XID另存，属于未提交实验阶段真值；不作为事件生产输入。partial-cancel值和已取消原始位置没有产出。

## 反例与回归

| 检查 | 结果 |
|---|---|
| raw未知短控制字、错map | checksum重封/scan通过，物理及行UNSUPPORTED_KDOM2_UPDATE_LAYOUT、rc1/stdout0 |
| raw短prefix长度错 | scan通过，物理及行UNSUPPORTED_KDOM2_VALUE_LENGTH、rc1/stdout0 |
| raw取消slot错/marker opcode缺失 | scan通过，物理及行分别MISSING_COMPENSATION_HEADER_ASSOCIATION/UNBOUND_PHYSICAL_WINDOW_OPERATION:11.5、rc1/stdout0 |
| raw最终commit tail指向已取消节点 | scan通过，物理及行UNDO_HISTORY_ALLOCATION_TAIL_MISMATCH、rc1/stdout0 |
| raw最后DELETE inverse before伪值 | scan及物理图仍rc0；两次合法存活变化局部处理后ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0，不能泄漏前两行 |
| 声明基线重复INSERT行/原回执副本 | 重算digest语义测试ROW_REPLAY_INSERT_OVER_EXISTING_ROW；原获取SHA下SNAPSHOT_ACQUISITION_DIGEST_MISMATCH；均rc1/stdout0 |
| 五比较器反例 | 漏live UPDATE、误发取消UPDATE、after伪值、partial commit中间SQL伪值、live指向已取消UBA均检出 |
| 同件jar旧回归 | seq31完整rollback抑制+1提交行、seq30完整INSERT/DELETE与独立native/SQL零差异；seq28/29真实初始边界仍整批零输出拒绝 |
| public events seq32 | DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0，CDC0 |

## 接续与限制

仅证明本固定schema/单rowpiece、两串行提交和一次取消中间UPDATE后继续修改的真实链路。不是所有SAVEPOINT/跨块/并发组合通过；无提交变化仍NO_ROW_CHANGES_AFTER_BASELINE，不当采集无业务数据keepalive/ACK。生产历史字典/主键语义和获取authority仍待证，SHA不认证签发者。

按[并发完整行与提交顺序派单](DISPATCH_W5_CONCURRENT_ROWS_20261005.md)继续；随后多savepoint/跨块/空结果、初始恢复/历史schema/DDL/ABA/类型、持续capture/ACK、StreamHouse和独立现场/发布。清单：[DELIVERY-partial-rows-20261005.json](evidence/DELIVERY-partial-rows-20261005.json)。
