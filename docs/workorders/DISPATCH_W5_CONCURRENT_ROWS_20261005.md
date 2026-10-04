# W5 真实并发完整行与提交顺序

状态：本次不同行并发有界样本已执行并独立验证，见[交付](DELIVERY_W5_CONCURRENT_ROWS_20261005.md)。同一行真实阻塞与相同结束SCN等更广边界未验收，见[接续执行包](DISPATCH_W5_ROW_ORDER_BOUNDARIES_20261005.md)。以下保留原始任务与判据，不能让物理图通过冒充通用逻辑行通过。

1. 专属现有项目表核source/两角色generation/无supplemental及单行ID11/rollback-proof/new-note。新的实际固定SCN基线及控制进程获取SHA；新目录，脚本先提交，不重放seq32。
2. 自有持久A会话CAS UPDATE ID11 TXT concurrent-open后保持未提交，握手记录完整XID。B独立会话INSERT新ID15/concurrent-b/b-note并commit，保存独立已提交SQL真值，必须仍看到A旧值。A随后CAS UPDATE自己的行TXT concurrent-closed并commit；保存独立SQL真值，再单独CAS DELETE B新行并commit清理。仅专属源、无DDL/config改变；异常只回滚自己的未提交事务，不重放已提交操作。
3. 冻结完整连续归档/native并先验自主物理图、所有source/位置/begin/end/heap/index。实际begin/end交叠及B先commit、A后commit由独立native证明；未知控制先修真实解析，不当并发门禁通过。
4. 明确逻辑输出采用commit SCN及稳定原始位置顺序：B INSERT在A已提交事件之前；A内部两次UPDATE保持原始顺序与完整before/after，最后B DELETE完整before。不能按raw到达顺序提前发布A未提交值；不能将同时活跃但不同rowkey误合并为UPDATE。
5. 如需重放改动，先收全按XID归属的存活原始pair/位置，再以已验证commit顺序应用到完整基线，整件source/覆盖/late before失败仍无输出。重复/缺节点、错end/owner、同rowkey冲突/未证并发、初始跨snapshot和漏事件/误顺序比较器分别反例；跨事务约束和通用schema不自动通过。
6. 达成后继续同rowkey阻塞/多savepoint/跨块/无提交变化空结果、初始事务恢复/历史字典/主键映射/获取信任/类型、capture/ACK、StreamHouse和现场/发布。public events继续拒绝。
