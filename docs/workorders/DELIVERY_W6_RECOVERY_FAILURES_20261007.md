# W6 新恢复入口真实失败与恢复

同一已提交候选product `e0a79dd84abdc0009684f136fbc2b101aa82e7af8e2da300dffdb12ce3ff34af`（代码60f56f5、原完整754，不重复构建）实际完成缺失文件、带可信摘要的组件混配、目标第二UPDATE失败及ACK存储故障四类反例；恢复后完整历史事件/目标/控制CP/持久ACK一致。没有新增生产代码。

新实验目录 `lab/out/recovery-failures-20261007`，helper `ork-recovery-failures-20261007`/37265，control `ORKRCFCTL_20261007`，target `ORKTGT_CMT_20261005.SHRF7`，window `history-70-71-recovery-failures`。只读原早V3可信登记100747510573780992/SCN2566695、原冻结70/71及原统一历史state；获取登记完整metadata相等，0新获取、0源业务重放/配置变更，禁止晚SCN回填。

| 阶段 | 实际判据与结果 |
|---|---|
| 恢复文件缺失 | 只移动新实验副本；task101316926910857216明确REFUSED/rc2/0事件、原因 `/evidence/recovery.state`；目标0/0/0、CP空/window PENDING/gate0/ACK0 |
| 身份组件混配 | 历史state的I组件换为另一冻结窗口I组件，服务端批准登记摘要与混配文件实际一致；内核仍拒绝 `HISTORICAL_RECOVERY_STATE_CHANGED`，REFUSED/rc2/0事件、目标和CP/ACK零推进；不只是外部SHA门禁 |
| 第二UPDATE晚失败 | 精确恢复原state/批准摘要后，受控目标CHECK拒绝第二UPDATE，原异常 `ORA-02290 SHRF7_LATE_CK` 与Caused by完整保留；两条事件解析完成，目标行/APPLIED/MEMBERS/COMMITS整批回滚为0，控制CP/gate/ACK零推进；失败batch101317504911114240 |
| ACK目录阻断 | 仅撤去owned实验约束，ACK路径以受控普通文件阻断；目标2/2/1、控制PUBLISHED/CONSUMED/CP已提交，taskFAILED并明确 `ORACLE_ACK_PENDING`，原 `FileAlreadyExistsException` 完整保留；published batch101317782108471296，不能因ACK失败抹掉提交事实 |
| 同任务补ACK恢复 | 移走阻断文件后resume，taskDONE；原两event ID/payload/完整4→3列前后像/操作逐项相等，目标commit及完整oracleEvidence/targetCommit一致，ACK与CP字节相同。故障前后目标行、事件账、成员、commit、全部batch、CP、window、ledger、gate逐项字节相同；新增batch0、target不重写。旧两失败文件引用与原reason不变 |

[353项双SHA清单](evidence/DELIVERY-recovery-failures-20261007.json)封存每命令rc/stdout/stderr、SQL、同一任务五阶段proof、恢复与混配文件及批准绑定、原异常文件/关联、ACK与最终日志；前234+113+100+356逐项未变，secret不哈希不纳管。新验证动作首次复用state标签触发驱动自身防覆盖AssertionError（任何动作前），原文/rc1保留；只修verify执行标签与被验证stage分离，原state未覆盖。各场景等待同一任务终态审计后取证，不重复resume凑通过。

最终helper停止false/143/exited，专属源仍running、NO/NO与旧两业务表保持，metadata相等。此目标仍是同实例跨schema，capture_complete=false；不能当独立目标、正式采集、生产零侵入配置证明、现场验收或发布通过。独立评审PENDING。

D1仍进行中，但新候选历史恢复入口的文件摘要毒化（前轮）、缺件、组件混配、晚失败、ACK故障及精确恢复、重复和提交后SIGKILL恢复已有实际产品证据。下一主线集中于**真正活跃事务跨发布持久恢复**，不再重复已过历史反例：proper产品HTTP V3获取→一次新受控跨日志事务→首次窗口pending不发布、完整持久状态/可信登记→进程硬退出→后缀续接→完整事件/原子目标/CPACK；不得用手工73/74 baseline冒充产品获取。pending SYS/真实pending index等一般矩阵仍在D2，原全部goal保留。
