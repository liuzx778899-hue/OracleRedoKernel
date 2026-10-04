# W5 真实完整行回滚门禁

状态：待执行。INSERT/DELETE有界完整行已通过，当前`ArchivedHeapRows`明确UNSUPPORTED_ROW_REPLAY_ROLLBACK，不能让物理rollback通过冒充完整行通过。

1. 专属现有项目表先核source/角色generation/无supplemental和单行ID11/boundary-closed/new-note；新目录，脚本先提交。实际获取新的固定SCN基线及控制进程交付SHA，不手填、不重放seq30。
2. 一次受控事务INSERT新ID13/rollback-open/rollback-note，再更新这条新行TXT至rollback-changed，记录完整XID，完整ROLLBACK；独立SQL确认新行不存在、原行不变。另一事务CAS UPDATE既有ID11 TXT boundary-closed→rollback-proof并提交，保存完整XID/旧ASOF/最终真值。固定schema、无源配置改变。
3. 冻结覆盖基线至两事务结束的全部连续归档与native。先验完整物理begin/end、所有heap/index原始/取消/保留和补偿归属；未知布局必须先补真实解析，不用安全拒绝充当完整行回滚支持。
4. 完全rollback只有在整件物理图证明全部原始已取消、零live、真实rollback end及全源覆盖成立时才能不产出该事务的逻辑行。随后提交的UPDATE仍需完整before/after；若改变现有拒绝策略，真实正例及缺补偿/错结束/错before/漏操作/先前合法变更后失败均整批验证。
5. 仅rolledBack旗标、最终SQL相等或比较器零差异不能替代补偿/完整图证明。无提交变化输入的空结果策略需明确；不悄悄跳过未闭合、未证partial或初始跨snapshot事务。
6. full rollback之后继续SAVEPOINT部分回滚与并发完整行、初始事务恢复、历史schema/主键映射/DDL/ABA、生产基线信任、类型、capture/ACK、StreamHouse和现场/发布。public events持续拒绝直至完整门禁通过。
