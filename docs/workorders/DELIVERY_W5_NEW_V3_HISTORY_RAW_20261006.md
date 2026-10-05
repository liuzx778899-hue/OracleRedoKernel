# W5 实际新 V3 后完整 SYS 历史

代码 `c9f340d`，kernel SHA256 `d7ee0dfd8bce1aaf6e5c5fb0aa9659f43ea15ba2a76043d17b2336393c1849b0`，Java17 模块 **296/0/0/0**。[374 文件双 SHA](evidence/DELIVERY-v3-history-raw-20261006.json)，前轮 227 文件未变。

## 新可信获取与一次性源实验

实际 V3 SCN **2536302**、SHA256 `226c9322176bb6fafffb9c6a2cecd093bb76a1bf2f9e6bd71570b7f4c5bc839a`，来自 owned source 现有读账号/e095822b adapter。六 SYS 完整行230字段、三索引身份/地址、业务基线，与固定 SCN 独立查询全部零差异。无 source grant/config 变化；NO/NO、旧表11/blocking-closed/new-note保留。读取账号既有权限仍是实验前提，不把本轮当一般生产权限/零侵入验收。

专属 `oraredo-19c-lab-20261004` 在此基线后执行一次 ADD EXTRA_V3 / UPDATE history-v3-added / DROP EXTRA_V3 / UPDATE history-v3-restored；两个 DDL、两次业务 commit，均已结束，禁止重放。seq68/69 捕获34029/14块，capture/归档零差异；完整 native dump 留 lab/out。collector terminal rc0，cdc_events0/consumer_ack false；不当目标或ACK通过。

## 原始历史结果

归档和 actual capture 重放完整输出字节一致。SYS 历史三事务由 factory 自主发现，未传入手选 XID：

| XID | commit SCN | original / row / index / control | 完整字段 | native/独立ASOF差异 |
|---|---:|---|---:|---:|
| 10.26.2305 | 2536394 | 6 / 3 / 3 / 0 | 267 | 0 |
| 10.10.2305 | 2536401 | 10 / 5 / 4 / 1 | 267 | 0 |
| 10.5.2307 | 2536419 | 6 / 3 / 3 / 0 | 230 | 0 |

22 original 的 native XID、role/generation、UBA/rci和完整 tail/end一致；共764值独立零差异。首提交仍处临时列名阶段，schema拒绝；后两提交分别为完整含 EXTRA_V3 / 恢复3物理列（含隐藏NOTE）的 schema。擦除四个24.x marker的DERIVED capture后，全历史输出仍字节一致。

首次旧kernel58a6e56e按OBJ$ profile拒绝/stdout0：新六列URP携带ckix239，正逆一致且native确认。新增只接受 SYS.OBJ$/37列、stored26/changed6的239，绑定正逆ckix；不用它当parent。其它值/九列profile仍拒绝，普通heap入口不放宽。七个新 checksum 合法 raw 反例均 scanner0/history1/stdout0：未知/不一致ckix、完整当前XID、generation、完整逆向OBJ#、index parent、缺begin控制。

旧静态V2三完整IUD字节不变；旧原/擦marker历史V2拒绝原因不变。产品、adapter未重新构建，不套用737/22作新验证。

## 留存失败与验收边界

预检误把既有NOTE=anchor期望为NULL，SQL返回原文/失败版本保留，未发生业务动作；首次OBJ拒绝、测试lambda编译失败、负例定位器遇到重复OBJ#/DATAOBJ#的Assertion及修复轮次保留。所有源实验只执行一次，失败后仅读同批输入。

**本轮通过实际可信新基线后的有界完整SYS历史组件。未输出/验收完整业务DML事件；未发生目标/control/checkpoint/ACK动作；不是产品HTTP、独立评审、现场或发布通过。** P仍efd068f/737/旧672，PR#13 draft/review pending，adaptere095822b/22是旧候选。

接续 [时点schema完整业务事件](DISPATCH_W5_HISTORICAL_ROW_SCHEMA_20261006.md)。原全goal active：历史业务事件/产品V3账HTTP、活跃跨窗、正式采集租约、独立目标、类型环境压力断电矩阵、独立评审现场发布。Codex卡顿放在整个Oracle完成后。
