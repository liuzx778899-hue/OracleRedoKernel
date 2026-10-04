# W4 跨块整事务rollback、扩展结束与短KTB Z

专属19.3无supplemental新表`T_UNDO_FULL_20261004`一次性执行120个跨heap块长值更新并整事务rollback，冻结seq15。此前内核真实拒绝结束20/24/4 flags0x14，以及最后两条8字节KTB Z。根据独立raw/native/SQL证据补齐有限形态，不扩大未知flags或猜当前事务身份。

完整封存树076865c1e0c98ff463db91503c93920c45ba7ea5；[证据清单](evidence/DELIVERY-076865c.json)。Java17完整Git archive树独立构建，124项测试，失败/错误/跳过均0；jar SHA256 `2dcd626c84e1bc001d8a97e00475d88730605ed732a93610d351864a8d3135df`。制品、原始输入、结果和失败原文关联SHA，最终逐件复核。

## 新输入与实现

seq15为1,299,968 bytes，SHA256 `bff7662a9a5f39d0e5270608b8ef738b9de79dfeb0c2c6cc3fac4cef99ee2629`；SCN[2200679,2202181)，动作窗口[2202125,2202177]。新表obj/dataobj73053、PK73054；长事务XID2.13.581，rollback后SQL事务ID为NONE。基线20行900字节，ID1/20位于不同heap块；所有源动作仅在专属lab执行一次，没有改补充日志或其他配置。

- `TransactionEnd`仅增加已观察0x14、20/24/4扩展形态；extension六个4字节值核对已观察常量/时间一致性，字段含义不作未证解释。native明确标记rolled back transaction。它没有native allocation-tail UBA，`undoTail`明确拒绝，不能把24字节extension当UBA。
- `HeapRowPatch`只增加8字节03/0d KTB Z对应packed XR单列长值update。其他头部/长度、XA及非该KDO形态拒绝。保留逆向列字节，XID/UBA为空；opaque KTB余字节不解释。归属仍经完整补偿匹配及生命周期验证。
- `ArchivedUndoInventory`保留UID/CON_ID上下文，`UndoHistory.closeRollback`核对caller经来源绑定lifecycle验证的rollback身份、完整XID/UID/CON_ID、所有原始输入已消费及active head为空。allocationTail仅为自身历史最后分配节点，不宣称与不存在的native tail相同。
- lab组装器先强制候选覆盖，再匹配begin补偿，最后以rollback关闭路径输出空live集合。API的ClosedIdentity仍可由caller构造；这不是不可伪造事务凭证，也不代替完整生产组装器。

## 有界验证

| 项目 | 结果 | 判定 |
|---|---|---|
| 原始5.1自主清单 | 120节点与native位置/UBA/metadata零差异 | PASS指定输入覆盖 |
| 自主5.6/5.11候选 | 120候选与native位置/字段零差异 | PASS候选覆盖 |
| 物理回滚集合 | 120补偿，含16个跨块/begin 5.11；0 live heap片段 | native exact metadata/逆向字节与集合零差异 |
| 结束身份 | RBA2535:232、segment37748880、slot13、sequence581、flags20 | raw/native零差异；无tail，明确不作tail认证 |
| SQL恢复 | 20行均恢复fixture基线，其中两行与最早native逆向值逐字节比较 | PASS本fixture |
| 结束派生拒绝 | 六个extension字改动、四个flags替换 | 10真实内存派生反例精确拒绝，10 lifecycle锁定 |
| KTB Z派生拒绝 | 两条真实Z，各截断/未知头部/XA/截断值4例 | 8项拒绝，不宣称lifecycle锁定 |
| 同件制品回归 | 旧五批及seq8/9原pair/补偿、选择和slot冲突、history/head-update等 | 通过；六批长事务合计756原始/430补偿/326 live |
| events | 六批长事务全部rc1/stdout0 | 继续拒绝 |

7个新增单测包括完整rollback无伪tail、extension反例、错UID/CON_ID/full sequence/commit disposition、缺begin补偿或原始输入以及短Z无身份/拒绝XA。首次0x14实现仍按commit四字段索引取时间，造成1项ArrayIndexOutOfBounds；单测捕获后改为末字段，失败原文保留。随后真实驱动暴露TRUNCATED_KTB，保留旧制品拒绝和第一次未完成输出；补上有限Z形态后才对完整120补偿及SQL判通过。没有覆盖这些失败日志或重跑DML。

## 接续与捕获边界

本包完成跨块整事务heap rollback有限验证，未覆盖事务内修改索引键、其他相关opcode、并发归属、崩溃恢复或W5字典/基线。没有完整CDC、部署、现场验收或发布。

用户追问捕获环节后，另完成CURRENT文件只读打开探测并形成[W7执行包](DISPATCH_W7_ONLINE_CAPTURE_20261004.md)。Linux该次读取成功不能替代捕获一致性，更不证明Windows独占或ASM问题已解决；归档copy不等于ldfcap，online持久捕获仍未实现。
