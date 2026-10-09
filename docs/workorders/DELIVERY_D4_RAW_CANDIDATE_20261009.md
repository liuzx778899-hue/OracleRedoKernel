# D4 RAW 完整值路径候选 · 2026-10-09

## 真实冻结RAW正常功能追加

专属新表 `ORKLAB.T_D4_RAW_20261009`，先行真实V3获取SCN3021645、SHA `bfa272f46f9216c852686591c0ab2a2d74daf8d93397b81d18d9c0e5a372ce88`；随后一次新fixture（UPDATE/INSERT/置NULL/DELETE各提交，另一次UPDATE回滚），冻结95 SHA `ff6a12f284c1a19602cef78242c08bf3bf60cf2c5353e5c85b03c3acbc68c7e5`。生产事件入口只读可信snapshot与raw；独立SQL/DUMP值仅作尺子。`lab/out/d4-raw-source-20261009/parser-r4/normal-proof.json`与frozen-r4 rc0：4完整事件、0回滚事件、完整前后像/RAW/NULL逐字节0差异，eventId唯一，最终ID1/BIN010080FF；raw发现leaf50363819。新源DML只执行一次，不重播75–94或95。

实际回滚暴露marker3短值缺口：补两列XA逆向和XR补偿的自定长非NULL短值（原三列XA继续支持），完整undo/事务身份/补偿配对不放宽；相邻列2短值仅允许已有观察位置103/104，未知位置拒绝。r2相关19项通过，最后XR更改只复验marker3与HeapCompensation共11项通过，旧RAW回归证据复用；新内核SHA `b767fa1d299be5b3df5f6d6e6c40b6d8ad86a4e13cf177d7ddc377d9b63ce3d7`，坐标 `0.1.0-raw-b767fa1d299b`。适配器保持 `2c4e7ab6d9919d11bb373a37addeccc593f354470e0c88341347764348c98772`，实际SPI `42f0b4aa41322b26b7c8acdd2a63b26edeeb6556a275c1b699becc323872b9e4`。

原失败保留：首次获取ORA-00942，因新fixture未授权读取，源业务DML尚未开始；仅专属新表授予既有实验reader SELECT/FLASHBACK并续获取。实验权限配置不证明生产零配置。原源码获取driver/原rc、KDOM拒绝、相关回归失败、XR拒绝与旧SPI缺EventSchema投影失败全部保留；后续只修逻辑/实验类路径并读同一冻结输入。helper日志保存后删除，初次与续接自有库stop均rc0，最终inspect exited，库卷保留。该有限真实解析通过；JDBC目标/产品CPACK未验证，不能当D4整体或生产现场验收。

产品候选接线：P pom钉新内核 `0.1.0-raw-b767fa1d299b` 与适配器 `0.1.0-raw-2c4e7ab6d991`，必要reactor离线打包rc0，未重复测试。应用SHA `073c95364a9a15305527a139e846bdd6ac754839930af8cb705cfd6cab1f23c9`，两个嵌入制品SHA与上述冻结解析制品核等；稳定应用与 `product-proof.json` 在 `lab/out/d4-raw-20261009/`。尚未换装；上次实际运行仍r123/24895aa/19725963。下一仅把已冻结RAW95、真实早期V3 receipt和独立值真值接入隔离产品目标，核目标完整事件、JDBC二进制绑定及实际CP/ACK，禁止新获取晚SCN冒充这批的早期基线。

## 原代码候选时点记录

上一包r123正常产品44+2/实际producer/CPACK证据继续复用，不重跑。本包开始D4开发，原标量扩展、DATE/TIMESTAMP及LOB完整范围保留，不将RAW一项当D4完成。

业务可信基线允许RAW，DICT_COLUMN/COL$映射严格TYPE#=23；历史行完整性保留原始字节并区分NULL/空值。基线获取直接rawtohex(RAW列)，不走字符集转换。事件用不可变OracleRawValue（JSON `{"hex":"00ffc328"}`）保留精确二进制；导出数组独立，目标按setBytes绑定，目标事件像明确RAW类型，旧NUMBER/VARCHAR2行为保持。契约见oracle-spi-projection-v1.md。

内核四个相关测试类15项、适配器四个相关类15项均通过，必要离线打包rc0；反例包括二进制零字节/非法UTF8仍精确保留、NULL/非NULL空值边界、数组修改不污染原值、未知类型/超范围typeId拒绝。新RAW业务输入尚为合成夹具；SQL获取/JDBC真实绑定、真实redo事务/完整前后像及产品CPACK未验证，不能据这些测试给RAW功能验收。

稳定证据 `lab/out/d4-raw-20261009/candidate-proof.json` 及kernel/adapter stdout、stderr、rc与两jar。内核坐标 `0.1.0-raw-600f01211968`，SHA `600f012119687cfca216d3b6b3aff13db1fda1ba2eab81fec07f8b003c65619d`；适配器SHA以candidate-proof为准。本轮源/目标/容器动作0；产品仍r123候选24895aa/应用19725963，未改其依赖或换装。

六列：代码候选通过｜针对性回归通过｜真实集成待证｜部署未执行｜现场未验收｜未发布。

下一执行：自有实例按需启动，新专属RAW表和先行可信V3获取；只执行一次新fixture（非旧75–94），冻结raw与独立SQL/DUMP值真值，验证正常I/U/D与回滚的完整像/NULL/二进制逐字节一致；必要问题集中修复后一次产品接线。保存原失败，不以结构解析或安全拒绝代替功能支持；结束即停库，固定helper保存日志后删除。D2一般事务、其余D4类型/LOB、D3连续平台及D5 RAC/ASM/TDE继续待证；独立评审/约定部署/用户生产验证/现场与发布未完成。
