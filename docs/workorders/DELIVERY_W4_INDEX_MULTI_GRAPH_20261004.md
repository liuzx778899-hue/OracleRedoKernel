# W4 多次 INSERT 并发图交付

[seq20位置研究](RESEARCH_W4_INDEX_MULTI_INSERT_20261004.md)仅核验一个index补偿。本轮补齐该冻结输入的两笔完整选定heap/index分配与取消图：A删除后回滚，2原始/2取消/0保留；B三次插入后提交，6原始/0取消/6保留。原始节点、取消顺序、有效head及保留集合与独立native零差异。**这是选定5.1/补偿图的有限验证，不能代替全部redo语义覆盖、逻辑DML或字典验收。**

研究驱动完整树 `e3552af769eb7309d2e17867e2b4e239df052d60`，main/test/pom与已交付内核aa44b0d差异为空。仍使用Java17封存167测试jar，SHA256 `4db98241abfea8579e2502563eca666375e8fb32724a0af403a8a19341e178a3`；没有重新构建或重复既有通过轮次。[证据清单](evidence/DELIVERY-index-multi-graph-20261004.json)绑定完整源码树、编译class、源raw/dump、命令rc、输出及首轮封存失败，全部文件两次重算SHA一致。本轮没有数据库动作。

## 验证结果

归档seq20 SHA256 `620c6361a3986fee40a18ccafb68e9b6a6b2cb9f59fc9db10fb70ce510aea400`，来源与最终SQL沿用位置研究；不重放DDL/DML。

| 完整XID | 原始节点 | 取消 | 保留 | native差异 |
|---|---:|---:|---:|---:|
| A 10.29.1230 | 2 | 2 | 0 | 0 |
| B 9.16.584 | 6 | 0 | 6 | 0 |
| 合计 | 8 | 2 | 6 | 0 |

A原始heap DELETE和index DELETE均撤销，index slot1经过B三个插入变为RESTORE slot3。B的六个5.1节点位于同一record20:27626:328 vector9—14，head依次为UBA37760641:224:48—53；完整保留集合按head顺序反向输出。不能将三个逻辑INSERT计作六个事件，也不能把物理heap/index pair自动合并为逻辑DML。

`compare_mixed_undo.py --multi-fixture`在native中按完整XID提取5.1、独立开始/结束身份、metadata、分配前驱、取消head和保留集合。它不读取Java模型来计算期望。原本有限窗口中的未知object补偿仍拒绝；不能据此认证所有同slot异XID控制归属。

七项真实输入派生反例，全部rc1/stdout0：

- A漏index原始节点、重复index取消：`UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD`。
- A修改回滚slot：`INDEX_POSITION_QUERY_CHANGED_FROM_OWNED_SOURCE`；修改leaf模式：`UNSUPPORTED_INDEX_ROLLBACK_MODE_OR_KTB`。
- A、B选择清单省略同record的中间INSERT vector5：`INDEX_LEAF_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED`。
- B漏index原始节点：`UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH`。

漏vector反例修改的是调用者选择，**没有物理删除raw vector**；owned清单仍从完整归档自主读取，拒绝不完整选择。它不能证明采集端缺失raw块/vector会被端到端发现。三项native比较器反例（漏原始节点、交换原始节点、改保留object）均检出；这是验证器自检，不计生产拒绝。

同件jar `events` rc1/stdout0，原因 `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。CDC仍零发布。

## 失败保全与接续

首轮封存脚本的wrong-live比较器反例试图把末行73064改为73063，而实际末行原本是73063，派生文本未改变，比较器正确返回rc0。原错误派生、rc、比较输出、脚本及终端失败全文保留于 `lab/out/release-index-multi-graph`；不能将该轮算全通过。随后只在另目录 `release-index-multi-graph-supplement` 修改实际末行73063→73064，确认派生不同，native检出1处差异；再执行原本未到达的events拒绝检查。已通过两笔图与七项生产拒绝没有重跑。

本批有限位置及选定图验证完成；下一步专属新fixture研究中间PURGE和普通DELETE的真实物理差别。二者与中间RESTORE目前继续拒绝，页维护/恢复/完整控制覆盖/W5字典/持续capture/接入/现场/发布仍待证，长期目标保持active。
