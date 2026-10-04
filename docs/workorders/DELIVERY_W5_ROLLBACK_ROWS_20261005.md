# W5 已闭合完整回滚与后续提交完整行

状态：专属19c固定schema，真实完整回滚不产行、后续提交仍重建完整行的有界链路通过。部分回滚/并发/初始恢复、完整CDC及发布未验收。

## 制品和真实源动作

源脚本0340edf、最终运行时1930b16、独立验证/派生器80135e2。新冻结树Java17 clean verify：225测试0失败/错误/跳过，jar SHA256 `efef72d97eb32a12d7ec3dde742354c19ccb76e3a0157fb1d19c044edaead254`。内核仅修改完整行重放中的回滚处置，物理解析和补偿算法无变化。

专属既有项目表先核单行11/boundary-closed/new-note、source/两角色generation/无supplemental，再实际只读获取snapshot2346786，SHA256 `fc382791fcd1814632f20e1d4c4e7ab64146154f99eb1bf873c6713d0002aa97`。一笔事务INSERT新ID13/rollback-open/rollback-note并UPDATE该新行TXT rollback-changed，完整XID10.15.1641后ROLLBACK。独立SQL确认仍只有旧ID11；另一事务CAS UPDATE该ID11 TXT boundary-closed→rollback-proof，XID10.9.1639提交。没有DDL/config/重启或supplemental改变；两源动作没有重放。最终项目行11/rollback-proof/new-note。

seq31归档4104704字节，SHA256 `f553866033cf07d73590f60d569e121e5903d990b8ccee824145e2c84eedbae4`，first2346198/next2346802覆盖实际基线至结束。原件/独立真值`lab/out/oracle19-rollback-rows-20261005`，首轮与新运行时证据`lab/out/release-rollback-rows`及`-r2`，默认不入Git。

## 实现和验收

首轮旧b9320ff已完整闭合两物理图，但行入口UNSUPPORTED_ROW_REPLAY_ROLLBACK、rc1/stdout0，保留原文，不当行回滚验收。

1930b16仍先完成整件自主物理图、source/SCN覆盖、初始跨snapshot和串行窗口门禁。只有真实rollback end、非空原始图、全部原始已补偿、零live的已闭合事务才不加入已提交行选择。其余partial rollback明确UNSUPPORTED_ROW_REPLAY_PARTIAL_ROLLBACK，不以flag跳过未闭合图。不回放已取消的INSERT/UPDATE到基线；后续提交仍按现有forward/inverse对、before/extent与整批覆盖核对。

- native独立发现两事务，完整回滚图3原始/3取消/0保留，后续提交图1/0/1，共4/3/1，完整有序图零差异。
- native行比较器先自主读取完整XID、rollback end及完整补偿图，验证3/3/0后排除其逻辑变化，不依据SQL旗标或最终相等来猜回滚；XA/XR独立区分。只有后续提交的1次完整UPDATE：11/boundary-closed/new-note→11/rollback-proof/new-note。
- 1次完整before/after及旧ASOF、回滚后、最终三SQL快照零差异；回滚ID13从未出现在逻辑行输出。

## 反例和回归

| 检查 | 结果 |
|---|---|
| raw补偿opcode缺失 | checksum重封/scan通过，物理及行UNBOUND_PHYSICAL_WINDOW_OPERATION:11.5、rc1/stdout0 |
| raw补偿slot错 | scan通过，物理及行MISSING_COMPENSATION_HEADER_ASSOCIATION、rc1/stdout0 |
| raw回滚end伪commit | scan通过，物理及行TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE、rc1/stdout0；不能只改flag绕过图闭合 |
| raw后续提交inverse before伪值 | scan和物理图仍rc0，完整行ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0 |
| 重算digest的声明基线before伪值 | ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0；同副本原回执SNAPSHOT_ACQUISITION_DIGEST_MISMATCH |
| 五比较器反例 | 漏后续commit、泄漏rollback行、after伪值、回滚后SQL伪值、物理图rollback flag伪值均检出 |
| 同件jar seq30 | 两完整INSERT/DELETE及三SQL快照native零差异 |
| 同件jar晚失配/真实初始边界 | seq30已处理INSERT后DELETE before失配整批零输出；seq28/29仍BASELINE_CROSSES_TRANSACTION_BOUNDARY、rc1/stdout0 |
| public events seq31 | DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0，CDC0 |

## 接续与限制

本例证明已完全闭合rollback在一笔后续commit之前的有限串行链路。当前无选中提交变化仍NO_ROW_CHANGES_AFTER_BASELINE，不将此拒绝当持续无业务数据时的生产keepalive/ACK策略；独立采集控制器尚待实现。没有解锁partial、并发、初始跨snapshot事务恢复，也没有生产历史字典、主键语义映射或可信获取authority认证。

接续[真实SAVEPOINT部分回滚派单](DISPATCH_W5_PARTIAL_ROLLBACK_ROWS_20261005.md)，之后并发完整行/字典/类型、持续capture/ACK、StreamHouse与独立现场/发布。清单：[DELIVERY-rollback-rows-20261005.json](evidence/DELIVERY-rollback-rows-20261005.json)。
