# W4 起点身份绑定交付

源码`0b36846cd1a6b5f1a305a9180381d9eed01ab29d`，main。完整提交树独立重建36项测试0失败0跳过，jar SHA256 `261c934c4cf424fa36b931d4243a03cb820f25655132e465132254bd2977a377`；[封存证据](evidence/DELIVERY-0b36846.json)。没有新DML、部署或目标写入。

`TransactionStart`解释实证5.2形态的segment DBA、slot、sequence、UBA、flags、PDB UID/CON_ID。`bindBegin`扫描完整record里的5.1，以相同CON_ID和精确UBA（DBA/sequence/record）找唯一候选，要求begin属性、slot/sequence一致，从该候选读取完整XID；不使用CLS→USN计算，不使用邻接或vector顺序。

同件jar只读冻结19c seq8/9，限定目标表dataObject73024：9+2个起点与独立Oracle dump零差异，decode/comparator均rc0。包括整笔回滚3.4.669、savepoint事务10.5.1133、跨归档6.12.579、交叉会话10.32.1136和9.19.579。此处证明起点身份，尚不证明提交、补偿或行事件归属。

新增6项测试覆盖顺序反转、精确UBA、不匹配UBA、缺失、slot与sequence错配、重复UBA、UID/CON_ID混流、未知flags、parent事务和非begin拒绝。slot复用反例是有界单元输入，真实重复/跨事务安全矩阵尚待证。

首轮seq8研究probe宽选5.2，在非目标记录上拒绝：代码曾把offset20的FBI误约束为0，已删除该错误约束。失败stdout/stderr保留；flags与parent身份约束仍在。修订probe通过候选5.1的精确UBA及dataObject限定目标73024；系统事务被排除，不能据此声称全部5.2形态支持。

六列：代码交付通过；针对性起点对账通过；完整事务集成待证；部署未开始；现场验收待证；发布未开始。CDC事件0。

下一步仍是来源身份/归档连续性绑定、起点/结束状态机、5.6/5.11原undo链与逆变化关联，再进入W5字典SCN和行基线；未完成不得开放`events`。
