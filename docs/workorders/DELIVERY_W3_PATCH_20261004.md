# W3物理行变化与W4 undo身份首轮反馈

日期：2026-10-04。执行：本chat Codex。状态：代码交付/有界验证完成，完整DML和事务门禁继续；独立评审待证。

## 新增实现

- `HeapRowPatch`：已观察的19.3普通单行heap IRP/URP/DRP；精确block/slot、xtype、物理列号、VALUE/NULL/ABSENT。未改主键保留ABSENT、DELETE列全ABSENT、尾部未出现列暂不推为SQL NULL。
- KTB F读取有限XID/UBA，KTB C无XID保持缺口；KTB L在真实rollback中为旧行ITL，拒绝把其XID/UBA当当前事务。物理UBA仍不充分证明操作归属，XR补偿可能恢复旧指针。
- `UndoHeader`：5.1的XID、undo block/sequence/record、OBJ#/DATA_OBJ#/tablespace、已观察begin形态；未知长度/flags拒绝。
- 两个原始redo研究驱动和两个独立dump comparator，不将dump/LogMiner/SQL truth作为内核输入。

## 真实样本与判据

同一冻结19c无supplemental样本，未重跑DML。seq8 SHA `e9ec1dafaedb93c65e2a0dbc7bc0a4f8d492a5cbd350fd46c7a994f2f34a4aae`，seq9 SHA `99ea74e7cc2e1f8d89c329179ed16dad2d3633beceb5ac76beb7a91c722a0ba2`。字典是受控ORKLAB.T_BASIC四列，此schema窗口有界，未声称自动DDL支持。

| 验证 | 实际结果 | 边界 |
|---|---|---|
| seq8物理patch | 19 patches / 40 present columns / 0 mismatch | 包含正常与补偿redo，不等于19个用户事件 |
| seq9物理patch | 2 patches / 5 present columns / 0 mismatch | 跨switch后段动作；事务闭合待证 |
| seq8 undo header | 15 headers / 0 mismatch | 身份层，不是完整undo链 |
| seq9 undo header | 3 headers / 0 mismatch | 同上 |
| 完整单元测试 | 24执行/0失败/0跳过 | 新增列图越界、缺field、split rowpiece、旧ITL误归属等反例 |
| CDC事件发布 | 0，入口仍rc1拒绝 | 事务/字典未验收，不开放出口 |

比较物理位置、kind、slot、逐列raw hex/NULL/ABSENT；undo比较XID、UBA、对象/表空间、begin。类型codec提供精确NUMBER/AL32UTF8/DATE；本轮不是全类型矩阵验收。

## 失败保全

首轮seq8因KTB L拒绝，研究驱动已经输出前段物理patch，rc1；`patches-8.txt`、stderr及`parity-8.json`保留。驱动输出不可当事件。Oracle dump证明KTB L旧ITL，补齐物理结构后同SHA的`patches-8-r2.txt`与`parity-8-r2.json`零差异。

`lab/out/heap19-20261004/`保存build、patch、undo和parity原文。正式提交/制品/证据hash由后续同提交树交付清单绑定，源码提交与测试日志对应，不把工作树制品默认当正式件。

## 仍未通过

R1完整行/键与历史before、R2COMMIT/ROLLBACK/savepoint/跨switch动作账、R3字典epoch与漂移、T009缺archive、T011DDL、NOLOGGING、初始基线、真实capture/目标链均待证。URP只带变更列/DRP无before已可观察，但安全不误发的限制不能转为功能支持。

下一步：从5.1/5.4/5.11及undo前驱/回滚链建立事务关联，必须区分当前undo与恢复后的旧ITL；单看opcode、同record、相邻、slot或命中UBA都不足以证明逻辑事件。继续原W3-W5派单，不重放已提交fixture。
