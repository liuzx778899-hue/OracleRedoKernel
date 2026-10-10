# 时间变体字节解析首包

当前五项总目标继续，D5搁置。本包取得真实时间变体样本并补BC、偏移时区解析/BC类型投影；未关闭时间类型完整产品链。

专属19.3实例新增表 `T_D4_VARIANTS_20261011`，9列/3行，覆盖DATE、TIMESTAMP(0/3/6/9)、正负/零偏移、Europe/Paris冬夏时及LOCAL TIME ZONE。独立环境记录DBTIMEZONE `+00:00`、Oracle时区文件32；未开启补充日志。源动作一次，raw109冻结后本地开发，source-stop rc0、Docker Desktop停止。首次脚本缺import的失败在任何数据库动作之前，原文保留；后续原始拒绝及原旧9列门禁测试失败也保留，不删除凑通过数。

实现：Oracle BC年不含年0，解析映射到Java ISO纪年（44BC→-43、1BC→0），初始编码反向恢复；保留纳秒。偏移型TSTZ按记录UTC＋显式正/负/零偏移解析，拒绝无区域上下文的region编码。九列非LOB IRP复用已验证的两字节null bitmap布局。BC DATE/TIMESTAMP适配器保留类型、ISO纪年和九位小数。格式依据为独立raw/native/SQL样本，OCI的[DATE字节格式说明](https://docs.oracle.com/en/database/oracle/oracle-database/26/lncpp/data-types.html)作为公开资料参考；未使用外部实现源码。

证据：`lab/out/temporal-variants-20261011/verify-r3/proof.json`，raw行前后undo绑定后3行/24时间字段与独立SQL DUMP差异0；DATE、各精度TIMESTAMP及偏移TSTZ共18个值/SQL真值语义差异0。35项受影响内核回归与2项适配器投影测试均rc0/失败0。

| 输入/制品 | SHA256 |
|---|---|
| raw109，4696064字节 | `9a73f5bcf76a9350ca599ee6c22636e1c49d138e75bd72ad82fae0afee089cfe` |
| 内核开发候选 | `03135a33bd2e3d015275905e8827c0d57d21a9abe3546c6cdf81245b2bf73f28` |
| 适配器开发候选 | `d2c561a4d752a64cee65c92b8b25778ce4b9dff33b3a76a9ee199e2074958525` |

复现：Java17构建内核后 `py -3.12 lab/temporal_variants_verify.py verify-next`，新输出目录，读取冻结raw和独立真值；不能重放 source 脚本。SQL/native只作尺子，均不成为事件输入；24字节核等不等于24种类型完整事件链通过。

接续：获取层目前按LocalDateTime编码，需保证BC、纳秒及区域/本地时区获取无信息损失；region时区、LTZ源上下文、完整事件、目标提交和HTTP checkpoint/ACK待补。当前适配器只证明BC类型投影，实际BC JDBC写入尚未验证。完成五项后统一更新固定依赖、合并main及部署清单，不要求执行方现在重新部署本包。ARRAY部分行补偿/pending恢复、同commitSCN恢复发布、LOB历史/代际/安全回收继续，D5不作为门槛。

## 区域/LTZ上下文接续（2026-10-11）

只读复用原三行/原raw109，OracleResultSet原生TSTZ/LTZ datum字节在+00:00和+05:30两个独立会话下均与原raw核等：18字段/差异0、源DML0；确认COL$181/231/property0，源DBTZ +00:00/timezlrg_32.dat，Oracle JDBC与源库时区名称交集取得595区域映射，382=Europe/Paris。没有通过Java TZDB推断Oracle DST。源停/helper自动删除/Desktop停止。

内核新增OracleTimeZoneContext及V4可信基线帧：区域映射/DBTZ/时区文件版本绑定schema SHA；未知区域、重复/乱序编号、前后上下文变化、没有V4上下文的时区列拒绝。TSTZ保留UTC+偏移/区域，LTZ保留数据库墙钟+DBTZ；原V1–V3字段与schema摘要保持原路径。冻结Paris冬令/夏令/秋季及BC值、上下文/基线/相关批次37测试错误/失败0；这是内核与获取字节前置，正式OracleSnapshotAcquirer V4输出、时区投影、目标原生datum写入及HTTP/CPACK尚未接通，不标完整支持。

开发kernel SHA256 `3a7c1ad198bd1fe95ff8a369ecc83b65559113c2a433c10afe3ee392d9e8c851`；证据 `lab/out/temporal-variants-20261011/context-r1/proof.json`，context输出SHA256 `42b407aac8363c2e9158613fbed72ab50c7e9b31bcfea6a926f1f15f98aab42f`。只读复核脚本tz_context_verify.py；原始大字节与凭据不入Git。Oracle官方TIMESTAMPTZ字节构造及原生方法契约：[JDBC API](https://docs.oracle.com/en/database/oracle/oracle-database/21/jajdb/oracle/sql/TIMESTAMPTZ.html)，源端LTZ会话初始化依据[Oracle Extensions](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/Oracle-extensions.html)。

## 时区正式获取 / ARRAY UPDATE / JDBC正常链（2026-10-11）

V4获取正式接通：源库DBTZ/时区文件版本/有效区域映射写入可信基线；DATE/TIMESTAMP/TSTZ/LTZ统一原生datum获取，保留全部纪年和纳秒。TSTZ/LTZ新类型保留原始字节与源时区基础，事件schema摘要绑定上下文；目标原生setTIMESTAMPTZ/setTIMESTAMPLTZ绑定，版本不符拒绝，LTZ还要求目标DBTZ一致。TSTZ UPDATE前像额外匹配区域名称，避免同瞬时不同区域被当完整前像。目标账号需只读v_$timezone_file权限；本次只对专属目标账号授权。

专属三列/三行表T_D4_ZONED_20261011，V4基线SCN3242935后仅一次UPDATE三行。Oracle实际生成11.19/KDO21 ARRAY UPDATE，补齐聚合头/每行KDO/长度流/同一个精确undo归属及有序三行；未知或不一致字段拒绝。raw111完整入口→3完整UPDATE（同一个真实XID/undo节点）→真实JDBC首次3/同批重试0/独立确认通过；target applied/member/commit/rows=3/3/1/3。Paris冬令/夏令、负偏移与LTZ归一化、9位纳秒、全部前后像/区域名称独立SQL差异0，再获取的6时区字段字节差异0。源无补充日志/配置变更；库停止、固定helper自动删除、Desktop停止。

最终内核相关49项/适配器10项失败错误0；移除新方法未使用参数后只补该解码器与旧ARRAY局部回归，最终同raw再次投影的完整事件/证据结构不变，复用真实目标收据。诊断输出顶层Map.of顺序在不同JVM不固定，字节级JSON比较最初失败，canonical事件/证据全核等；它不参与生产batch/event摘要。

- rawSha256 `bafc89cc769502cbbe6f9a72780327fd6b0cf887394dbcfc8b8f0f3eca52f1d6`。
- baselineSha256 `0a9834c2ea9d4784b1524a4c0f13a0f638d12804041899afe3492bfd08f29afc`。
- zonedAcquisitionSha256 `238b42c16bc537f2a75c8f8ad8828bf02051660510eee98e52ff7f37f9186d40`。
- envelopeSha256 `fe1c3c12fdfedcb5f0b7d5b77d24c017c971d5160b391cba414d09c605920056`。
- adapterSha256 `ddf291e4186a00f3ab14e474d74b6125443cf6c1e69423b84a33cd1fe7722900`。
- 真实目标核 `27044afa9863a0b3fbffb6cd0f1603f46f4f094ab4d35f61df5fdff6c10632ba`；仅移除私有未用参数后的最终核 `04c7de81bf27ceac7087814d45587c06977baed9e503d068de7e913af8f462fa`，完整事件/证据核等。

证据 `lab/out/zoned-temporal-20261011/final-proof.json` / `verify-final/comparison.json`；acquire/future-update/native/project/target/reacquire/stop均rc0。首轮CDB已开但PDB未开导致pre rc1，发生在fixture前；等待ORKPDB READ WRITE后重试新目录。首轮事件不支持ARRAY UPDATE rc1保留；测试长度流未考虑4字节对齐的失败保留并修正；没有重放源未来UPDATE。

时间正常内核/JDBC链已完成本次样本范围，BC/精度原包证据复用；新HTTP/checkpoint/ACK与正式产品固定制品仍待接线。11.19 ARRAY UPDATE提交解析已补，但一般/部分节点回滚与pending恢复、同SCN恢复发布、LOB历史恢复回收仍继续；本包不标五项全部完成，D5搁置。
