# W4 事务结束与补偿字段首轮交付

源码提交：`b18870951ba1dedb5454565056d1d928b16e0da8`，分支 main。独立完整提交树构建，30项测试0失败0跳过；制品与输入/输出/日志 SHA256 见 [封存清单](evidence/DELIVERY-b188709.json)。本轮只读既有冻结样本，没有新DML、部署或目标写入。

## 交付与验证

- `TransactionEnd` 从5.4读取segment DBA、slot、sequence及原始flags；只接受实证状态9、flags0/2/4和字段形态。flags4标识整笔回滚，未知状态/字段拒绝。
- `UndoCompensation` 从5.6/5.11读取对象、undo/segment DBA、previous DBA、slot、rci、wrap、flags、原undo opcode和begin属性。只接受实证User undo done形态，不产生撤销事件。
- 原始归档由Java读取；独立Python comparator只读取Oracle dump作对照。限定19.3 ORKPDB CON_UID677737149、CON_ID3；seq8从block30680、seq9全量。研究probe的环境选择不进入生产解析规则。
- 同件jar对账：seq8共82个5.4、5个5.6、2个5.11；seq9共4个5.4。合计93个marker，位置/record SCN/字段零差异，decode与comparator均rc0。
- 新增6项测试涵盖真实commit/rollback/savepoint/begin字段及未知状态、截断、缺User undo done、错误begin wrap拒绝。物理字段支持不等于事务安全验收。

## 三个已确认反例

1. T006整笔回滚真实XID为3.4.669；XR删除行里的旧ITL指向10.3.1134。不能把旧ITL身份当回滚身份。
2. T007 savepoint回滚的5.6中rci=35/34分别指向前驱undo；XR行恢复旧UBA。不能用恢复后UBA或无证明的rci+1规则认领当前撤销操作。
3. T012 record的commit是vector6，而当前事务5.1在vector7–10。必须完成整条record关联后再闭合，不能看到commit就即时发布。

5.11 begin compensation的wrap=1不是事务sequence669或1133；5.4里的segment DBA尚未证明映射为USN，不能由CLS算USN后直接放行。以上字段保持原始含义。

## 六列处置与下一门禁

代码交付通过、针对性字段对账通过；事务集成验证待证、部署未开始、现场验收待证、发布未开始。`events`仍rc1，CDC事件0。

下一步：5.2 segment/slot/sequence/UBA与5.1完整XID的精确关联；记录级先收齐再绑定；5.6/5.11与原undo链及逆行变化交叉校验；跨seq8→9的开始/提交闭合；缺archive、重复或多义undo、slot复用及旧ITL反例。关联不足必须整事务拒绝。W5字典SCN/基线及完整键仍未验收。
