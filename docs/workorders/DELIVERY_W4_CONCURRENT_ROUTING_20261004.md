# W4 并发 marker 分发与外国 B 回滚图交付

关联清单本身不授权取消。本轮在冻结seq21上接入双上下文lab消费者，完整消费A窗口的B、B、A、A四项关联，将B marker交给B自己的完整生命周期、heap/index物理match和有效head，独立关闭B回滚图。native仍为2原始/2取消/0保留。**完整路径现在越过缺B原始节点的门禁，停在A的中间PURGE位置拒绝；没有认证A完整图或开放CDC。**

完整研究源码树 `40a0fda24cdfad481a2fef16ceae3320c169d1f0`，只新增lab消费者；main/test/pom与已交付内核582fec6差异为空。复用184测试封存jar，SHA256 `b842e9c47e1318326dbbdfc476edca7355f37f89ce1768f4502a2eb1580442e0`。没有重建或重跑已通过单测/旧回归。[证据清单](evidence/DELIVERY-concurrent-routing-20261004.json)关联完整源码、编译class、jar、源raw/dump、查询/图输出、命令rc及派生拒绝，双次SHA一致。本轮无数据库动作。

## 消费路径与证据范围

`ConcurrentRollbackProbe`严格绑定专属冻结seq21 SHA256 `f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80`。object/leaf/列数仍为已观察fixture常量，不当作W5字典认证，也不是生产通用多事务入口。

- A、B各自拥有独立完整原始undo/补偿清单、生命周期、heap/index pairs、UndoHistory及owned叶位置模型。每笔原始节点的位置/身份都与清单相符，再精确UBA配对物理forward/inverse；两笔控制都观察到各自活动状态中。
- 全部四个marker查询必须先消费owned关联清单，完整XID决定上下文。B物理match取得的原始UndoHeader必须与关联结果完全一致，再进入B的head取消；不能仅因关联header匹配而取消。
- B结束时要求B补偿选择覆盖完整B窗口，通过完整rollback身份及UndoHistory空head关闭；源头前后核SHA。B的begin/end、原始2节点、两个取消与最终空保留集合独立native零差异。
- `foreign-only`完整消费四项关联并验证B图，但**只记录A关联，不处理A物理补偿或关闭A图**。输出明确标识 `A_physical_graph_not_processed`；A生命周期结束不充当A语义图证明。
- `full`在B graph真正关闭后处理A物理补偿。最终stderr先出现 `ROUTED_B_CLOSED|2|2|0`，随后拒绝 `UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE`，rc1/stdout0。此前单事务路径的`MISSING_INDEX_COMPENSATION_ORIGINAL`已被该有界分发路径越过，A的PURGE作用仍未实现。

正例stdout含四项ASSOC及B的两项NODE、两项CANCEL。封存脚本仅按固定行前缀拆分为两个选择文件，分别交给独立`compare_compensation_associations.py`和`compare_mixed_undo.py`；完整原始输出、拆分文件和脚本均有SHA，非匹配前缀行导致失败。关联4项/其中B2项、B图2/2/0均零差异，不将物理node计为逻辑事件。

## 拒绝与保留门禁

七项真实输入派生lab反例均rc1/stdout0且为RedoException：

| 反例 | 实际原因 |
|---|---|
| 漏B index原始节点 | UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD |
| 消费关联但漏B index取消 | UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD |
| 重复B index取消 | UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD |
| 改B index回滚slot | INDEX_POSITION_QUERY_CHANGED_FROM_OWNED_SOURCE |
| 改B marker不透明字节 | ASSOCIATION_QUERY_CHANGED_FROM_OWNED_SOURCE |
| 把B分发到A上下文 | ROUTED_OWNER_NOT_ACTIVE_OR_CHANGED |
| 省略外国marker查询 | COMPENSATION_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED |

最后一项在B结束的补偿覆盖检查先拒绝，不伪称已到关联finish。派生修改的是消费/内存vector，不物理删除归档，不算七次Oracle动作。所有输出缓冲到整轮验证之后，失败不输出部分图。

events同件jar继续rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。新消费者只验证本批有限并发分发；未知owner、多个可能原始头、cross-block关联、其他layout/控制、恢复和全量逻辑DML仍不接受。

下一步扩大PURGE实际位置矩阵：较大键插入后回滚、多个中间插入/PURGE以及普通DELETE控制，独立核目录位置与实际布局。保留当前中间PURGE/DELETE/RESTORE拒绝，不能凭一次净slot变化0就实现通用减1。完整A图、生产分发器、W5字典/基线、持续capture/解析/ACK、StreamHouse接入、部署/现场/发布仍待证。
