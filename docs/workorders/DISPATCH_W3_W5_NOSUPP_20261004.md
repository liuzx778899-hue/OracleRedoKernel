# DISPATCH · 无补充日志基础行、事务与字典

状态：开发中。执行：本chat Codex；独立评审：待独立证据。优先级P0，直接承接fa61476。用户已授权自主推进，不需重新征求日常实现许可。

## 问题与已满足前置

专属19.3已有无supplemental的原始归档seq8/9与完整dump，53,092 records/104,573 vectors结构对账零差异。真实heap示例显示IRP有行值、URP只给变更列、DRP本身无before值；rollback也产生11.3/11.5，故opcode本身不能当已提交逻辑事件。

目标是证明或拒绝无补充日志的I/U/D完整事件。不是把LogMiner SQL、dump文本或最终行状态改写为事件。

## 工作范围与资源

- 源：只用 `oraredo-19c-lab-20261004` / ORK19 / ORKPDB / ORKLAB（本项目专属）。既有共享Oracle仅只读，其他仓不改。
- 冻结输入：`lab/out/oracle19-nosupp-20261004/manifest.json`；先核SHA。已有fixture失败后仍有已提交数据，不执行fixture19.sql重跑。
- 原始样本与真值只用于本地验证；源码不得读取dump/LogMiner/truth来解码。允许提交自有无敏感数据的有限hex回归fixture，来源登记。
- 新fixture必须新名称并记窗口。需新场景时先读当前schema与事务状态，不删除旧表或归档。

## 交付物

1. 单行heap raw patch：IRP/URP/DRP，block/slot、物理列号、VALUE/NULL/ABSENT；已知完整rowpiece形态外拒绝。UPDATE未改主键不伪造。
2. KTU/KTB事务身份与undo关联证据；op C无XID时不从前一条记录猜，部分回滚明确定位取消。
3. COMMIT缓存门：已提交动作原顺序与commit排序；rollback/partial rollback绝不泄漏。未知目标操作/缺日志令整个窗口零发布。
4. SCN版字典与对象世代：本轮可先固定有界schema+SCN窗口并检查漂移，不能将当前字典误标为通用自动DDL支持。
5. 公开CLI/接口、正反例、hash清单、逐case反馈及STATUS；代码验证后小步提交推送，不等待未到期GA。

## 验收判据与反例

| 编号 | 输入 | 必需结果 | 必需反例 |
|---|---|---|---|
| A1 | 原始19c IRP + 已核字典 | NUMBER/VARCHAR2/DATE逐列值/NULL一致 | 缺字段、未知rowpiece flag、非法编码拒绝 |
| A2 | 原始URP单列变更 | 物理变更列正确，未改列ABSENT | 主键缺失不当NULL、不凭当前源查询补历史 |
| A3 | DELETE + 正确undo/基线 | 删除语义/键/before得到独立证明 | slot复用、旧基线/缺基线拒绝 |
| A4 | T004/T005/T006/T007 | COMMIT动作账与source真值逐项一致，rollback泄漏0 | 补偿D/I不被当原操作；撤销链歧义拒绝 |
| A5 | T008 seq8→9 | 同一XID/undo关联跨switch闭合 | 缺seq8或9、begin缺失、错世代拒绝 |
| A6 | T011 DDL/对象号复用 | 支持有证据的新epoch或显式停发 | 不用当前schema追译旧redo |
| A7 | `events`错误路径 | rc非零且stdout/事件文件/目标发布均0 | 前半段干净后半段坏仍零发布，不残留旧events文件 |
| A8 | 目标终态和事件账 | 同时核操作种类、事务、字段、顺序和终态 | 最终行相等但事件缺失/多发必须失败 |

R1行值、R2事务、R3字典各自记账。一个codec、一个正例或安全拒绝不升级整个case或全矩阵。

## 留证与禁止项

保存每条命令/rc/开始结束时间、原始日志、SQL case操作账、输入/制品/output SHA、record/vector/字段比较数、发布事件数。失败追加，不覆盖旧输出。生产官方通道、业务库改动、开启共享supplemental、停止共享容器、未证明的事件出口均不在本单范围。

六列状态与原始失败保留按STATUS执行；独立评审、真实集成、部署、现场、GA由后续门禁关闭。
