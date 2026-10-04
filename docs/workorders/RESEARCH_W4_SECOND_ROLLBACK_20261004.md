# W4 真实第二次savepoint回滚及继续写入

此前“rollback→resume→第二次rollback”只有合成history测试。本轮在专属19.3无supplemental实验容器一次性创建独立表`T_UNDO_SECOND_20261004`，完成两次savepoint回滚与两轮恢复写入，并冻结新seq14。生产内核没有改动，Java始终仅读取raw归档；Oracle dump及SQL用于独立验证。

研究脚本/驱动封存提交cfdcdc0；[证据清单](evidence/RESEARCH-second-rollback-20261004.json)。使用既有完整封存内核9a2344b的同件jar，SHA256 `388a0533936cd31dd73b98437c155215760fe11c11384feae711fc79a632575b`；src Git tree与研究提交完全相同。117项核心测试是上一轮封存结果，本轮没有重跑或宣称新的核心构建。lab驱动从本轮完整Git archive源码重编译，新增可选fixture补偿计数，旧默认60不变；该计数不是生产解析依据。

## 源动作与冻结输入

- 专属容器`oraredo-19c-lab-20261004`、owner OracleRedoKernel、19.3镜像绑定通过；DB ORK19/DBID1276726821，ARCHIVELOG，MIN/ALL均NO。没有源库配置变更。
- 新表obj/dataobj73051，PK73052；基线20行各900字节。ID1 file12/block211/slot0，ID20 file12/block215/slot5，确实跨heap块。
- 长事务完整XID`3.11.671`；两次回滚前后及提交前SQL身份一致。第一轮恢复检查返回keep-1-060/keep-20-059；第二轮恢复检查返回resume-1-010/resume-20-009。
- 更新阶段：60keep、60discard、rollback to kept60；10resume、savepoint resumed10、10second-discard、rollback to resumed10；6final、commit。最终ID1为final-1-006，ID20为final-20-005，各900字节。
- seq14：49,845,248 bytes，SHA256 `609cd5d52ab1682a20954475cb0d3717bd37faa153157fed1f26d87e8c635598`；SCN[2165458,2200679)，动作窗口[2200616,2200675]。独立dump按动作SCN有界，归档解析仍自主遍历完整原始文件，不以dump裁剪输入。

## 验证结果

| 项目 | 结果 | 判定 |
|---|---|---|
| 原始5.1自主清单 | 146节点，native位置/UBA/metadata零差异 | PASS指定输入覆盖 |
| 自主5.6/5.11候选清单 | 70个，同PDB已验证窗口native位置/字段零差异 | PASS候选覆盖，非通用并发归属 |
| 有序history物理补偿 | 70撤销，含9跨块；76保留片段，native exact metadata/逆向/保留列字节零差异 | PASS有界heap场景 |
| 真实两轮交错 | 按原始记录位置合并后为NODE120→CANCEL60→NODE20→CANCEL10→NODE6 | PASS两次撤销及之后继续写入 |
| 直接字段与原始allocation tail | 146节点，19generation/19跨DBA引用，native零差异、提交tail一致 | 不新增独立归档时点跨块前驱证明 |
| 历史行KTB | 144处不等于history生效前驱 | 继续反驳通用KTB前驱替代 |
| 最终SQL | 两行与保留patch最后值字节一致 | PASS本fixture |
| 四批旧样本同件jar/新研究driver回归 | 加新样本五批共636原始/310补偿/326保留，native零差异 | PASS既有有界规则回归 |
| events | 新seq14 rc1、stdout0 | 继续拒绝 |

完整fixture只执行一次；成功执行SQL副本与封存源码SHA相同。DDL/DML不重放，失败只修只读验证器。

## 保留失败与验证器修正

第一次研究驱动编译漏列`TransactionLifecycleProbe`依赖，compile rc1；补齐后继续只读验证，原stdout/stderr/rc保留。首轮native补偿比较器失败`native compensation candidate count`：在RBA97212:224找到两个相同metadata/逆向字节候选，其中一个原始undo位于未来RBA97217:16，尚未发生。先保留两次诊断原文，再以独立native RBA排除未来候选；本样本恰有1个未来候选被排除。已经发生的多义候选仍拒绝，不按Java结果、计数或最终SQL选择原始undo。修正后的四批旧样本回归也通过。

这是独立验证器的时间方向修复，Java内核在修正前已产出146/70/76研究结果，不能将首次comparator失败写成解析器失败或将未经对账输出写成验收。

## 接续

新增真实第二次savepoint的有界heap支持证据。并发候选归属、整个事务rollback（含begin/index）、所有相关控制opcode、崩溃恢复、W5 SCN字典/基线及真实capture仍待证。19条完整跨块前驱来自有序状态，直接字段对账不代替独立归档时点完整前驱认证。没有部署、现场验收或发布，完整DML目标未完成。
