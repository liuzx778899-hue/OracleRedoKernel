# BC / 时间精度真实 JDBC 链反馈（2026-10-11）

## 已交付

- DATE/TIMESTAMP 获取使用 OracleResultSet 原生 datum 字节，避免 Java 日期转换丢失 BC 纪年；内核没有新增 Oracle JDBC 依赖。
- BC 值用于目标赋值和 UPDATE 前像条件时均明确绑定 BC 格式。AD 写入沿用已有路径。
- 专属真实 raw110 的六列 KDOM2 五字段后缀更新补齐；完整前后像、提交归属及原有拒绝门禁保留。
- 原生 COL$ NULL$=1 隐式主键非空形式补齐；0/1/2 对应元数据可空/非空，未知值及矛盾元数据拒绝。

## 有限证据

只执行一次基线后的源 UPDATE：DATE/T0/T3/T6 为44 BC、T9 为1 BC；源未开启补充日志。SCN3235410 基线→raw110→1完整 UPDATE→真实 JDBC首次1/同批重试0，独立提交核验通过；目标 applied/member/commit/rows=1/1/1/1，六列双向差集0。精度0/3/6/9的实际舍入与 SQL 真值一致。BC 行再次原生获取的六字段与 raw/native 观测字节差异0。HTTP/CPACK尚未跑新候选。

受影响内核40项＋适配器7项，失败/错误0（包括后缀截断/多余字节/错列映射拒绝、BC前像条件及既有LOB目标回归）。

- kernelSha256 `84774209f334369edc3c7ab3adb9f38c9a6280b62983cb9ddf14746fa49b92c6`。
- adapterSha256 `6ce27a8985da0ed0a9fc2f102b7c7fc7478c40582cf9fe3468acaa05385f23b1`。
- rawSha256 `7c8b392b79db3bac244244a7a6c37b808e5a098be782e007e8e71f0a78d78916`。
- baselineSha256 `ff19decb1e9e4fa1ca7c82c99fd61de792ec3654165ada9c42bd633634e05f92`。
- bcAcquisitionSha256 `3795b6917ed41e499c9aa433faff65676c4ecb34b37e38eb24c4c6294a7790e1`。
- envelopeSha256 `1f5ba7d0e2e9f3b64002b6a52200da935e9cd04bd98f2a1f0eac6b4a8abc0768`。

证据：`lab/out/bc-temporal-20261011/final-proof.json`；source/resume-r1 acquire/future-update/native/copy rc0、verify-r2 project rc0、target-r1 target/bc-acquire/stop rc0。大制品及私有凭据不入Git。复核命令为 `py -3.12 lab/bc_temporal_verify.py`（输出必须使用新文件，已有通过证据保留）；投影命令记录见 verify-r2/project 输出与本包 BcTemporalBatchProbe 参数契约。

## 失败与边界

首轮获取因新样本 COL$ NULL$=1 与旧门禁不一致拒绝，发生在未来 UPDATE 前，source/acquire stderr/rc1保留；只读诊断后继续同一表，fixture/seed与未来 UPDATE未重放。首轮事件入口拒绝六列 KDOM2，verify-r1/project rc1保留。开发测试的错误fixture DBA与一次脚本GBK读取失败已修正，没有源端影响。

库停止、固定 helper自动删除，未更改其它业务环境。BC与无时区精度本包真实链通过，不等于时区类型全部支持：区域/LTZ上下文、一般时区事件与目标链、新HTTP获取/目标/checkpoint/ACK仍继续。ARRAY部分行补偿/恢复、同SCN恢复发布、LOB历史恢复回收及产品新依赖仍待；五项完成后进入模拟测试。D5搁置。
