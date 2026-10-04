# W5 完整闭合批次的零变化结果

本例验收：归档内所有目标物理事务完整闭合，结束早于一次新获取的完整固定SCN基线，返回零条变更并保留完整基线状态。无目标事务的归档仍拒绝；不是W7持续静默、活跃事务续接或ACK验收。

源码761e2e0先删除无变化统一拒绝；55c331a补回“基线之后存在存活索引但无heap证据”的拒绝条件。最终运行时`55c331a1d7ffeaf9e1b78a76e52222668a73d792`，`release-closed-batch-r2`，Java17 229测试/0失败/错误/跳过；jar SHA256 `cf339689693b34acd252652dc16dd39c3545844eaba092f95ffc2d2c8ecf1021`。实验state probe06e07ee独立输出完整行状态，不作为生产事件输入。最终[清单](evidence/DELIVERY-closed-empty-20261005.json)关联所有输入/制品/输出SHA、rc与旧制品/最终制品对照。

## 实际只读样本

`lab/closed_batch_baseline19.py --execute lab/out/oracle19-closed-batch-20261005`，专属`oraredo-19c-lab-20261004`先绑定owner/image/running，复用真实只读固定SCN获取器，基线SCN2360835，实际获取SHA `5bbba96a2db3a4687a81bfb2dd15cb59d50d15d717a052181ee68e0fb64366f3`。独立ASOF与FINAL SQL均一行ID11/concurrent-closed/new-note。没有DML/DDL/config/supplemental或重启，仅专属归档切换与dump。没有编辑旧snapshot冒充一次实际获取。

连续输入seq33/34/35。其中33/34与此前冻结样本SHA完全一致；新seq35为49233920字节，first2349226/next2360847，SHA `779f0445bd00cb0a1fddb4055e685f2ef022bb94257be11d132746f8730d0349`。新基线在整个输入覆盖内。原始archive/dump和大型制品只保留本地ignored目录。

独立native发现三笔完整目标XID：9.16.599结束2349206，10.33.1644结束2349213，10.29.1644结束2349219，均早于新snapshot。物理图原始6/补偿0/live6零差异；这些live属于基线之前，不能当新变化输出。行入口stdout空、rc0、明确changes0；状态probe输出完整一行，与实际baseline和两独立SQL快照零差异。

最终r2再次运行physical/rows/state；三个stdout与初版逐字节相同，关联同一冻结输入/两个实际jar SHA。独立native比较器使用初版已经通过的对照结果，通过该字节等价证据复用，没有伪造一次最终jar的native命令。最终r2的旧并发四行、seq30两行、seq31全回滚抑制后一个UPDATE、seq32部分回滚三行均重新执行，native/SQL零差异。

## 拒绝与边界

- 新基线配仅seq33/34：仍`BASELINE_SCN_OUTSIDE_INPUT_COVERAGE`，rc1/stdout0。
- 仅seq34缺begin、仅seq33缺end：仍物理生命周期拒绝，rc1/stdout0；不能通过更晚的基线过滤掉不完整事务。
- 仅seq35无目标事务：仍`NO_PHYSICAL_OBJECT_TRANSACTIONS`，rc1/stdout0；尚无同等范围的未知向量审计证据，不报静默成功。
- seq34真实cleanout count派生篡改配新snapshot及seq35：仍`UNSUPPORTED_SINGLE_ENTRY_BLOCK_CLEANOUT`，rc1/stdout0，即使目标事务结束早于基线也不跳过物理校验。
- 实际snapshot配错误获取digest仍拒绝；比较器新增“凭空多事件”和错误完整state两反例均rc1。
- 同件jar原始初始跨snapshot、晚before失配、同rowkey派生冲突、相同结束SCN拒绝回归通过，均stdout0；public events仍拒绝。

只有基线之后无存活节点或所有目标事务结束早于基线时才允许本例空结果；index-only live的保守条件已保留，但本轮尚无独立指定分支反例。基线之后完整rollback零live的单独空批次真实样本尚待执行，不以“全回滚后还有一个commit”的旧混合样本代替该验收。初版审查补条件后的两个构建和全部初版输出保留；一次启动命令误写Python路径导致shell无法启动，无源动作，随后正确路径启动最终构建。

接续：真实rollback-only零live空批次、真实同一行阻塞；完全无目标事务的静默需补覆盖与未知opcode证据，再推进W7连续捕获/续接/ACK与W6接入。生产基线信任、历史字典/恢复、扩展类型和现场/发布仍未完成。
