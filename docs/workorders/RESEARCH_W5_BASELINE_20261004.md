# W5 无补充日志 SCN 基线、物理列序与世代研究

结论：两次受控专属实验冻结 seq25/26，10 个 heap 正向/逆向物理列布局与 native 零差异。固定 SCN 历史基线可保留未改主键/列；真实 trailing NULL 缩列与 TRUNCATE 世代变化揭示两个内核缺口。本轮是研究取证，不是完整行、历史字典或生产 CDC 验收。

## 来源与范围

- 专属容器 `oraredo-19c-lab-20261004`，19.3/ORK19/ORKPDB，仅新建本项目 `ORKLAB.T_BASELINE_20261004`；MIN/ALL supplemental 既有值均 NO，没有修改数据库配置。
- 首轮源脚本 `ad9f9e0`；只读恢复脚本 `cfa7187`；布局比较器 `66a0ce1`；完整研究树 `9003ec5`。
- 内核保持 `6dd1772e22daea6195709ebf02f1f93fb56c1060`，同件203测试制品 SHA256 `5f74b32874c2ad5dd9dbf16bc53265ad927db77eb2d72cca4a73465fce6d997b`；本轮未重跑已通过测试，main/test/pom 没有变化。
- [证据清单](evidence/RESEARCH-baseline-20261004.json)：182 文件双次 SHA 一致，关联原始归档、dump、SCN 查询、列字典、源命令 rc、实际 parser 拒绝与比较器反例。raw/dump 留本地，不上传。
- seq25：5,721,088 bytes，SHA `6d593f143521236722386196a5f0908459419d305c1364d968637599d0b109fe`；seq26：131,072 bytes，SHA `1674e2f73893fd6d1ab13b08e799a7d6ba6898a0e10a4babb6aa847bce61afae`。

## SCN 基线与 invisible 列

表含 ID NUMBER、TXT VARCHAR2、NOTE VARCHAR2 invisible；初始行 `10/alpha/secret`。NOTE 的 COLUMN_ID 为 NULL，SEGMENT_COLUMN_ID/INTERNAL_COLUMN_ID 为 3。raw/native 实际 NOTE 位于物理列2（从0计），不能按可见查询投影的列序解释。

第一次创建/INSERT 已提交，BASE_TX `10.33.1245`。随后 SCN2282650 的 Flashback 基线读取返回 `ORA-01466`，原 stdout/rc 保留；尚未执行 UPDATE。恢复只做新只读查询，没有重建表或重放 INSERT，SCN2282688 成功得到完整基线。

UPDATE_TX `10.24.1246` 依次仅改 TXT→beta、NOTE→NULL、TXT→gamma 后提交。SCN2282707 快照为 `10/gamma/NULL`；在这些 UPDATE 提交后再次 `AS OF SCN2282688` 仍为 `10/alpha/secret`。这只证明该次受控固定-schema 的历史数据快照，不能把当前字典的两次相等当整个 SCN 窗口无 DDL/ABA 的通用证明。

只读 SCN 查询的机制参照 [Oracle DBMS_FLASHBACK](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_FLASHBACK.html)，segment/internal/invisible 列字段参照 [ALL_TAB_COLS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_TAB_COLS.html)。这些文档只说明接口，实际列布局结论由本批 raw/native 直接核对。

## 新解析缺口：末尾 NULL 缩列

seq25 原始 record `25:11164:16` 含同一事务三个正向 UPDATE、三个同源原始 undo，commit vector 位于 undo 前。三个正向都没有 ID 字段，不可凭 UPDATE patch 直接输出完整主键/全行。

| 方向/阶段 | native ncol | native nnew | 直接给出的列 |
|---|---:|---:|---|
| TXT alpha→beta 正向 | 3 | 1 | 物理列1 beta |
| NOTE secret→NULL 正向 | 2 | 0 | 无字段、无显式 NULL |
| TXT beta→gamma 正向 | 2 | 1 | 物理列1 gamma |
| 第一步 undo | 3 | 1 | 物理列1 alpha |
| 第二步 undo | 3 | 1 | 物理列2 secret |
| 第三步 undo | 2 | 1 | 物理列1 beta |

另有初始 IRP/其 DRP inverse，合计8 heap vector直接字段与独立native零差异。当前完整物理入口 rc1/stdout0，`UNSUPPORTED_UPDATE_ROWPIECE`，首先在第三步 inverse 的 ncol2≠声明列数3拒绝；不是新 UPDATE 功能通过，也不把安全拒绝当支持。

只降低列数比较还不够：第二步正向 nnew0 与 inverse 的一个变化列集合不相等。必须显式保留物理 ncol/缩列信息，绑定完整 UBA、逆向字段、同一slot、历史字典与基线，再论证固定 schema 下末尾列的 NULL 效果。原始 ABSENT 仍须保留，禁止全局把 ABSENT 转成 NULL。

## 世代反例

在 seq25 全部输入/旧快照已经冻结后，仅对同一专属实验表 TRUNCATE，并插入新 `10/new-epoch/new-note`；NEW_TX `10.0.1248`。旧、新 heap 行都为 relative_file12/block403/slot0。

| 对象 | logical object_id | 旧 data_object_id | 新 data_object_id |
|---|---:|---:|---:|
| heap | 73073 | 73073 | 73076 |
| index | 73074 | 73074 | 73075 |

seq26 新 IRP/其 DRP inverse两个布局与native零差异；当前 source-bound入口以旧 logical scope 拒绝 `UNSUPPORTED_UNDO_HEADER_FLAGS`（碰到未支持 DDL/undo profile），以新 data-object scope拒绝 `OBJECT_TRANSACTION_MAPPING_MISMATCH`。两者均rc1/stdout0，只记录实际功能缺口，不能声称已实现世代转换。

TRUNCATE 后重新查询旧基线 SCN2282688 返回 `ORA-01466`、rc1，原始日志保留。这证明当前行、只按logical object_id或block/slot存的缓存、以及事后再取旧快照均不能代替已冻结的完整历史基线；生产须持久化来源/PDB/data_object/SCN，并在映射无法证明时停发。

## 验证器与发布状态

布局比较器反例：漏末尾缩列 record、凭空补 NULL、把invisible列2错置为列1，分别检出5/1/1差异。研究 Java probe只读取物理字段，既不做全行恢复也不执行补偿；其rc0不计DML支持。

同件jar对seq25/26 `events` 都rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。原始方案、持续capture/ACK、W6、现场及发布目标完整保留。

下一步执行 [W5末尾缩列与世代门禁](DISPATCH_W5_ROW_EXTENT_EPOCH_20261004.md)，优先修复本冻结真实失败，禁止再次运行源SQL；生产自动历史字典仍独立待证。
