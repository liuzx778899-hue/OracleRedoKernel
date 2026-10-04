# W5 不同行并发完整行与提交顺序交付

仅验收本次19c固定schema、可信进程获取的完整基线之后、不同行键的有限并发样本。持续capture、StreamHouse接入、现场与发布均未通过；public events保持拒绝。

源脚本c036164，26字节cleanout修正09655d6，行重放运行时5bfde6407e357fe522421d22091a7b29b2fc637c，独立比较器/派生脚本09ddedc。最终运行树`lab/out/release-concurrent-rows-r4/tree`，Java17共229测试、0失败/错误/跳过；jar SHA256 `6a34071efd47fa10d6c6cfec655432d2d3ba433e55d73002afedb4f291fef175`。完整输入/输出/制品/命令见[封存清单](evidence/DELIVERY-concurrent-rows-20261005.json)，两次SHA核验。原始大文件保留在本地ignored目录，不入Git。

## 实际输入与可证伪结果

专属`oraredo-19c-lab-20261004`，无DDL、配置、supplemental或重启动作。一次实际固定SCN2349199获取先于A事务；基线回执SHA `3a1f3371c798d85bb414eb279059e13507d35d7ef21b99f06058dbf587c201a8`。A XID10.33.1644对原有ID11两次更新，第一次持有不提交；独立B XID9.16.599插入自有新ID15并先提交，SQL必须仍看到A的旧值。A第二次更新后提交；最后XID10.29.1644删除新ID15提交清理。源动作已经完成，未重放。

| 输入 | 字节 | SHA256 |
|---|---:|---|
| seq33 | 227840 | 05b33d3ffa665f9101c031d861856148bd6264841f75c679e76d08b2af9890a4 |
| seq34 | 7680 | a9a34a12d666aa62a5ed9d92e6be62b9b4018677ecad8fcf19f46dc968c5fc75 |

完整物理图A 2/0/2、B 2/0/2、清理2/0/2，共6原始/0补偿/6存活，三个完整XID与独立native归属/位置零差异。行入口只使用原始归档与冻结基线；先收全归属到XID的存活heap pair，再按已验证结束record SCN重放，各事务内保留原始位置顺序。

逻辑输出顺序为B INSERT → A UPDATE(open) → A UPDATE(closed) → 清理DELETE，共4条完整before/after。native比较器从真实begin UBA与结束slot/sequence独立构造生命周期，证明A/B交叠、B先提交，按native结束SCN构造验证投影；投影只用于独立对账，不输入生产解析器。完整行与OLD_BASELINE、AFTER_B_COMMIT、AFTER_A_COMMIT、FINAL四个SQL快照零差异。最终专属表一行ID11/concurrent-closed/new-note，新ID15已删除。

## 反例与回归

- 六份重封seq34：cleanout count、ITL、flags、header SCN、未来prior SCN、非零尾字节。scan均rc0；physical及rows均`UNSUPPORTED_SINGLE_ENTRY_BLOCK_CLEANOUT`、rc1/stdout0。
- 最后DELETE inverse TXT改字节，scan/physical均rc0；在三个合法行变化之后before校验失败，`ROW_REPLAY_BEFORE_VALUE_MISMATCH`、rc1/stdout0，未泄漏前缀。
- B的heap forward/inverse DBA一致改成A所在DBA，slot同为0，checksum重封；scan/physical均rc0，rows明确`UNSUPPORTED_ROW_REPLAY_OVERLAPPING_ROW_KEY`、rc1/stdout0。只证明该派生冲突拒绝，未证明真实同一行阻塞支持，也未证明index/heap主键语义一致性。
- 比较器四反例：改为begin顺序、漏A第二次UPDATE、错误after、AFTER_B SQL泄漏A未提交值，均rc1。
- 同件jar seq30完整INSERT/DELETE两行、seq31完整回滚抑制后一个UPDATE、seq32SAVEPOINT三存活完整行，各native/SQL零差异；seq28/29真实初始跨基线仍`BASELINE_CROSSES_TRANSACTION_BOUNDARY`、rc1/stdout0。
- 两原始归档分别public events均`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`、rc1/stdout0。

## 失败保全与限制

r1在真实leaf OP4.1处`UNSUPPORTED_INDEX_LEAF_WINDOW_OPCODE:4.1`，不能算并发门禁通过。首次cleanout实现错误假设24字节，实际字段26字节，r2共229测试中1失败、构建rc1；误继续验证导致缺jar编译rc1，控制台GBK显示异常，未生成有效r2运行制品。修正26字节及六字节零尾后r3构建229项通过，物理图通过，行仍`UNSUPPORTED_ROW_REPLAY_CONCURRENT_TRANSACTIONS`。r4才得到当前逻辑正样本。失败目录均保留。派生共享行键driver首次误用第7个vector越界，在未写输入/未运行解析前失败；按真实vector清单修正为第1个forward后完成上述拒绝，原stderr/rc保留。

新增cleanout仅接受实际观察的empty16或单ITL2/flags6/26字节两opt profile，验证完整SCN、布局、来源与零尾；不接受任意cleanout。相同结束SCN明确拒绝，但本轮未独立验证该分支。一般并发、真实同一行阻塞、多savepoint/跨块完整行、无提交变化空结果、初始事务恢复、历史schema、基线颁发方认证、主键语义映射、扩展类型、持续capture/ACK与全流程验收仍待证。下步见[执行包](DISPATCH_W5_ROW_ORDER_BOUNDARIES_20261005.md)。
