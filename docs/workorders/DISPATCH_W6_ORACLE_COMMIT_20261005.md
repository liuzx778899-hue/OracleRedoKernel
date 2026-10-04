# W6 Oracle接入与目标commit凭证执行包

承接7cd63da的真实实验目标恢复证据；实验SQLite通过不等于产品接入。由本chat继续自主推进，保持现有模型、禁Luna。生产events仍拒绝；本派单不授权共享/生产数据库或驱动部署动作。

## 先核边界

修改StreamHouse前读该仓AGENTS、产品化v1.2方案、STATUS及接口冻结稿，保留他人dirty；这里只读观察过公开SPI/目标端口。Oracle kernel依旧独立Java17，无Spring/JDBC/Flink运行时依赖；接入层单独artifact引用内核坐标，不复制解析/实验源码到产品仓。产品source agent须C++，Python collector只作实验依据。

## 交付及可证伪判据

1. **Oracle envelope与batch契约（P0）**：真实source DB/resetlogs/thread/PDB、对象/schema epoch、完整XID、SCN/RBA/vector、before/after完整性、稳定eventId、输入可信receipt及所有journal摘要。未知source/commit时间不得编造。说明SPI既有LSN字段如何承载/扩展Oracle位置，先拒绝需要未知commitAt的目标路径；不借用SQLServer driverEpoch/IssueSeq来冒充Oracle capture证明。相同输入重复输出身份一致，改源/reset/schema/位点及payload冲突明确拒绝。
2. **严格decoder隔离（P0）**：只接受完整已核batch，整批buffer成功才返回事件；迟到bad before/未知布局/未结束事务/缺journal必须非零rc且零下游写入。不得把lab ROW stdout协议作为生产接口；不得将独立SQL/native比较输入注入事件生产。
3. **目标commit凭证（P0）**：在产品支持方言的独占实验目标中，将事件幂等身份/payload、整批身份和目标DML放同一数据库事务。`applied=true`只有返回值，不能单独证明崩溃后的commit；目标返回或可核的receipt必须包含target identity/batch digest/event count。同身份不同内容拒绝。先制定目标侧技术账本与权限边界，不要求源业务DDL/补充日志。
4. **三位置恢复（P0）**：capture已持久位置、解析完整批次/活跃状态、目标已commit/ACK分别存储。ACK落后可从目标凭证恢复；ACK超过目标commit必须拒绝；目标提交前/后、ACK前/后真实进程kill，核应用次数及完整中间像，不能只看最终行。跨批未完事务不得因capture水位推进而删除原始输入。
5. **真实产品链（P1）**：核准实验配置后，原始journal→Oracle内核→引擎连接器→pipeline→真实目标DML/receipt→ACK；保留输入/jar/输出/配置SHA与rc。默认异常为零目标写入、无ACK晋级；只读dry-run不能填部署/现场通过。先独占测试目标，再正式部署窗口。

## 当前待证与验收

W5生产可信baseline/schema交接、一般事务/类型矩阵、跨批活跃恢复尚未通过。可以推进契约/隔离adapter/目标提交协议，不可因此解除公共events拒绝。目标依然是端到端零源侵入，不以本执行包或实验代码交付标目标完成。

分别回填代码交付、针对性验证、集成验证、部署、现场验收、发布六状态；失败原文追加，禁止重放已提交源fixture动作。任何正式源动作先限定专属实验容器；其他项目/共享环境依据其AGENTS另行核授权和边界。
