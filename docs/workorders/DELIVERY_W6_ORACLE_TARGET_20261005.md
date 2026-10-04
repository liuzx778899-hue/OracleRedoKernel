# W6 真实 Oracle 目标提交与 ACK 恢复交付

模块运行时 6a6ce3e8361d8c036b3da68efa24effdbb6a07f8；实验脚本 937705b0531c4ac46f7e7303c5daa89c0fa0e421。adapter jar SHA256 8103c262891d66b1174e6c89f310eb326d550b7613084d27ee0740cfc020bbf4，独立模块5测试零失败/错误/跳过；内核245已通过轮次保留，本轮不重跑。内核/SPI 固定制品未变。[双次 SHA 清单](evidence/DELIVERY-oracle-target-20261005.json)。

冻结40—42原始 journal 经内核完整事务/行、实际 SPI projection，再由新 JDBC port 写真实 Oracle。目标为专属 oraredo-19c-lab-20261004 的 ORKPDB 内新建 ORKTGT_CMT_20261005 schema，7组共35表。源/目标同实例、跨 schema，不能当独立生产目标实例验收或零数据库配置变更证明。原源业务表无新增 DML，补充日志仍 NO/NO，实例未重启，LGWR 同 PID5601。只使用现有 Java17 image 运行独立 JVM，与 Oracle 共享容器网络；不启动 Flink 服务。厂商 JDBC jar 从专属 Oracle image 复制并校验，未上传 Git；口令文件完全排除证据哈希/提交。

五表协议使业务 CAS、事件审计、批成员、提交凭证原子提交，随后独立持久 ACK。三次真实 Java 客户端 SIGKILL，退出码均137：

| 强杀位置 | 杀后审计/成员/提交凭证 | ACK | 恢复新增 DML |
|---|---|---|---|
| PRE：全部写入后、commit 前 | 0/0/0，业务行回滚 | 无 | 3 |
| POST：commit 后、ACK 前 | 3/3/1 | 无 | 0 |
| ACKD：ACK force/原子替换后 | 3/3/1 | 有 | 0 |

正常及三个恢复目标再次消费同批，均0新增，ACK 字节不变。六真实拒绝：第二消费者 ORA-00054；第三次 CAS 匹配失败导致前两写及审计全回滚；有效 ACK 超过目标提交；目标身份错误；ACK 内容冲突；已有事件完整像被改后内容冲突。拒绝均无成功事件输出。

独立比较器用原三源提交 SQL、基线 ID11/NOTE 构造六个中间像，与实际目标审计、成员次序、凭证、最终行和精确 LF ACK 比较。PRE/POST/ACKD 三个当前目标 fresh SQL 零差异；正常 NORM 使用破坏前已保存快照零差异。NORM 在最后反例中被有意污染，当前不能宣称健康；其 actual SQL 比较失败，加漏成员/错像/错 ACK 共4比较器拒绝。最终只读审计未重跑强杀或 DML，实验 helper 全部退出。

首次密码长度 ORA-00972 及 Windows CRLF 导致预期 ACK_WITHOUT_COMMIT 实际提前 ACK_CONFLICT 两次失败原文保留。仅分别续跑未执行阶段，LF 超前 ACK 的真实目标拒绝已补证；没有重复已通过轮次。失败执行的早期源码变体不冒充最终脚本树；命令、SQL 脱敏文本及 rc/stdout/stderr 保留。

本轮通过限定 raw→内核→SPI→专属 Oracle 目标 commit→ACK 及客户端崩溃恢复。没有通过完整 StreamHouse pipeline、独立目标实例、生产可信基线/历史字典、跨批 checkpoint/保留策略、C++ agent、实时高压、断电、独立正式评审、部署、现场或发布；公共 decoder 门禁继续关闭。[接口](../interfaces/oracle-target-receipt-v1.md)。接续主线：不同/重叠批次全局事件身份与内容、成员凭证验证，再消费 checkpoint 与 Oracle 类型化窗口/位置契约。

封存首次逐字节断言失败已保存：最终 driver 工作树为 CRLF、Git archive 为 LF，规范化内容一致。分别冻结实际执行字节及 Git 树、双次哈希，不宣称二者逐字节相同。
