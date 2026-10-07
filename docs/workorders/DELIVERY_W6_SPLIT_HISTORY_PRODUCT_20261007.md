# W6 完整历史事件产品链候选

指定私有 StreamHouse worktree 产品提交 **1755975**，不可变 kernel `0.1.0-split-history-a84db1a3ff8f`、adapter `0.1.0-split-history-fbf1d30c8072`。adapter **26/0/0/0**、产品完整 reactor **750/0/0/0**；从完整产品提交树构建一次，实际运行同件 `ef852e719862ee41c552fb6de9370af66f9229b56faa92bb7b2dfce02fc5ceb7`。嵌入 kernel/adapter SHA 全等，SPI 全 class 字节等于已验证接口制品。未复制内核源码或实验过程到产品仓。

## 原输入与实际链路

复用实际产品获取记录 `100747510573780992` / SCN2566695 / baseline SHA323fd35a，原冻结70/71及既有完整两UPDATE投影。快照以只读挂载保留原 `/evidence/snapshots` 路径；没有重新获取、回填晚SCN、源fixture重放或日志配置变更。

专属源 `oraredo-19c-lab-20261004` 上自有控制 schema `ORKV3CTL_20261006`，新目标前缀 `ORKTGT_CMT_20261005.SHP7`（同实例跨schema），helper `ork-split-history-product-20261007` / 37262。先核旧 helper 标签、运行制品 SHA 和空绑定/事件账，再停止旧 helper，启动新候选；自动消费关闭，只通过认证 HTTP 驱动指定窗口。

| 判据 | 实际结果 |
|---|---|
| HTTP任务 | `101213916868202496`，原窗口 `history-70-71`；DONE |
| 完整业务事件 | 两UPDATE，原事件ID/payload/完整before-after/schema4→3/隐藏NOTE与EXTRA_HP NULL，逐成员独立SQL零差异 |
| 目标账 | APPLIED2 / MEMBERS2 / COMMITS1；schemaSequenceSHA241d24ac绑定原batchSHA832ae6e6 |
| 控制账 | 原批`101213917652537344` PUBLISHED/rc0/event2、窗口CONSUMED、事件ledger2 |
| checkpoint/ACK | 完整source、两capture、baseline、逐事件XID/SCN/位置/schema及目标receipt全部等于冻结投影；ACK与checkpoint精确字节相同，SHA1489e4484bf0061722c39e4f20e8c0408fac76bf0d962b7c1563855f64b68dc4 |
| 重复与同件重启 | HTTP resume 的异步完成审计均DONE/windows0/published0；目标/控制/checkpoint/ACK精确字节不变，运行jar SHA相同 |
| 凭证反例 | 仅自有目标COMMITS.SCHEMA_SHA改零；实际恢复`ORACLE_TARGET_COMMIT_CONFLICT`，完整异常与fail-closed审计保留，目标行/事件/成员、既有checkpoint/ACK无推进；原已提交任务仍DONE，不能写成任务FAILED |
| 精确恢复 | guard限定原batch/错误值/单行后还原schema序列SHA，同任务恢复异步完成；成功账/CP/ACK不变，失败审计保留 |
| 源与结束状态 | NO/NO；旧业务11/blocking-closed/new-note、新fixture0/history-product-restored/anchor保留；旧获取ACQUIRED_ONLY不变；新helper停止false/143/exited，未停止源容器 |

本轮完整HTTP/目标/checkpoint/ACK只证明该有限冻结窗口。capture_complete仍false，不是正式C++在线采集验收；目标仍同实例跨schema，不是独立目标实例。受控预建nullable列并集，不是任意DDL自动部署。

## 证据和后续

[双SHA与写后核验清单](evidence/DELIVERY-split-history-product-20261007.json)，原1181/549/407封存项保持SHA与大小不变。新输出目录 `lab/out/split-history-product-20261007`；secret不哈希、不上传，所有已封存目录不得再写入。

真实凭证反例失败按预期保留；首比较器以Windows默认GBK读取UTF-8产品日志失败，原脚本/UnicodeDecodeError保留，后改为ASCII异常字节匹配，只复用原HTTP/Java输出。初次状态采样任务RUNNING/目标0是尚在运行，随后原任务完成；没有因观察未完成而重新提交或重放源。

六状态：代码交付=有限历史产品候选；针对性验证=模块26/产品750通过；集成验证=该窗口HTTP/目标/CPACK及重复/重启/凭证反例通过；生产部署=未开始；现场验收=待证；发布=未开始。PR#13 draft依赖#12、独立评审PENDING，不自审通过。

下一开发执行包：新输出目录接续历史链晚失败整批回滚/绑定篡改及跨窗活跃事务恢复；正式C++采集与平台/高压/故障、一般完整DML/类型及W8—W11仍待证。部署与生产验证按用户安排协同；完整原goal继续active，Oracle全部目标完成后才查Codex卡顿并关闭automation。
