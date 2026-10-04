# W5 获取基线后的真实INSERT/DELETE完整行

状态：真实有限链路已完成，见[INSERT/DELETE交付](DELIVERY_W5_INSERT_DELETE_ROWS_20261005.md)。以下已提交动作不重复执行，接续[完整行rollback](DISPATCH_W5_ROLLBACK_ROWS_20261005.md)。现有UPDATE/PK及真实初始事务跨snapshot拒绝已有限交付；通用DML和CDC未验收。

1. 专属现有项目表先只读核source/无supplemental、两角色generation、总数1以及ID11/TXT boundary-closed/NOTE new-note。新输出目录，脚本先提交，不重放seq28/29事务。
2. 先用实际只读获取脚本取得新的固定SCN完整基线及控制进程交付SHA；不手填基线，不复用已跨过事务的2345429来冒充正样本。
3. 一次性INSERT新增ID12/TXT insert-proof/NOTE inserted-note，必须影响1行并记录完整XID/commit；独立保存两行SQL真值。另一事务仅CAS DELETE这条ID12新行，必须影响1行并记录另一XID/commit；独立保存最终仍1行真值。不删除/修改既有ID11，不以最终行相等代替两次操作意图与before/after。
4. 冻结覆盖snapshot至两次commit的完整连续归档、原始输入SHA和native dump。完整物理图先独立验收，再验证新增行完整INSERT after以及DELETE完整before；heap/index对及事务图、native有序变化、两次SQL真值必须全一致。
5. 任意未知布局、未证delete逆向值、遗漏/重排、先前rowpiece冲突均整批拒绝。派生原始字节反例、错基线行与比较器反例分列；不能把scan、编译、最终行相等、安全拒绝算真实DML支持。
6. 保留失败原文和已提交动作，只补读取/解析；随后继续rollback/并发/类型、初始事务恢复与历史字典，capture/ACK、StreamHouse和现场/发布独立验收。未全部通过前public events拒绝。
