# W4 savepoint回滚后继续写入

新增专属实验表`T_UNDO_RESUME_20261004`，20行baseline，选择不同heap block的ID1/20：60次keep更新、savepoint、60次discard更新、rollback to、10次resume更新、commit。源码fixture e322007，研究driver ea0e0d8；[SHA与命令清单](evidence/RESEARCH-resume-fixture-20261004.json)。只执行一次DML，不修改旧fixture，不开启supplemental logging。

## 冻结现场

- 专属19.3容器，DBID1276726821，MIN/ALL均NO；源SCN2165398..2165454。
- baseline XID10.17.1164；rollback前、rollback后及resume后明确SQL查询均为XID10.2.1163，避免DBMS_OUTPUT缺行造成身份缺证。
- 表dataObject73049，PK73050；ID1 relative file12/block197/slot0，ID20 file12/block199/slot5。
- archive seq13，5,733,888 bytes，SCN[2161538,2165458)，SHA256 `0b005d2ca6c63811564ca73d35e22ea611ce19ff14feaee4ae93f2da49dd05b3`。已保存实际执行SQL、SQL truth及完整native dump；原样本不入Git。

## 结果与反例

使用封存生产源码9ffc19b的同件jar，SHA256 `501b19f7a47ef45f3007d0ca4d2c7764725d18a0441c08fb7cbb0e9c7cc47125`：inventory读取130个原始5.1节点，位置/UBA/元数据引用与native零差异，130个原始UBA均不同。

旧cursor返回rc1、stdout空，`CURSOR_CROSS_BLOCK_DBA_MISMATCH`。初始保留尾为`37760270:242:6`，rollback前最后生成节点为`37760278:242:2`；首次resume节点为`37760278:242:3`，previous DBA指向37760270，而不是最后生成节点的块37760278。原始历史与生效链分离，不能简单把“上一次生成的undo”当resume的前驱。

该首次resume记录RBA11153:352，native同record5.2控制为segment0x02400110、slot2、sqn0、`flg=0x0002`、新UBA `0x02402d16.00f2.03`。当前新节点仍在rollback前的分配块内且record3；previous DBA非零不能一概解释成分配新undo块。已有`UndoBlockAdvance`只支持0x000a/record1，继续拒绝这个新布局，未偷偷放宽。其inverse历史KTB为旧行版本`0x02402d0e.00f2.05`，也不能替代事务生效尾record6。

旧补偿研究driver成功完成补偿匹配后因固定120节点计数拒绝，原失败保存。新研究driver改为检查“最后一个尚未撤销的原操作”，允许显式给定本fixture的130节点覆盖判据；native comparator独立统计实际原始节点、精确匹配恢复列字节、检查保留片段，并核对SQL最终两行。fixture固定计数只作实验覆盖，不是生产完整性证明。

| 有界验证 | 实际结果 |
|---|---|
| 原始heap pair | 130，与native零差异 |
| 原undo补偿 | 60，含8个跨块，与native零差异 |
| 保留heap patch | 70，与native零差异 |
| 两行最终SQL值 | ID1 resume-1-010、ID20 resume-20-009，均900字节，末保留片段字节相同 |
| 旧seq10/11/12补偿回归 | 新研究driver、同生产jar，三批180补偿/180保留片段与native零差异 |
| 事件出口 | rc1、stdout空，CDC0 |

本轮未改生产Java内核，复用9ffc19b的98项已封存测试结论，未重新执行该测试轮次。仅有新fixture和研究driver针对性验证；不得写成完整DML验收或生产新控制支持。

## 接续

下一步解码5.2/0x0002新UBA控制，并用同record唯一原始undo、完整活跃身份及相关拒绝反例绑定。事务图必须分别维护原始分配历史与补偿后的生效head，允许rollback后新写入引用保留head；不能继续要求所有原始节点形成无分叉单链。index效果、第二次savepoint回滚、完整rollback与恢复还待证。

本轮收到heartbeat时goal服务仍显示usageLimited，先保全输入；实时账号查询随后确认ordinaryUsageAllowed=true，原模型额度已恢复，继续完成只读对账。保全时的未分析检查点单独保留在本地并列SHA，未冒充先前已有解析验收；未启用Luna，未消费手动重置额度。
