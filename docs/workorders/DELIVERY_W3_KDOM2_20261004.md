# W3 KDOM2 扩展长度更新交付 / W4 跨块真实样本

源码 `6985d9c19a17cf9c9cf281f28dcf336cc56c7df4`，main；完整提交树独立重建，82项测试0失败0错误0跳过。同件jar SHA256 `e125894bd67255b76db0f0f12d52ddd8638f834e403ccb325e6c6c1062df1beb`。[封存清单](evidence/DELIVERY-6985d9c.json)绑定输入、dump、SQL真值、构建和验证日志。本轮执行专属实验库新表DDL/DML及切归档；未部署解析器、未写StreamHouse目标。

## 源动作与冻结

唯一源 `oraredo-19c-lab-20261004`，19.3，supplemental MIN/ALL始终NO/NO。新表 `ORKLAB.T_UNDO_CHAIN_20261004`，object/dataObject73043，PK index73044。插入ID1与900字节baseline后提交；随后60次keep更新、保存点、60次discard更新、撤销至保存点、提交。只读最终SQL值为900字节keep-060及K填充。

seq10归档13,957,632 bytes，SHA256 `3325f2b389093ce1f6e7f5653c9d5a1c7677147eca97af86ad5762ec2126e46e`。文件SCN范围[2149123,2158819)，源动作窗口2158699..2158815。原始结构解析25,340 records。Oracle独立dump只选源窗口；dump不作为运行时输入。实验ARCHIVELOG先前已开启，不构成生产NOARCHIVELOG全流程可用证明。

最初脚本变量MODE导致PLS-00103，发生在DDL前，表不存在检查保留。修复变量后新目录r2仅执行一次源动作，SQL rc0；之后Python导出CLOB换行失败。保留原stdout与实际执行SQL，通过只读truth脚本及`--recover-capture`完成冻结，未重放DDL/DML。CHAIN_TX未出现在实际源stdout；事务XID1.1.604来自raw与独立native dump，不能冒称源SQL打印证明。

## 代码与针对性验证

`HeapRowPatch.decodeKdo`新增真实观察到的28字节KDOM2单列更新：xtype0x81/0x82分别保留XA/XR，列map仍独立校验；值字段必须为0xfe、little-endian长度、精确payload。900字节payload不含三个长度前缀字节。未改变29字节非packed路径。当前拒绝KDOM2短值、NULL、多列、其它布局与截断/多余payload，未有实证者不放行。

同件jar从RAW独立输出181正向/补偿patch（1insert+120update+60XR）与121inverse patch；native dump校验位置、kind、DBA、slot、xtype、ABSENT/VALUE及全部列字节，302patch零差异。最后XR恢复的900字节TXT与只读SQL真值一致。这不证明完整提交生效事件集合。

长期事务的120个undo节点跨16个物理undo block，15个入口previous DBA非零且rci=0。`UndoLink`仍明确拒绝这15个入口，不能仅凭previous DBA猜sequence/record，亦不能把历史row ITL UBA等同通用undo链前驱。

真实vector内存派生6项反例（长度错、截断、额外payload、未证编码、列map越界、多列布局）返回预期拒绝；加15个真实跨块入口拒绝，审计21/21。harness rc0为审计成功，stdout为拒绝报告，CDC事件0。原冻结seq8/9的18个forward/inverse pair用同件jar回归，native零差异。

四个新增单测覆盖900字节、XR保留及拒绝形态；82项全通过。首轮native comparer误读`XRxtype`并漏掉`[ 2]`空白，183/1差异报告保留；修复验证器后零差异，未为此放宽parser。首轮负例harness checked Exception编译失败及ClassNotFoundException保留，后续单独提交08bb117修正签名，正式封存树6985d9c重新编译执行通过。

## 接续

代码交付/针对性KDOM2字节验证通过；W3完整行、W4跨块链/全部补偿/保留集合仍进行中；W5字典、W6接入、W7采集及扩展未验收。`events`同件jar rc1、stdout0，原因DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED，CDC零发布。

继续用本批已冻结seq10推导跨块前驱，无需重跑DML；先证明通用节点链接与失败锁定，再组合120更新及60补偿，缺任一跨块/补偿必须拒绝，随后进入SCN字典/基线与StreamHouse集成。
