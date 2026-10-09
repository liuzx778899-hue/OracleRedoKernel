# D4 RAW 完整值路径候选 · 2026-10-09

上一包r123正常产品44+2/实际producer/CPACK证据继续复用，不重跑。本包开始D4开发，原标量扩展、DATE/TIMESTAMP及LOB完整范围保留，不将RAW一项当D4完成。

业务可信基线允许RAW，DICT_COLUMN/COL$映射严格TYPE#=23；历史行完整性保留原始字节并区分NULL/空值。基线获取直接rawtohex(RAW列)，不走字符集转换。事件用不可变OracleRawValue（JSON `{"hex":"00ffc328"}`）保留精确二进制；导出数组独立，目标按setBytes绑定，目标事件像明确RAW类型，旧NUMBER/VARCHAR2行为保持。契约见oracle-spi-projection-v1.md。

内核四个相关测试类15项、适配器四个相关类15项均通过，必要离线打包rc0；反例包括二进制零字节/非法UTF8仍精确保留、NULL/非NULL空值边界、数组修改不污染原值、未知类型/超范围typeId拒绝。新RAW业务输入尚为合成夹具；SQL获取/JDBC真实绑定、真实redo事务/完整前后像及产品CPACK未验证，不能据这些测试给RAW功能验收。

稳定证据 `lab/out/d4-raw-20261009/candidate-proof.json` 及kernel/adapter stdout、stderr、rc与两jar。内核坐标 `0.1.0-raw-600f01211968`，SHA `600f012119687cfca216d3b6b3aff13db1fda1ba2eab81fec07f8b003c65619d`；适配器SHA以candidate-proof为准。本轮源/目标/容器动作0；产品仍r123候选24895aa/应用19725963，未改其依赖或换装。

六列：代码候选通过｜针对性回归通过｜真实集成待证｜部署未执行｜现场未验收｜未发布。

下一执行：自有实例按需启动，新专属RAW表和先行可信V3获取；只执行一次新fixture（非旧75–94），冻结raw与独立SQL/DUMP值真值，验证正常I/U/D与回滚的完整像/NULL/二进制逐字节一致；必要问题集中修复后一次产品接线。保存原失败，不以结构解析或安全拒绝代替功能支持；结束即停库，固定helper保存日志后删除。D2一般事务、其余D4类型/LOB、D3连续平台及D5 RAC/ASM/TDE继续待证；独立评审/约定部署/用户生产验证/现场与发布未完成。
