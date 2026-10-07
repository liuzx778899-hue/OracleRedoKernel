# W6 实际产品历史持久恢复集成

同一私有候选 `e0a79dd84abdc0009684f136fbc2b101aa82e7af8e2da300dffdb12ce3ff34af`（代码60f56f5、原完整754/0/0/0，不重复构建）实际通过认证HTTP恢复任务→两完整历史UPDATE→目标原子提交→控制checkpoint→持久ACK。服务端批准绑定引用统一历史恢复文件SHA `486299c0e2317cf502ba9d56fd5d80d08c24e48e6bc634159ca1b249b2ade5a7`；恢复文件来自先前独立JVM实验，只复制到新运行目录，原件不改。

专属helper `ork-recovery-http-20261007`/37264、控制库 `ORKRCVCTL_20261007`、目标前缀 `ORKTGT_CMT_20261005.SHRC7`、窗口 `history-70-71-recovery`。原获取100747510573780992/SCN2566695复制登记，完整metadata、登记时间与来源字段相等；不新获取、不晚SCN补洞、不重放任何源业务动作。原冻结70/71输入只读挂载；共享StreamHouse未改。

| 实际验证 | 证据与判定 |
|---|---|
| 恢复文件篡改 | 原文件追加受控毒化；HTTP任务拒绝 `ORACLE_RECOVERY_CHECKPOINT_HASH_MISMATCH`，REFUSED/rc2/事件0，目标0/0/0、CP空、window PENDING、gate0；原失败及毒化文件保留 |
| 精确恢复后同任务续接 | task101313799314829312，published batch101313958895513600；两有序event ID/payload/完整前后像/操作逐项与原冻结projection一致；target APPLIED2/MEMBERS2/COMMITS1，schema序列凭证相同 |
| 控制与ACK | PUBLISHED、CONSUMED、task DONE；完整oracleEvidence及targetCommit逐字段相等；ACK字节与CP JSON相同，ACK SHA `1b6559c9cf34c750dd63209c30908f0e6919e5f2fb29649867dae080b547ee49` |
| 同任务重复 | 实际异步终态审计windows0/published0；目标、账、CP、ACK不变 |
| 提交后强制退出及重启 | helper SIGKILL退出137，重启运行SHA相同；同任务resume后windows0/published0，所有事件账/提交/CP/ACK不变；这是提交后进程恢复，不是提交中途崩溃或断电 |

[234项双SHA清单](evidence/DELIVERY-recovery-http-20261007.json)包含命令rc、stdout/stderr、SQL、批准绑定、原与毒化state、各阶段proof、ACK及最终日志。前候选113、统一内核100、原产品356逐项SHA未变。secret文件不哈希、不纳管。最终owned helper停止（false/143/exited），专属源继续运行，NO/NO及旧业务表/原获取登记保持。

失败追加保留：首次启动及重启health在服务就绪前connection refused；同一已确认运行helper随后ready，不重复创建。首验证器在恢复完成后读取当前ACK目录，误用历史“无ACK”条件，原AssertionError保留；修正为当前ACK修改时间晚于已记录毒化阶段结束，并以历史CP空/零gate/零目标证明拒绝。首次重复阶段SQL在异步done审计写入前读取，原AssertionError与原SQL保留，仅等同一任务后续终态，不重新resume凑通过。未修改产品代码。

仅本有限历史恢复产品链通过，D1仍进行中。缺失文件、可信SHA一致但内部组件混配的实际产品反例、新恢复候选晚失败/ACK失败，以及真正活跃事务跨发布持久恢复尚未验证；原产品晚失败证据不移作新恢复入口的证明。源raw前缀仍必需、pending SYS/真实pending index与一般矩阵待证。目标同实例跨schema、capture_complete=false；独立评审PENDING、生产部署/现场/发布未通过。

下一执行：新目录保持此封存只读；先补新恢复候选发布前缺件/组件混配及晚失败/ACK故障，再proper产品V3获取后一次新受控跨日志活跃事务，验证跨发布checkpoint/恢复。不使用73/74手工baseline冒充产品获取，不重新跑已过轮次。D2—D5及完整goal继续保留，全部Oracle目标后才查Codex卡顿并关闭automation。
