# V2获取身份实库反例与SYS全量undo清单缺口

driver代码5cd9f82；kernel672f01bc/271和产品efd068f/737/application2db7106f原制品复用，无生产代码改动、不重跑已通过轮次。goal active，历史字典/完整DML/现场/发布未完成。

## 实际获取身份反例通过

复用专属控制ORKV2HIST_20261006/获取100644812901937152及same owned helper。实际persisted GET先核健康；逐项篡改authority、账SNAPSHOT_SCN、metadata source dbid、dictionaryAnchors[0].dictionary.logicalObject、anchor key.slot，五次真实认证GET均code非0拒绝。每项finally恢复原authority/SCN/metadata字符串，恢复GET全字段与原GET一致、V2基线SHAe16e6275未变。

目标ROWS1/APPLIED0/MEMBERS0/COMMITS0、ledger0、CP空、TASK FAILED、WINDOW PENDING、ACK0及源/旧表/NO/NO审计前后字节一致。没有源DDL/DML、任务submit/resume或目标发布动作。helper启动仅获取核验，完成后exited143；源服务未重启。此实库反例补齐前单身份项，不替代完整字典历史。

## SYS全量5.1清单研究与新失败

从已冻结seq66/67、真实六anchors关联出的三笔SYS XID出发，既有kernel自主遍历整归档5.1节点，独立native按完整XID重建有序identity/UBA/previous DBA/rci/opcode清单：

|sequence/XID|native节点|kernel清单|结论|
|---|---|---|---|
|66 / 10.18.2272|6|rc0 / 6|清单逐项零差异|
|66 / 3.30.705|10|rc1 / stdout0|UNDO_INVENTORY_METADATA_LINK_MISMATCH|
|67 / 3.26.705|6|rc0 / 6|清单逐项零差异|

三笔共22个native节点，只接受两笔12个5.1覆盖；未认证任一完整SYS控制图或历史行值。失败XID第一节点seq66:6712:16:v2是logical0/metadata76bytes/opcode5.7/begin/UBA37749054:436:32，下一COL$节点6712:296:v2为UBA...:33、metadata24bytes、wrap705、previous DBA0且rci0；现有普通heap清单规则要求非begin same-block rci非0，故拒绝。后续8节点rci33—40。这些只是直接字节事实，不猜5.7语义、不删除门禁、不将rci0当隐式前驱。

原rc/stack/stdout和十节点raw metadata保留，native图只作尺子；12清单匹配不代表22节点闭合、四COL$依赖完整或SCN历史可用。未新增source或target动作。

## 保全与下一步

[146文件双次SHA](evidence/DELIVERY-v2-identity-sys-inventory-20261006.json)，上一轮334逐项未变；秘密不输出/哈希/提交，大件ignored。源码执行前pin/执行copy及commit后copy区分。

[接续完整SYS控制派单](DISPATCH_W5_SYS_BEGIN_CONTROL_20261006.md)：先独立核5.7控制与首COL$的分配/前驱语义，再实现有限typed control和完整输入/原始头审计，保持错rci/UBA/缺begin/漏SYS节点反例拒绝。随后完整SYS行与列历史、缺EXTRA=NULL正功能、间接COL$链、活跃跨窗/C++采集/租约/独立目标/矩阵压力断电/评审现场发布继续。仅五身份负例和两清单通过，不收缩原目标；Codex卡顿所有Oracle任务完成后再查，automation保留。
