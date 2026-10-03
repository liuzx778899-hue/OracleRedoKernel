# W4 原始heap/index undo链交付

源码`254e5e7b90b6d83743e8251e6bd980be31a6443d`，main；完整提交树独立重建78项测试0失败0跳过。jar SHA256 `2df2a2f212ff01ca2ba4bb243f86936a83fbb058c550ed1a7fffb17fd27f52a6`。[封存清单](evidence/DELIVERY-254e5e7.json)关联raw输入、独立dump、正反例与日志。本轮无Oracle DML/部署/目标写入。

## 交付与实证

`UndoLink`读取5.1 metadata的XID/UBA、原opcode与显式rci前驱；当前仅接受实证同undo block/sequence形态。校验slot/wrap，begin必须对应已验证起点且前驱为空。begin中的prev ctl uba属于其它历史控制信息，不串进当前事务链；跨block的previous DBA非零直接拒绝，不猜sequence。

`UndoChain`按完整XID/CON_ID建原始节点图，以精确UBA作节点键；拒绝重复地址、缺前驱、分叉、循环/不连通、错误begin/XID/container和错误尾部。失败后锁定实例。来源/record UID及活跃身份由外层已验证生命周期提供，不能把不同源的孤立vector直接混给图。

`TransactionEnd.undoTail`从5.4原始ktucf字段读取显式UBA。九个提交的尾部必须与唯一图尾一致；两个整笔rollback的5.4没有尾部字段，只验证唯一原始图尾，不能宣称拿到了原生rollback tail证据。

同件jar对冻结19c seq8→seq9，目标表事务：11条原始链、18个heap节点、14个index节点与独立Oracle dump零差异；9个原生commit tail一致。包括跨归档6.12.579的三节点链（undo block本身未跨块）。

T007原始链从提交指向的record37回到36→35→34→33，包含index37和index34。此链保留所有原始节点，包括后来被补偿的操作；它不是“回滚后生效链”，也不能把tail37误当仍生效操作。之前两次heap补偿关联仍是另一项证据，index补偿/完整生效集合待组合。

## 反例与限制

新增6项测试覆盖到达顺序反转、显式heap/index前驱、缺index锁定、重复/分叉、错误tail、循环/不连通、错完整XID/CON_ID、缺begin、metadata错配、无前驱和未证跨block拒绝。

同件jar读取真实T007五节点原始图，先验证正例，再派生六项内存反例：缺index34、重复index、错误rci、分叉、错误提交tail、未证跨block。6/6返回预期拒绝；harness rc0是审计成功，stdout为拒绝报告，不是事件。派生反例不算六项新Oracle执行。

六列：代码交付、针对性同块原始图验证通过；完整事务集成进行中；部署/发布未开始；现场验收待证。生产`events`仍rc1、CDC事件0。

当前只解释index undo的metadata link，未解释index逆向键值/补偿效果；跨undo block、补偿链退栈、重复修改/缺补偿、checkpoint/恢复、完整行/SCN字典仍待证。不能称为全事务回滚安全验收。

下一步在专属19c实例新建独立fixture，制造跨undo block长事务和savepoint撤销，冻结原始归档及独立dump，推导previous DBA与sequence规则；不得重跑已经提交的原fixture。随后组合原始链、全部补偿和保留行效应进入W5。
