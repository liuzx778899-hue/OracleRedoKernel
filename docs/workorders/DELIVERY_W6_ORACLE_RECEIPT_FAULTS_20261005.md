# W6 真实目标凭证破坏、拒绝与恢复

实验源码 833b79e2273ef7464348fe2d38be4d232b266eb8，生产模块6a6ce3e及 adapter8103c262…、kernel15b07ddf…、SPI93e451fe…未变。逐件核原制品/worker SHA；未重建、未重跑既有5/245测试或SIGKILL轮次。[双次SHA清单](evidence/DELIVERY-oracle-receipt-faults-20261005.json)。本轮对象仅专属同实例已有目标 ORKTGT_CMT_20261005.MIXD，不是源业务表。

已有完整批 commit/ACK 下，分别条件修改且 SQL%ROWCOUNT 必须为1，再执行真实Java/JDBC消费者：

| 实际破坏 | rc1原因 | 恢复后新增DML |
|---|---|---|
| 删除完整批ordinal2成员 | ORACLE_TARGET_BATCH_MEMBERS_INCOMPLETE | 0 |
| 最后一事件payload SHA改为64个0 | ORACLE_TARGET_EVENT_CONFLICT | 0 |
| 完整批commit eventsCount 3→4 | ORACLE_TARGET_COMMIT_CONFLICT | 0 |
| IDENT sourceBinding改为different-source | ORACLE_TARGET_IDENTITY_MISMATCH | 0 |

四拒绝stdout均0，旧ACK字节不变；每项拒绝后的目标行集合与破坏前该项快照一致，没有DML或凭证补写掩盖损坏。每项条件恢复之后所有业务行、事件完整审计像、批成员、提交凭证及IDENT恢复原行集合，消费成功但0新增，ACK相同。最终MIXD健康3审计/5成员/2凭证，原source identity/业务行与NO/NO补充日志不变，LGWR5601保留/helper退出。

首次driver在缺成员已拒绝、指定成员已恢复后，逐字节快照断言失败：原SQL只ORDER BY ORDINAL，不同batch同ordinal的顺序不固定。原输出行集合实际一致，失败原文/初始执行源码保留。续接改为排序完整行（保留重复行数量）并纳入IDENT；跳过已做删除/拒绝/恢复，只执行该项未完成恢复消费和剩余三项。最终与初始执行源码分别冻结，不混称同一轮制品。最终脚本对应Git树规范化内容已核。

这完成消费checkpoint前目标拒绝门禁，不表示checkpoint已实现。下一步按[三位置checkpoint执行包](DISPATCH_W7_CONSUMER_CHECKPOINT_20261005.md)实现独占、完整原始窗口与可信scope绑定、实际target核验后推进、半写保全及真实消费者SIGKILL恢复。仍不授权raw删除，不解开公开decoder；任意活跃窗口/生产可信历史字典/完整产品pipeline/独立目标实例/部署/现场/发布继续待证。
