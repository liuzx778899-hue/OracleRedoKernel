# W5 归档自主完整 SYS 历史候选

代码 `a3fb674`；内核 SHA256 `58a6e56ef7844bb58da94e06f5ae98fe7a63b2f347b60f02ffa071a095812ade`，Java 17 全模块 295/0/0/0。证据入口：[227 文件双 SHA](evidence/DELIVERY-dictionary-history-kernel-20261006.json)，前轮 220 文件内容不变。

`ArchivedDictionaryHistory` 从输入发现目标基线位置/完整 index parent 对应的 SYS XID，收齐原始清单、begin/head/end、UBA 图与 typed 正逆 pair，然后按提交 SCN 重放完整行和三个索引集合。factory 私有创建 timeline；无手选 XID、无 dump/SQL 生产输入、无业务事件输出。未知依赖、跨基线 SYS 事务、未闭合/相同 commit SCN、SYS 补偿/回滚等均拒绝。本轮只支持已观察的 19.3 窄 profile；新增 COL$ IRP 的 32 存储列逐项 NULL bitmap、完整 size、尾保留字节和完整布局约束后才允许四个 nullable 尾列完成。

## 针对性结果

- 冻结 seq66/67 的归档、实际 capture、擦掉三个 24.x marker 的 DERIVED capture 三种输入完整输出字节一致：三次 commit，22 original = 11 row pair + 10 index pair + 1 control。
- 三提交 SCN 2522904/2522911/2522937 的完整 SYS 行 267/267/230 个值，与旧 SCN 独立 ASOF/DUMP 真值零差异，共 764 值。首提交临时列 schema 拒绝；后两提交分别可形成含 EXTRA/恢复后的物理 schema。
- 11 个整入口反例均 rc1/stdout0：7 个既有 checksum 合法 OBJ$ raw 变异、2 个新增合法 checksum cluster bitmap/row size 变异、晚实际 V3、旧实际 V2。新 cluster 两例 scanner rc0 后语义拒绝。
- 新状态测试发现 parent 值检查遗漏并修复；首轮选择全部 SYS 角色把无关 dictionary 事务纳入后拒绝。build-r2 失败和各次 raw 失败日志保留。最终 build-r4 rc0/295。
- 改动影响核验：旧 actual V2 静态三完整 IUD 输出字节相同；原/擦 marker 历史 V2 仍按 `TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185` 拒绝。

## 验收边界

用于旧 seq66/67 正例的 V3 文件仍是明确命名 `DIAGNOSTIC-NOT-ACQUIRED-v3.snapshot` 的测试基线，来自旧 SCN 研究数据；不是可信获取 authority。实际 V3 SCN2533047 晚于这些日志，整入口拒绝它回填旧覆盖。**本轮是历史内核组件/诊断验证，不是历史 DML、产品 HTTP、现场或发布通过。** 普通旧事件路径仍保持历史拒绝；没有目标/control/checkpoint/ACK 推进。

本轮 0 源业务/配置动作、0 目标动作、0 产品构建。P 产品仍 efd068f/737/旧 kernel672，adapter e095822b/22；PR#13 draft/review pending，不声称新制品集成通过。

下一单：[可信新 V3 后新历史日志](DISPATCH_W5_NEW_V3_HISTORY_RAW_20261006.md)。原 goal active；跨窗活跃事务、正式 C++ 采集/租约、独立目标实例、类型/环境/压力/断电矩阵及独立评审/现场/发布继续。Codex 卡顿排查在完整 Oracle 目标之后。
