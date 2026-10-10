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
