# SYS非行begin与首COL$控制原始证据

代码6404423，只新增两个只读lab研究工具。复用冻结seq66/672f01bc内核；无生产修改、无源/目标动作、不重跑已接受271/737或源事务。原完整goal active，SYS历史功能与现场发布未通过。

## 逐字段事实

自主遍历归档，以完整XID3.30.705选择5.1，再以同record精确UBA定位唯一段37748896并收集段/slot/sequence控制。研究工具中的专属container常量和指定XID只作冻结实验选择器，不进入生产解析或授权。

十条5.1 identity/UBA/metadata object/flags/wrap/previous DBA/rci/opcode及begin/head/end三控制位置/SCN/DBA与native零差异；末尾5.4 flags2的tail=37749054:436:41与最后分配节点一致。完整控制图尚未认证，不能把字段核对当图闭合。

|原始位置|关键字段|意义边界|
|---|---|---|
|66:6712:16 v1/v2|5.2 flags0x51、slot30/seq705、UBA37749054:436:32；5.1 obj0/op5.7/begin|非行begin控制存在；旧TransactionStart不支持flags51|
|66:6712:296 v1/v2|5.2 flags2、seq0、精确UBA...:33；COL$21/data2、wrap705、previous DBA0/rci0|首行头赋值存在，不能据此造出33→32前驱边|
|66:6712:296 v2/v3|逆向KTB L旧XID10.18.2272/UBA37749351:283:61；前向F当前XID3.30.705/UBA...:33|逆向ITL是另一旧事务，不是当前事务undo链前驱|
|同锁记录|LKR clustered tabn5/slot51，逆向to0、前向to2|不同于现有普通heap tabn0/to1锁profile，不可直接套用|
|66:6720:236 v1|5.4 slot30/seq705/flags2、tail...:41|末尾分配地址匹配，不证明所有行before/after|

后续八条节点的rci明确依次引用33—40；两条观测控制、首行根与后续行链需要分别表示。目前门禁仍拒绝原SYS清单/历史事件，未泛化允许非begin rci0。

## 执行与失败保全

首次probe在非目标XID的未知UndoHeader flags失败，stdout0/rc1和失败Java/driver已保全；修正为先核完整20字节XID再解目标header，第二次raw/compile rc0。后续比较器的SCN格式、媒体marker无DBA及KTB空格/旧ITL选择问题保留执行copy与终端失败原文，只重读既有rc0 raw输出，未重复compile/raw采集或任何源动作。5.2 fbi与native核对为byte20，不将其后非零内部字节当fbi整数。

[双次SHA清单](evidence/DELIVERY-sys-begin-control-20261006.json)包含原失败与修正证据；上一146文件逐项SHA/长度不变。大件/原始记录在ignored lab/out，不入Git。最后提交driver新增自动保留失败hook，明确以committed-driver-not-reexecuted区分，不宣称hook执行过。

## 下一步

实现窄typed非行begin和首行根绑定：原始完整XID/段slot/sequence/container/精确UBA/metadata5.7及flags51，同record flags2唯一控制与非begin根rci0必须配对并完整覆盖；分配控制和行链分别表示，禁止臆造begin前驱。补缺控制、重复控制、错UBA/slot/sequence/container/object/rci、漏首/中间节点等合法checksum语义反例，再用三笔SYS全覆盖native独立对照。只有支持该原始形态后继续SYS clustered行值/类型/列名/物理序号和SCN历史正功能。

间接COL$链、活跃跨窗、C++采集/租约、独立目标、完整矩阵/压力断电、独立评审/现场/发布继续保留。Codex卡顿按用户要求在全部Oracle目标完成后调查，automation保留。
