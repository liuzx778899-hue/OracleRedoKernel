# W4 5.11 begin补偿关联交付

源码`d3f07b326142e5ec6295b8aa03cdf7982039ee3e`，main。完整提交树独立重建72项测试0失败0跳过；jar SHA256 `45b516bf9da3959b77e24cd37ee48eb397b6ab13343591b1237c7b51a2f98e7f`。[封存清单](evidence/DELIVERY-d3f07b3.json)关联raw输入、独立dump/SQL真值及输出/日志SHA。本轮没有Oracle DML、配置变更、部署或目标写入。

## 实现与实证

`TransactionLifecycle.beginCompensationIdentity`按PDB UID、CON_ID、segment DBA、slot寻找唯一活跃的完整XID。该映射源于5.2精确UBA→同record5.1 begin的已验证身份；不按CLS推导USN，不把5.11 wrap1当事务sequence。缺活跃起点或segment/container错配拒绝。

`HeapCompensation.matchBegin`要求原pair为该完整XID的begin undo，header对象/前驱与XR逆向行内容一致且唯一；5.11的DBA解释为segment DBA，不误当原undo block DBA。任何匹配拒绝也锁定传入生命周期，不能跳过匹配失败继续finish。

同件jar，冻结19c seq8→seq9，history18原pair/11闭合身份：

| 补偿 | 原操作 |
|---|---|
| 5.11 RBA8/30714/64 | XID3.4.669，undo0x0240202d.018f.2f，INSERT6/rollback |
| 5.6 RBA8/30718/128 | XID10.5.1133，undo0x02402cbe.00f2.24，INSERT8/discard |
| 5.6 RBA8/30718/304 | XID10.5.1133，undo0x02402cbe.00f2.23，UPDATE discard |
| 5.11 RBA8/30725/24 | XID10.27.1133，undo0x02402cbe.00f2.2a，首次T008失败前INSERT18 |

独立Oracle dump对四次关联零差异。T007保留原undo record33的INSERT7，冻结SQL row7四列DUMP字节零差异，SQL最终没有6/8/18。首次T008的ORA-65040及回滚仍是已保留失败，不改写成成功的跨归档测试；真正跨归档ID28不在以上撤销集合。

两个5.11 XR行的旧ITL分别指向10.3.1134、10.5.1133，均不作为当前回滚身份。这证明当前样本中的旧ITL误关联被排除，不替代更广矩阵。

## 验证与处置

新增6项测试覆盖真实字段形态、wrap1与完整sequence区别、缺起点锁定、segment/container错配、已结束身份、旧完整sequence、重复原候选、错误slot和匹配拒绝锁定。第一次单元fixture的5.2多录一个零字节导致layout拒绝；r2/r3原日志保留，按冻结原字段修正后通过，不是生产parser放宽。

同件jar只给归档8/只给归档9均rc1且stdout0 bytes，分别拒绝未闭合身份/缺验证起点。

六列：代码交付、针对性有界补偿关联通过；完整事务集成进行中；部署/发布未开始；现场验收待证。生产`events`仍rc1、CDC事件0。

完整index/undo前驱链、跨undo block、重复/缺补偿、重启恢复、完整before/after与SCN字典/基线仍未验收。当前matcher返回原pair关联，不能直接当可发布行事件或完成整个回滚安全验收。下一步主攻所有相关5.1（含index）的前驱覆盖/唯一性，再组合完整事务行效应和W5。
