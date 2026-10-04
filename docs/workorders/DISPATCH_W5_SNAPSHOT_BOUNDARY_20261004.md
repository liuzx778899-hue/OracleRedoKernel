# W5 初始活动事务跨snapshot真实边界

状态：已按一次性真实实验完成有限拒绝门禁，见[边界交付](DELIVERY_W5_SNAPSHOT_BOUNDARY_20261004.md)；不重复执行以下已提交动作。typed世代/真实获取到新DML的有限链路已交付；不是整个W5完成。核心比较的是begin/end Record SCN，不把Record SCN当通用SQL事务开始时刻。接续[INSERT/DELETE](DISPATCH_W5_INSERT_DELETE_ROWS_20261004.md)。

1. 专属既有项目表先只读确认当前ID11/TXT typed-epoch/NOTE new-note、heap73073→73076/index73074→73075。新输出目录，获取/操作脚本先提交；不能重跑seq27的CAS事务。
2. 一个受控持久SQLPlus会话执行首个CAS UPDATE后保持事务未提交，记录完整XID与READY握手。另一受控连接归档当前日志以确保首批redo已持久化，再做新的只读固定SCN基线获取；读到的必须是旧已提交值，不拿未提交连接自己的当前行当基线。
3. 保留获取进程实际交付的snapshot SHA。原会话随后第二次受控UPDATE/commit；再次归档，冻结覆盖起止的连续全部文件及native dump/SQL truth，记录begin/snapshot/commit的真实时序。失败保全、仅对本会话未提交事务做必要rollback，不重放已提交动作、不修改业务/共享环境。
4. 首先独立检查完整输入下物理图、开始/结束身份和native字段，再核`beginRecordScn<=snapshotScn<endRecordScn`是否成立。如果物理图因未知控制等拒绝，报告实际原因并修证据支持的解析，不能当snapshot拒绝功能验收。
5. 当前不支持初始活动事务的策略下，整件`readVerified`应明确BASELINE_CROSSES_TRANSACTION_BOUNDARY、rc1/stdout0。只首批缺end、只末批缺begin、晚/当前SCN、错误获取文件等分别反例。若实测Record SCN不足以识别边界，保留误接受/反例，改门禁依赖证据，不靠比较器或延迟启发式猜测。
6. 任一错值即使此前已有合法变更也必须整批无输出。合成边界/缺项验证与真实源证据分列；基线来源可信交接、历史schema/DDL/ABA仍独立待证。

达成后接续INSERT/DELETE/rollback/并发完整行与广泛类型，再做持续capture/ACK、StreamHouse和独立现场/发布。目标不缩为此单fixture；public events继续保持未验收拒绝。
