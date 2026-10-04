# W5 基线之后真实完整回滚的空批次

实际rollback-only有限样本通过：新固定SCN基线之后，一个完整XID两次更新、完整回滚，原始2/补偿2/live0；逻辑变化0、完整状态仍一行，与native及三份独立SQL状态零差异。不以旧“全回滚后另有commit”样本代替本验收。不代表无目标事务静默、持续capture/ACK、接入或发布通过。

源脚本8da2f77，独立比较器/派生脚本d9baca0。生产代码没有改动，复用55c331a已通过229测试的Java17制品，不重跑既有构建及旧验收轮次。jar SHA256 `cf339689693b34acd252652dc16dd39c3545844eaba092f95ffc2d2c8ecf1021`，实际验证在`lab/out/verify-rollback-only`。命令、输入、dump、获取回执、制品与所有输出双SHA关联见[封存清单](evidence/DELIVERY-rollback-only-20261005.json)。

## 一次实际源实验

专属`oraredo-19c-lab-20261004`核owner/image/running、DBID/activation/reset、ARCHIVELOG及无supplemental、两角色generation、仅ID11/concurrent-closed/new-note。实际只读固定snapshot2361733，获取SHA `5f42117ba55fd8768ff2da3fab2b617e761d68d4fc9ff7b0c4879fdef744e466`。

同一自有事务对现有ID11做TXT concurrent-closed→rollback-only-open→rollback-only-end，两次CAS均须影响一行；记录XID10.21.1675后执行ROLLBACK，无commit。OLD_BASELINE ASOF、AFTER_ROLLBACK和FINAL三SQL快照一致，原有行值恢复。未做DDL/config/supplemental或重启，仅专属归档切换/dump；操作已执行一次，不重放。

冻结seq36，240128字节，first2360847/next2361744，SHA `d7c65405e5944a20c87a55b2266be2e41ff2810c760bd0335dbe718bb3c1c86e`。开始原始节点36:466:16:4/5、普通取消36:467:348:2、begin取消36:468:68:2，结束36:468:300，record SCN2361736。native独立证明begin/end都晚于新基线且完整rollback、全部原始节点取消，无live；不是基线之前旧事务过滤。

实际行入口rc0/stdout0、changes0；状态probe完整一行，与实际基线及三SQL快照逐字节零差异。native物理图比较完整XID、位置、UBA、取消顺序，1事务/2原始/2补偿/0live零差异。

## 反例

五份真实seq36副本只改明确字段并重封512字节块校验和，scan均rc0，physical/rows均rc1/stdout0：

| 派生输入 | 实际拒绝 |
|---|---|
| 普通取消marker 5.6→5.7 | UNBOUND_PHYSICAL_WINDOW_OPERATION:11.5 |
| begin取消slot21→22 | MISSING_COMPENSATION_HEADER_ASSOCIATION |
| 最后XR恢复TXT首字节c→x | MISSING_HEAP_COMPENSATION_ORIGINAL |
| 结束marker 5.4→5.7 | INCOMPLETE_TRANSACTION_IDENTITIES:1 |
| rollback flags4→5 | UNSUPPORTED_TRANSACTION_END_FLAGS |

任何一个都不能通过rolledBack标记直接静默跳过。独立比较器另检出泄漏一条取消事件、错误完整state、错误AFTER_ROLLBACK SQL三反例。public events仍DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0。

准备阶段生成器第一次Python引号语法错误，在源码生成和源执行之前失败；原stderr/rc保存，修正生成器后先提交源脚本，再执行唯一源实验。没有源端重试或新生产制品。

后续主攻真实同一行阻塞、index-only拒绝指定分支及完全无目标事务范围审计。主键语义/生产基线认证、历史字典/恢复、扩展类型、持续捕获/ACK、StreamHouse接入、现场与发布仍待证。
