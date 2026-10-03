# W3 逆向行片段交付

源码`3418fb921da1b602f22ec3f62dff78df7556bc6e`，main；完整提交树独立重建，51项测试0失败0跳过。jar SHA256 `0edd6586347f196f68b0ff9b96467237648e041a3b0b96eb30a52a41089d7f45`。[封存清单](evidence/DELIVERY-3418fb9.json)绑定原始输入、独立dump及输出/日志。本轮只读冻结样本，无DML/部署/目标写入。

`UndoRowPatch`从5.1读取undo真实身份和逆向KDO。当前支持实证11.1 heap undo、8/16/32字节已知KTB及IRP/URP/DRP单行片段；校验header slot/continuation wrap，不从历史KTB猜当前XID。输出inverse patch中的XID/UBA为空，真实身份保留在`identity`，避免混淆旧ITL或恢复后的引用。

KDO列/slot/行片段判定抽取为正向与逆向共用方法；VALUE/NULL/ABSENT保持原义。原INSERT的逆向DELETE只有位置，不造before行；原UPDATE的逆向UPDATE提供改列旧值，未改键仍ABSENT；原DELETE的逆向INSERT含原行出现列，尾部缺列仍不补成NULL。

同件jar对19c seq8/9的目标dataObject73024：15+3个逆向patch、9+1个出现列，与独立Oracle dump零差异。共享KDO改动同时回归原DBA0x03000083的19+2个正向/补偿物理patch、40+5个出现列，零差异。逆向样本还覆盖另一物理块0x03000087；旧正向对账仍只是原DBA范围，不能混为完整表行事件覆盖。

新增5项测试覆盖改列旧值、缺键、INSERT逆向DELETE、历史KTB与真实undo XID不同、显式NULL、header slot/wrap错配、KTB/字段/列map拒绝。实验数据独立生成，无外部参考实现源码。

代码交付与针对性片段验证通过；完整before/after、行与undo精确关联、savepoint补偿链、W5字典/基线集成待证；部署/发布未开始，现场验收待证。`events`仍rc1、CDC事件0。

下一步：原XA行变化与同record唯一undo UBA精确配对，验证DBA/slot/改列map/逆操作及XID；再将5.6/5.11与原undo header、活跃事务身份、逆行变化交叉校验，证明不误取消T007保留修改。不能用邻接、rci+1、恢复后UBA代替证明。
