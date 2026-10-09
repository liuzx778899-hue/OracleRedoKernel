# D4 DATE／TIMESTAMP 正常功能与有限产品链 · 2026-10-09

DATE／无时区 TIMESTAMP 的可信基线、历史 schema、完整值、事件和 JDBC 目标已接通；实际 raw → StreamHouse → 原子目标 → checkpoint／ACK 有限正常链通过。不是 D4 整体、生产部署或现场验收通过。

事件使用不可变 `OracleTemporalValue(type,iso)`，DATE固定秒格式、TIMESTAMP固定9位小数秒，不制造时区／epoch；NULL显式保留。业务TYPE#12／180，非法日期、非规范值、BC／超范围年代、未知和带时区类型拒绝。基线通过 JDBC LocalDateTime 读取，获取前后完整物理 metadata 核等，保留原TIMESTAMP精度声明检查；目标按 `setObject(LocalDateTime,Types.TIMESTAMP)` 绑定，审计像保留类型。格式参考 [Oracle JDBC TIMESTAMP](https://docs.oracle.com/en/database/oracle/oracle-database/18/jajdb/oracle/sql/TIMESTAMP.html)，长度、纳秒字节序及SQL值由实际样本核验。

源样本：专属 `ORKLAB.T_D4_TIME_20261009(ID NUMBER,D DATE,TS TIMESTAMP(9))`，DML前实际Acquirer获取V3，SCN3030921、SHA `0274b636be8f1ab55fbc7cc43455b8cf4efac0714cf85b2f42614a6e0866799e`。一次UPDATE／INSERT／双列置NULL／DELETE各提交及双列UPDATE回滚；原96.raw SHA `7bf82007de2a14a13daa96191cb46a219e9eb13d7fc87f7478212990c41ee844`、leaf50364915。独立SQL／DUMP确认DATE长7、非零纳秒TIMESTAMP长11、零纳秒长7；闰日初值、零纳秒INSERT、NULL及最终2026-10-09T12:13:14／.987654321正确。事件入口只读可信snapshot与raw，SQL／DUMP仅作尺子。

真实输入先暴露历史类型门禁遗漏12／180，修复后再暴露三列行后两列合并KDOM2值流及双列NULL裁尾。补已证明的起始列1／两项连续自定长XA及XR，要求完全消费值流；双列裁尾核两个原VALUE、两个NULL标记和正反向尺寸变化，完整XID／UBA／位置及undo配对继续保持。其他多列／NULL／extended packed形态仍待证，不据本包宣称D2一般事务收口。原内核20／适配器13相关测试通过，门禁修复仅复验3项，多列／marker3／undo／补偿19项通过；其余证据复用。适配器首次仅测试异常类型预期错误，只重跑失败类；本地可变SPI摘要不一致在编译前拒绝，改钉原实际SPI42f0b4aa。原失败、执行副本、布局均保留，无源DML重放。parser-r8 rc0：4完整事件／回滚0／完整像、日期、9位纳秒和NULL逐项0差异／eventId唯一。

最终内核SHA `31007fab2de11341420c25003927ed3b96334eb1899a73d4b0ab12d7d0433157`，坐标 `0.1.0-temporal-31007fab2de1`；适配器SHA `02a827f6a47bde0d9d21ee58c2d3c895bec0764a67f76c3f26fca6345348541f`，坐标 `0.1.0-temporal-02a827f6a47b`。必要P reactor离线打包rc0、不重跑测试，应用SHA `a68e66198570056cea3605eb365c26df902385891de9672dada39cf58c1eb019`，嵌入两制品摘要核等。

产品：隔离 `ORKTIMECTL_20261009`／`ORKTGT_CMT_20261005.RTIME1`，自动任务101979989796438016 DONE、PUBLISHED rc0／4、time96 CONSUMED；目标事件／成员／提交4／4／1、ledger4／PASS1。四事件完整像、顺序、ID、payload、schemaSequence及目标实际DATE／TIMESTAMP DUMP与源逐项0差异，NULL及回滚正确。DB checkpoint与ACK全文同字节，SHA `4a34f1ba79ac6a32f0fe1b232339d642c3e0a76ec80c7bea7f8e0df71a7c1e05`；producer与冻结预期全文同字节，SHA `29bf485ef62df848119c66dfbc6456d947e776b497b9d41e6b2f79841a2f40c9`，1source／through96／rawLowWater96／pending0／published4。raw全保留，ACK不授权删除。

边界：原早期Acquirer记录以实验SQL登记原command／rc／receipt，再经产品verified核来源、对象、SHA、SCN及全部历史metadata，单独批准有限绑定；不是正式HTTP acquire、晚SCN回填或生产导入验收。专属fixture SELECT／FLASHBACK授权不证明生产零配置；同实例目标不算独立目标验收。集成源DML0／新获取0，源最终值与旧RPROG1账46／46／2保持；不重跑75–96、RAW或旧恢复链，不追加故障矩阵。固定helper两轮日志保存后删除、自有库stop rc0及最终exited，库卷保留。

证据：`lab/out/d4-temporal-source-20261009/`保存实际获取、源DML／DUMP、原96、失败及 `parser-r8/normal-proof.json`；`lab/out/d4-temporal-20261009/`保存构建／回归和 `product/product-proof.json`；`lab/out/d4-temporal-product-20261009/normal-product-proof.json`、actual.stdout、实际ACK／producer和完整派生driver证明产品提交，runtime driver rc0。秘密及大制品不上Git。

六列：代码交付通过｜针对性回归通过｜有限DATE／TIMESTAMP集成通过｜专属实验换装通过、生产未部署｜现场未验收｜未发布。后续优先正常功能：D4 BC／带时区类型／精度变更／其他标量／LOB，D2一般事务，D3连续在线／平台／保留监管；D5 RAC／ASM／TDE尚未开始，独立目标／评审／约定部署／用户生产验证／现场与发布仍待完成。完整D1–D5目标保留，继续当前chat，集中加固后移至完整功能候选。
