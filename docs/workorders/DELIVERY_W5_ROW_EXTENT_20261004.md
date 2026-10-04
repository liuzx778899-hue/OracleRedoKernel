# W5 物理尾列缩短解析交付

状态：有限物理解析及配对通过；完整行、SCN历史字典、逻辑DML、CDC、现场与发布未验收。没有新数据库动作，全部复用冻结归档。

## 制品与范围

- 最终运行时/实验源码 `9b3f738c98a73e38c7539cba6c44b609204d80ef`；Java17同完整Git树构建，209测试、0失败/错误/跳过。jar在 `lab/out/release-tail-extent-r3/tree/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar`，SHA256 `94443fbb313d436a414dd096cc688ce2748dea4133141636c84b3d5a2d5f8660`。
- 初次实现d4800e0的208测试通过；223e3c3第二轮209测试出现1错误，CrossCompensationTest复制Patch时旧构造器丢失extent，严格补偿比较拒绝。原报告/日志保全；9b3f738只修该测试以保留真实extent，不降低生产拒绝门禁。
- 清单：[DELIVERY-tail-extent-20261004.json](evidence/DELIVERY-tail-extent-20261004.json)。包含三轮构建、原始失败、冻结输入、派生输入、命令rc、完整stdout/stderr及两次独立SHA复核。

## 行为变化与可证伪依据

`HeapRowPatch.RowExtent`保留storedColumns/changedColumns/removedColumn/sizeDelta；字典列数为解释上界。普通URP允许有限较小物理ncol，但缺列仍ABSENT。IRP保留实际size/delt；packed KDOM2的布局字段不能当普通size delta，因此显式未知null。兼容旧Patch构造器会产生未知extent；有/无extent不再可认证为同一补偿逆向。

专属19c seq25中，NOTE为invisible物理列2，真实NOTE secret→NULL并没有显式NULL字段：XA xtype1、28字节KDO、ncol2/nnew0、map2、空值field、尾标记1、delta=-7。有限尾列入口只接受这些实际约束，并保留ABSENT；不直接产生SQL NULL。`HeapUndoPair`要求同record唯一完整UBA、PDB、block/slot及身份对应，inverse ncol3/nnew1只恢复物理列2的6字节secret，delta必须为负的值长度加1。普通前后extent也必须一致。

限制：只覆盖已证实单列末尾删除及短值长度不超过250；多列缩短、扩张、XR尾列、行链等仍不宣称支持。字典上界仍是调用参数，不证明SCN历史列布局。

## 真实与独立验证

输入seq25 SHA256 `6d593f143521236722386196a5f0908459419d305c1364d968637599d0b109fe`，全部原始字节生产解析，不读dump/SQL生产结果。

| 检查 | 证据结果 |
|---|---|
| 有界自主物理入口 | 自主发现2事务，5原始/0取消/5保留，native全量有序图零差异 |
| typed物理patch | 8正向/逆向vector，列数/map/出现值与native零差异 |
| 完整UBA物理配对 | 4pair含1个3→2尾列pair；身份、slot、extent、size及双方列状态/字节native零差异 |
| 真实派生拒绝 | 错tail map、forward slot、缺undo、inverse slot、size delta、inverse map、重复forward UBA、ncol越界共8份；各重封块checksum，scan rc0，完整物理入口与typed pair均rc1/stdout0 |
| 独立比较器反例 | 漏变化、把ABSENT改NULL、错extent均rc1检出；不能充当内核拒绝证据 |
| 受影响seq24回归 | 同件jar三事务10原始/4取消/6保留，与native有序图零差异 |
| 受影响长值回归 | 同件jar seq15/XID2.13.581，120pair/120取消/0保留、16跨块补偿，列字节/native及有序cursor字段零差异。使用保全的lab驱动class、新jar优先classpath，未重跑DML |
| public events | seq25/26均rc1/stdout0，DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED，CDC0 |

第一轮旧内核seq25 `UNSUPPORTED_UPDATE_ROWPIECE`仍在 `lab/out/research-baseline/physical.*`，新结果另存r3。所有反例、失败日志均追加保全。

## 后续门禁

下一步按[W5派单](DISPATCH_W5_ROW_EXTENT_EPOCH_20261004.md)实现来源绑定的固定SCN基线与三次变化完整before/after，逐项逆向校验。必须拒绝缺失/过晚/跨边界/错来源/错世代基线与before不符，最终值相等不能代替顺序验收。logical/data-object角色映射、TRUNCATE复用与历史DDL/ABA仍待实现；本交付没有把尾列ABSENT解释为NULL，没有完成历史字典、完整CDC或持续capture/ACK。
