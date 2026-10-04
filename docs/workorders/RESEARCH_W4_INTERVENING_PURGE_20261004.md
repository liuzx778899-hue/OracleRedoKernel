# W4 中间 PURGE 与其他 XID 回滚研究

本轮专属源一次实验：A删除中间键50并保持未提交，B插入较小键20后回滚，最后A回滚。真实redo出现B的10.3 PURGE，A的RESTORE最终回到原slot1；**现有owned清单在B短KTB Z PURGE处安全拒绝，未到位置重放或并发补偿归属认证**。B的heap回滚另出现短Z XR DELETE，同件内核也拒绝。两个布局缺口均不能用最终SQL正确或静态matcher成功代替实现。

源动作脚本0017daa；完整研究树 `cea54f9e618f08904f020ead482acf4620609f49`，main/test/pom与已交付内核aa44b0d差异为空。使用已有167测试封存jar，SHA256 `4db98241abfea8579e2502563eca666375e8fb32724a0af403a8a19341e178a3`，未重建或重复已通过回归。[研究清单](evidence/RESEARCH-intervening-purge-20261004.json)封存源码、class、源输入、输出、失败原因、命令rc，所有文件两次重算SHA一致。

## 环境与源动作

仅在已授权 `oraredo-19c-lab-20261004` 做新表有限DDL/DML、归档和diagnostic dump。先核owner/image/running，DDL前SQL核DBID1276726821、ARCHIVELOG、MIN/ALL均NO；没有开启supplemental、切模式或重启。表 `ORKLAB.T_INDEX_PURGE_20261004` heap73065，PK73066、leaf0x0300015b。baseline键10/50/90提交；A完整XID10.19.1233删除50，B完整XID9.22.584插入20后rollback，A之后rollback。A ready后才执行B，B返回时A进程仍活着，A/B结束本地事务ID均NONE。所有源动作rc0，只执行一次，没有重放前批。

A删除后SCN2266876、B回滚后2266881、A回滚前2266900。最终SQL10/50/90与ten/fifty/ninety全部存在，20不存在。这只核源动作效果，不认证图或逻辑事件。

完整seq21归档668,672 bytes，SHA256 `f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80`。native有界SCN2266840..2266913。raw/dump仅本地 `lab/out/oracle19-index-intervening-purge-20261004`，Git只放脚本与摘要。

## 独立native与raw位置

| 操作 | record/vector位置 | record SCN | slot | ITL |
|---|---|---:|---:|---:|
| A DELETE | 21:1284:148:3 | 2266876 | 1 | 2 |
| B INSERT | 21:1285:328:3 | 2266876 | 1 | 3 |
| B PURGE | 21:1286:440:1 | 2266876 | 1 | 3 |
| A RESTORE | 21:1295:140:1 | 2266900 | 1 | 2 |

四个leaf位置、opcode、record SCN、ITL、slot、row size与raw六字节leaf字段零差异。前三个record SCN相同，**不能以SCN严格递增代替RBA/vector顺序**。native PURGE描述为 `purge leaf row (count = 2)`；记录这一原文，不自行把count解释成移除了两个键或slot目录总数。

真实B PURGE字段：

```
10.3 / DBA0x0300015b
KTB: 030d000000000000
leaf: 030101000d00
```

native明确为op Z，八字节KTB；5.6 metadata slot22/wrap584，与B相符，区别于A slot19/wrap1233。B heap begin回滚在 `21:1287:100:1`，11.3 / DBA0x03000155，native KDO DRP、xtype XR，raw如下：

```
KTB: 030d0001c7000b00
KDO: 5501000352010003fa120302010040000000009f
```

这些完整原始字段被保全，用于后续有限布局实现；本轮不宣称heap所有KDO字段已经独立对账。

## 同件内核的真实缺口

- A单补偿旧静态matcher rc0，匹配数1，stdout空。它只处理A目标marker，跳过B的其他marker，并且最终slot相同；**不能当成A完整事务图通过**。
- owned positions入口rc1/stdout0，`UNSUPPORTED_INDEX_ROLLBACK_MODE_OR_KTB`，在构造完整同叶清单时拒绝B short-Z PURGE。
- 对B实际10.3 vector直接调用`IndexCompensation.decodeRollback`，同样rc1/stdout0，确认不是位置算术错误。
- 对B实际11.3 vector直接调用`HeapRowPatch.decode`，rc1/stdout0，`UNSUPPORTED_KTB_Z_ROW_PROFILE`；当前新增短Z路径仅支持观察到的XR INSERT与既有packed XR UPDATE。
- A与B完整混合图入口均在同叶清单短Z处rc1/stdout0，因此**两笔完整图都未验收，不能填2/2/0**。这也没有验证后续其他XID marker的归属门禁是否足够。
- events rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，CDC零发布。

五项拒绝是**真实不支持入口证据**，不是五个派生安全反例，不能把保守拒绝计作PURGE功能支持。原始失败stderr/rc、静态成功日志及全部输入绑定研究清单，未放宽生产拒绝。

## 接续判据

先独立解码此八字节index KTB Z + 六字节单叶PURGE，以及二十字节heap KDO XR DRP，并核原始字段/短Z无XID无UBA的约束和错误布局拒绝；不能把Z历史字节当当前身份。完成后用冻结seq21重新验证清单覆盖，预期它包含A DELETE、B INSERT、B PURGE、A RESTORE。

随后再论证PURGE位置作用：本批只证明中间INSERT/PURGE组合的**净slot变化0**，不是一般PURGE减1规则。还需较大键、多个删除标记、目录目标被purge、其他count/layout反例；其他中间操作继续fail closed。同时完整marker清单必须区分A/B并各自核全XID、UBA、head及begin/end，不能简单跳过slot不相同的marker并声称覆盖完整。W5字典、持续capture/解析游标/ACK、现场与发布仍待证。
