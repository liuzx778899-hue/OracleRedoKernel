# W7 有界消费恢复与真实目标提交/ACK

实验源码7cd63da，生产Java内核仍b712d56/240测试，jar SHA256 `7d4345f1f79c7b6eeb75a9dc1702308a0162f72faa4d4cbf6c3987d77b65d1fc`。未重建内核、未重跑已通过capture轮次或源DML。新增目标恢复实验，不宣称W6真实接入、现场验收或发布。

## 输入、目标与粒度

原始关闭journal40—42及既有可信baseline2366727，通过同件Java的`readCaptured`再次生成完整缓冲三UPDATE，输出与既有通过件逐字节相同。新消费任务的输入为该受控lab输出；没有把SQL truth/native dump改作生产事件输入，没有开放公共events。baseline SHA256为`646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf`。

目标是本机独占SQLite WAL/FULL实验库，实际执行SQL事务，保存Oracle三列完整raw-hex镜像，不是文件假写入，也不是StreamHouse支持方言的验收。源Oracle无DML/DDL、归档或配置动作。表、目标身份及锁仅在新的`lab/out/consumer-commit-20261005`创建。

这批三笔源事务在**一个目标事务**中重放，非每笔源事务独立目标提交；不是跨批无限流。写入顺序：取得目标侧实际Windows文件锁 → 核可信spec与所有输入SHA → 完整输入缓冲 → 开目标事务 → 每行before严格CAS及应用身份记录 → 同事务写批次commit凭证 → 目标commit → 临时ACK文件flush/fsync → 原子replace。目标事务中不修改源库。

事件身份在本实验绑定同源身份、baseline世代、完整RBA/vector、完整XID及物理row key；batch摘要另绑定目标、输入制品摘要与完整payload。重放先核目标commit及逐事件应用记录，内容矛盾拒绝，不凭ACK文件或最终行相同跳过。三个UPDATE最终恢复原值，所以应用次数/中间before-after必须独立核验。该身份规则是冻结实验协议，生产schema epoch与目标身份交接尚未冻结。

spec SHA由实验控制进程交接，目标幂等账本/ACK缓存属于该实验；不认证恶意管理员重签整个spec或重造目标账本。ACK是完整已提交batch身份，**不是raw读取offset、capture next_sequence或解析进度**。未实现跨批活跃事务恢复/留存删除。

## 实际进程强杀与重放

父进程对三个真实Windows消费者进程执行`Popen.kill()`/TerminateProcess，实际rc均1；不是Python异常模拟，不杀Oracle。三个实验库分别初始化，不复用损坏测试库。

| 强杀位置 | 独立连接查看目标 | 新进程恢复 | 后续重复 |
|---|---|---|---|
| 第2行及应用记录后、目标commit前 | 0应用/0commit/无ACK；两行更新全部回滚 | 3新增应用，1commit后ACK | 0新增应用 |
| 目标commit后、ACK前 | 3应用/1commit/无ACK | 0新增应用，从目标commit核证后补ACK | 0新增应用 |
| ACK原子替换后 | 3应用/1commit/有ACK | 0新增应用；核目标账本与ACK一致 | 0新增应用 |

正常路径也为3应用/1commit，再次消费0新增；每个库`PRAGMA integrity_check`返回ok。只证明实际进程死亡与数据库恢复，不证明宿主断电、SQLite介质故障、ACK目录断电持久性或生产数据库提交保证。

## 拒绝及独立对账

八项消费者反例rc1/stdout0：另一实际进程持目标consumer锁、最后一条before不符、合法样式ACK但目标没有commit、目标身份不同、外部spec摘要错误、输入摘要错误、ACK内容冲突、commit存在但最后应用payload损坏。迟到before反例在前两次UPDATE后发生，整个SQL事务回滚，应用/commit均0，ACK不存在；失败ACK/应用凭证原件保留。

独立比较器不调用消费者解析或identity函数，直接只读三恢复目标库，与既有三笔源端提交后SQL及原baseline核完整before/after、最终行、应用ordinal/身份、批次commit与ACK。三目标库各3条应用/1凭证、差异0；原始源SQL提交次数仍只有3，不补造9笔业务执行。四项比较器反例检出：真实损坏目标、漏应用、错误after、错误ACK。

首次源码封存发生Git archive自动CRLF转换，逐字节断言失败；失败记录及原archive保留。仅以`git -c core.autocrlf=false archive`补封，四个实际执行源码与新完整归档逐字节一致，已通过实验不重跑。消费者/主driver执行SHA分别`46c7127dab933a1cc39944f29e708f15982403a9d81487ae4af3c2208cefb6d2`、`e6c5046d88e7c6beaf59f1c2a16f53b5df7e0a6b6891a8e9cf099aa710139058`。

[双次SHA清单](evidence/DELIVERY-consumer-commit-20261005.json)关联输入、制品、执行源码、每项命令/完整stdout/stderr/rc、强杀标记、真实SQLite库、提交/ACK和独立对账。样本和目标文件仅在lab/out，未上传。

## W6发现与下一步

只读核本地StreamHouse公开SPI与实现：`CapDecoder`要求非零rc零事件；`EventEnvelope.TransactionRef`字段仍用LSN命名，Oracle须明确SCN/RBA语义；`TargetApplyResult`只有applied/reasons，不提供目标同事务commit凭证；现有stream append/SH_STREAM_ACK是接收分片字节确认。不能将这些返回值直接当本实验目标commit ACK。

接续按[W6接入执行包](DISPATCH_W6_ORACLE_COMMIT_20261005.md)冻结Oracle envelope/输入batch/目标commit凭证契约，再实现隔离连接器及真实支持方言的集成故障验证。源agent产品约束为C++单进程，本Python collector/consumer只属实验。W5生产可信交接、历史字典、跨批活跃事务、实时采集和现场/发布仍待证；公共events继续安全拒绝。
