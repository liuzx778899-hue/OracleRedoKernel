# W5 真实SAVEPOINT部分回滚与存活完整行

状态：待执行。完整回滚3/3/0抑制和后续commit完整行已有限通过；当前对commit图中有补偿的partial事务明确拒绝。

1. 专属现有表先核单行ID11/rollback-proof/new-note、两角色generation/source及无supplemental；新的实际只读固定SCN基线和获取SHA。新输出目录、脚本先提交，不重放seq31。
2. 受控同一事务INSERT新ID13/partial-open/partial-base，SAVEPOINT后UPDATE新行TXT partial-cancel，再ROLLBACK TO该savepoint；独立记录仍在同事务的完整XID/自身行状态。随后UPDATE新行NOTE partial-note并commit，保存两行已提交SQL真值。另一事务CAS DELETE这条新行ID13/partial-open/partial-note并commit，保存最终原行不变。源动作仅一次，无DDL/config变更。
3. 完整归档/native先验真实所有原始/取消/live与最终commit undo tail，不能按SQL真值猜被取消节点。期望第一图heap INSERT和后续NOTE UPDATE存活，TXT UPDATE原始被取消，index INSERT存活；第二图heap/index DELETE完整存活，实际计数按原字节/native为准。
4. 逻辑产出必须保留INSERT完整after，再输出存活NOTE UPDATE完整before/after，后续DELETE完整before；取消的TXT partial-cancel不得出现。生产选择只能用已闭合有源图的live原始位置/身份，不按DBA、slot、列或最近值启发式合并；全部补偿仍需物理解析。
5. 缺补偿、错live/original关联、错最终tail、原始/forward顺序错、后续before失配、初始跨snapshot等分别整批拒绝；漏事件、误发取消操作和中间SQL伪值比较器反例独立检出。失败保全，不重跑已提交DML，只补字节解析/读取。
6. 达成后继续多savepoint/跨块/并发完整行及无提交变化的显式空结果策略，初始事务恢复/历史schema/DDL/ABA/主键映射、生产获取信任、类型、capture/ACK、StreamHouse和现场/发布。public events持续拒绝。
