# W5 末尾缩列、SCN 基线与 data-object 世代门禁

状态：待实现/验收。直接依据 [真实研究](RESEARCH_W5_BASELINE_20261004.md)，不以计划交付冒充功能通过。用户已授权本chat自主推进；专属实验已经冻结，下一轮优先只读复用seq25/26。

## P0 物理行范围与末尾缩列

1. `HeapRowPatch` 保留原始KDO的物理ncol/nnew及列映射。字典列数是解释上界，不能要求所有URP物理ncol都等于它；物理缺列仍ABSENT。只在已证实布局下允许较小ncol；超出字典/错误字段数/越界或重复map仍拒绝。
2. 同源 `HeapUndoPair` 显式匹配实际 `3→2,nnew0` forward与inverse物理列2 secret，完整UBA、PDB、block/slot及原始身份都核对。禁止通过删掉变化列一致性检查或把全部ABSENT变NULL取得通过。
3. 用来源绑定的固定SCN完整基线顺序重建三个变化的before/after，逐项核逆向值与当前缓存一致。键/未改列只来自该基线；没有基线、SCN晚于事务begin、初始事务跨越快照边界、来源或世代不匹配、before值不符都整批拒绝。首次实现明确固定schema和有限单rowpiece，不泛化row chaining或DDL。
4. seq25完整事务图、三次变更顺序及最终值与独立native/SQL快照分别对账；不能仅终态相等。当前`UNSUPPORTED_UPDATE_ROWPIECE`保全，修复后同输入成为受影响功能回归。

## P0 世代与历史字典边界

- 分开 logical object_id/data_object_id；映射同时绑定DBID/activation/resetlogs/thread、container UID/ID、SCN版本、列的SEGMENT_COLUMN_ID与INTERNAL_COLUMN_ID，不能把visible COLUMN_ID当物理map。
- 旧数据世代73073与新73076在同file/block/slot0，缓存键必须包含世代，旧基线不能应用新IRP。index73074→data73075同理。
- 当前内核同值object/data-object接口不支持该转换。扩展typed物理范围时，保留错误交叉role/未知对象拒绝；不能仅把四个ID放进Set而丢掉heap/index对应关系。
- 两次当前字典相等不证明整区间无DDL或ABA。实际自动历史字典/DDL边界尚无来源证明时，保持发布门禁；不将调用者声明的SCN区间当已认证历史。
- TRUNCATE后旧ASOF查询已实际失败，不补用当前行/新世代快照。所有取证失败必须落盘、停发，无自动启用补充日志或改变源配置的回退。

## 必需反例与交付

- 真实派生/合成分别覆盖错误ncol上界、缩列缺逆向或错slot/UBA、非末尾截断、未改列ABSENT误当NULL、错invisible map、错误/缺失/过晚/当前基线、wrong data-object/PDB/source、世代复用，以及提交在undo之前的record处理。
- 独立验证器至少核漏变化/伪NULL/错世代；其反例不能代替内核拒绝。有限图与行恢复结果必须缓冲到整批通过，任一错误stdout/CDC0。
- 只修改所需内核与测试，Java17无DB/JDBC/Flink/Spring依赖；同完整提交树构建并绑定输入/jar/输出SHA、命令rc，保全首次错误。新代码后才补受影响回归，不重复已通过旧轮次。
- 交付中文报告/manifest/STATUS并提交推送。W5固定范围支持、自动历史字典、CDC事件、capture/ACK、W6接入、部署/现场/发布各自验收。不能以本派单或安全拒绝标完成。
