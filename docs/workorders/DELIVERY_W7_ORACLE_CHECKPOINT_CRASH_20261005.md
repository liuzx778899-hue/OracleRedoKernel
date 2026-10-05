# W7 新目标 DML→checkpoint 与四处真实消费者强杀

生产运行时ec3b4306ce088b93fedc5794b4ffb57f225a2306不变；lab b2410022b1ac899eeff9324b9636150a64589ff7。adapter6f06247e…、kernel ea9f58c6…、SPI93e451fe…与上一交付逐件SHA一致，生产源码/jar不改，15模块/245内核通过轮次不重跑。[双次SHA清单](evidence/DELIVERY-oracle-checkpoint-crash-20261005.json)。

专属同实例目标已有账号下创建CPNO/CPBE/CPAF/CPTM/CPAK五namespace共25表，原源业务表/NO-NO补充日志不改、LGWR5601保留。目标实验DDL/DML生成同实例日志，不当生产零数据库配置证明或独立生产目标实例验收。输入仍为保留原始40—42 capture及可信实验baseline，没有源DML/切换/采集轮次重放。

未插桩正常路径 CPNO：prefix40—41实际2新增，checkpoint seq41/7块/end2366749及prefix ACK；full40—42只1新增，checkpoint前进42/9/end2366759，prefix ACK不变；完整批及旧前缀再消费均0新增，最大前沿保持42，checkpoint字节不变。目标最终3审计/5成员/2commit，完整before/after六像与原三源提交SQL零差异。

| 独立消费者强杀边界 | 杀后目标审计/成员/commit | 杀后消费状态 | 恢复新增DML |
|---|---|---|---|
| CPBE：target commit前 | 0/0/0，未提交写回滚 | 无checkpoint/ACK | 3 |
| CPAF：target commit后、checkpoint前 | 3/3/1 | 无checkpoint/ACK | 0 |
| CPTM：checkpoint.tmp全部write+force后、replace前 | 3/3/1 | 完整tmp，无checkpoint/ACK | 0 |
| CPAK：checkpoint replace+目录force后、ACK前 | 3/3/1 | 完整checkpoint，无ACK | 0 |

四次均父进程核Docker client ID及owner/scope/fixture/running之后只SIGKILL Java helper、实际docker wait137，杀后独立SQL核验；日志stdout0，原Oracle服务不动。各恢复完成后再消费均0新增。CPTM实际强杀遗留temp重新核原始输入/目标凭证、保留原字节为retained，再完成checkpoint/ACK；CPAK原checkpoint字节不改并补ACK。CPBE暂停时第二独立进程在输入/target前实际ORACLE_CONSUMER_ALREADY_RUNNING，rc1/stdout0。

两处target提交等待点用lab JDBC代理只在业务ROW UPDATE的commit前/后阻塞；两处文件force等待点使用lab Java agent、JDK17内置ASM在生产checkpoint.persist的两个已有force调用返回后插入阻塞。仅实验进程载入agent；生产jar未改，记录原class SHA（与jar条目一致）及插桩class SHA。CPNO正常/全部恢复与重复不载agent；两处fsync强杀是实验插桩证明，明确不当无插桩现场强杀、数据库进程崩溃或断电证明。agent源码/制品留独立lab，无生产依赖。

主driver全程rc0。独立第二只读audit未重跑DML/强杀：五当前target SQL完整快照与原独立六像/成员/receipt匹配；checkpoint每批source/schema/baseline/SCN/leaf、所有capture序号/摘要、关闭block/sequence/endExclusive、事件数逐项对原capture receipt/native已验证输入，零差异。三个错SCN/source/captureSHA的比较反例，即使重算合法checksum仍检出；SHA不替代可信字段验证。各batch ACK内容精确UTF8/LF核验，client全退出。

本轮通过有限原始窗口新DML→target commit→checkpoint→ACK及四处客户端进程崩溃恢复。仍不授权删raw；缺历史输入/旧target元数据发布态拒绝矩阵需接续，提交回包丢失/数据库崩溃/断电、任意活跃跨窗/生产可信历史字典、实时高压、Windows/ASM、全StreamHouse产品pipeline、部署/现场/发布仍未验收。下一步恢复负门禁补齐缺原始历史input与已发布target凭证损坏，随后接Oracle实际窗口/位置SPI及可信字典主线，避免把有界实验当通用产品。
