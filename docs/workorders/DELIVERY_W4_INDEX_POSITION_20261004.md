# W4 同叶 INSERT 槽位重放与短 Z 回滚交付

冻结并发样本中，A 删除键3时索引slot为1；B插入键2后，A回滚的RESTORE slot为2。本轮将内核拥有的同叶清单用于位置重放，成功关联这一补偿；同时解码该回滚实际出现的短KTB Z / XR INSERT。A、B两笔有界heap/index分配与取消图与独立Oracle dump零差异。**只验收已观察的插入位置变化和有限图，不代表完整DML、字典或CDC发布通过。**

完整内核源码树 `aa44b0d10b5953e7d1e103ee44cef605f4dff5b8`；Java17独立封存重建167项测试，0失败/错误/跳过。jar SHA256 `4db98241abfea8579e2502563eca666375e8fb32724a0af403a8a19341e178a3`。补充拒绝驱动源码树b934d99只新增lab文件，main/test/pom与该内核树git diff为空；补充验证使用同件封存jar，未另建生产制品。[证据清单](evidence/DELIVERY-index-position-20261004.json)记录源码、制品、冻结输入、输出、命令rc、首轮失败及补充验证，所有文件两次重算SHA一致。本轮无数据库动作，没有重放已执行DDL/DML。

## 实现与约束

- `IndexLeafPositions`只接受私有构造的owned leaf inventory。原始pair的完整身份、字段与键字节必须唯一属于清单；补偿record/vector/SCN/metadata及原始字段必须匹配清单中的源位置，当前事务源头必须与清单归档头和SHA相同。
- 从原始操作到补偿前，顺序重放同叶INSERT；插入slot小于等于目标slot时目标slot加1，插入位于后方则不变。**中间DELETE/PURGE/RESTORE一律拒绝**，不能凭操作名假定物理目录移动方式。页split/维护等仍由清单入口拒绝。
- `IndexCompensation`新增接受owned positions的入口，保留完整metadata/UBA/ITL/row size/活动身份/唯一候选检查。旧入口仍要求静态slot相等；补偿成功后才记录位置，重复或倒序查询拒绝。失败锁定位置模型与事务生命周期，不能继续发布。
- 清单保存完整pair、回滚row与marker签名，绑定原始字段，防止调用者保留typed元数据但改键/历史KTB/marker字节。签名来自本地可信归档读取，不能代替源采集真实性和缺口证明。
- `HeapRowPatch`仅补充已观察的8字节KTB Z、version0x0d、11.2、49字节KDO、XR INSERT路径；现有严格KDO/列检查继续生效。Z没有当前XID/UBA，不从历史值补造；事务归属仍依靠undo/history和补偿身份。原有packed XR UPDATE路径保留。

入口中的index object、leaf DBA及列数仍由lab调用者提供，**W5 SCN字典尚未认证该映射**。当前位置模型尚未证明多次并发插入、普通DELETE与PURGE的区别、跨叶搬迁、恢复后的窗口，以及全部相关opcode/control覆盖。

## 同件制品的真实验证

seq19 SHA256 `497d09a556faed6b96307f9a439413e08109d0920b75bbeacb909cccbd582d63`，来源、交叉会话顺序、配置和最终SQL沿用[原始slot实验](RESEARCH_W4_INDEX_SLOT_SHIFT_20261004.md)。Oracle dump是独立校验输入，事件路径只读取raw redo。

| 门禁 | 结果 | 范围 |
|---|---|---|
| A原始DELETE、B INSERT、A RESTORE | native slot1 / slot1 / slot2；新matcher精确命中A XID10.23.1220、UBA37749016:244:12，零差异 | 一次同叶并发插入迁移 |
| A事务图 | 2原始 / 2取消 / 0 live，native零差异 | heap DELETE及index DELETE均撤销 |
| B事务图 | 2原始 / 0取消 / 2 live，native零差异 | heap/index INSERT保留；不能直接当逻辑事件数 |
| 新heap pair | 4对、6正向出现列 / 2逆向出现列，native字节零差异 | baseline两对+A/B各一对 |
| 五笔既有混合图回归 | 22原始 / 19取消 / 3 live，native零差异 | seq8/9/18有限样本 |
| 六批长heap回归 | 756原始 / 430取消 / 326保留，native零差异 | seq10—15；此次行解析runtime变化后的回归 |
| events | rc1、stdout0、`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED` | CDC零发布 |

`compare_index_shift.py`从native原始5.1/10.22提取完整UBA及原始位置，并从三次实际操作取slot；`compare_mixed_undo.py`按完整XID独立生成分配顺序、取消对象和有效head，未以Java输出生成期望。图数量及最终SQL不替代全部redo语义、完整行或逻辑DML认证。

七项位置派生反例（补偿slot、marker原始字节、原始pair键、重复、重复原始候选、漏B、外源位置）全部rc1/stdout0，原因见清单。四项图反例（漏index节点、错leaf slot、错leaf模式、重复补偿）同件jar全部rc1/stdout0。十项位置模型单测覆盖后方插入不移动、typed XID伪造、来源不符、其他中间操作拒绝及失败锁定；合成安全测试不能扩大真实支持范围。

补充驱动从同一真实record `19:225422:240` 的11.2 vector1派生七种单点变化：XA、KDO opcode、split标志、缺列、vector opcode、块号、KTB version。要求rc1/stdout0且确为RedoException，AssertionError不计通过。派生仅发生于内存vector，不修改原归档，也不是七次实际Oracle运行；完整结果绑定在清单`z_insert_refusals`。

## 保全与接续

第一次构建161测试出现3失败/3错误，原因是合成marker的tablespace0与原始undo tablespace5不一致，既有matcher先拒绝。原始源码、XML、stdout/stderr/rc保留在 `lab/out/index-leaf-position-20261004/first-source` 与 `first-reports`，修正测试fixture后另名验证。该失败不能算位置模型反例已通过。

第一次真实A图仍在heap短Z入口拒绝 `UNSUPPORTED_KTB_Z_ROW_PROFILE`，stdout空。原始开发jar SHA256 `6eafc279e631b230817e440ffbf2b5410b79b4fb299f96476a979106da767c98`、驱动和失败全文保留；这是开发制品，不冒充完整封存树。随后依据同一raw/dump的XR IRP新增有限路径，以最终aa44b0d完整树另目录验证，未重跑源库动作。

下一步先做专属源的多次较小/较大键INSERT实验，独立核验顺序与位置，再研究前方PURGE及普通DELETE的真实物理差别；不同中间操作在证据不足时继续拒绝。继续补全并发marker归属、相关控制输入、页维护/恢复，再推进W5字典/基线与W7持续capture/解析游标/ACK。部署、现场验收和发布均未开始，长期目标保持active。
