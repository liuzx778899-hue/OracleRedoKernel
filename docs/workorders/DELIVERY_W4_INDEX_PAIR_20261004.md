# W4 索引叶操作与原始 undo 配对交付

新增 `IndexUndoPair`，在原始字节路径内把观察到的10.2 insert / 10.4 delete与同record的5.1 / metadata10.22绑定。封存源码 `b0771f7`，内核实现首次提交 `d5bdace`。本次无数据库动作、无配置变更，事件发布门禁继续拒绝。

## 行为与判据

当前forward身份只取KTB F/带cleanout的F中的完整XID及UBA，或C中的UBA。undo完整XID来自ktudb，不从历史KTB L/Z/C取当前身份。匹配需要同record、同CON、唯一完整UBA、操作互逆、同索引block/ITL；有显式forward XID时必须一致。INSERT还核对forward/inverse key字节；DELETE向量没有key，保留缺失状态，只从RESTORE undo保留key/keydata，并核对rowSize。

支持本批观察到的单叶布局：forward6字节leaf、24字节F/70字节带cleanout F/16字节C；undo20字节leaf、metadata10.22、已观察KTB L/C/Z。key只接受单个短长度前缀组件，保留opaque bytes；keydata只保留6字节原文，不冒充字典解析后的主键或逻辑ROWID。真实key证据仅覆盖本批2字节NUMBER值，其他类型和复合key仍待矩阵验证。未知布局/模式、缺失或歧义候选拒绝。

## 封存结果

从完整Git archive b0771f7独立Java17构建：**133测试，0失败/错误/跳过**。同件jar验证：

| 检查 | 结果 | 证据边界 |
|---|---|---|
| seq8/9索引真实配对 | 14对：12 INSERT→PURGE、2 DELETE→RESTORE | native独立按完整UBA配对，位置/身份/直接字段零差异 |
| 真实record派生拒绝 | 12项全部拒绝 | 缺undo、重复undo/forward、CON混用、UBAsequence、forward XID、key、block、ITL、模式、短header、未知KTB |
| 既有heap回归 | 18pair与native零差异；4补偿/1保留与旧封存行文本一致 | 平台换行不参与行内容比较，原始输出SHA分别保留 |
| events | rc1，stdout0 | `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED` |

记录派生反例直接修改真实record/vector副本，不是归档字节级篡改，也不证明事务失败锁定；本配对器没有跨record活动状态。新增9个单测覆盖正配对、C身份、歧义、CON/UBA、当前身份/位置/key、布局、未知companion及metadata拒绝。旧124项随完整构建通过，不作为新增验收量。

本地：`lab/out/index-pair-20261004/`研发，`lab/out/release-index-pair/`首个完整封存，`lab/out/release-index-pair-r2/`最终封存。[交付清单](evidence/DELIVERY-index-pair-20261004.json)关联源码、jar、输入/dump、命令、输出、rc及SHA256。

## 失败与更正

首次测试编译失败：lambda引用了随后重新赋值的变量，Maven rc1原文保存。修正后构建通过。首次真实XID反例修改undo sequence，实际由metadata一致性先拒绝；最终改为直接修改forward当前XID，确认 `INDEX_UNDO_OPERATION_BLOCK_ITL_XID_OR_KEY_MISMATCH`，完整重新封存，旧结果不覆盖。

## 未关闭门禁与下一步

PURGE undo没有keydata，不能独立交叉验证INSERT的keydata；DELETE forward没有key，关联依据是唯一UBA及上述一致性，不是forward主键值证明。KTB cleanout尾部、undo未解释字节不认证；本接口只解码所列字段。对象与索引归属尚无SCN字典绑定，indexId没有被假定为某条业务主键；整体CDC继续fail closed。

下一步以3条index 5.6 + 10.3 purge绑定原始index undo，核对补偿归属/顺序，并使heap与index原始节点共同进入完整有序history。10.31维护、未知相关控制、跨块/并发/恢复必须有明确处理，不能静默遗漏后宣称完整事务。字典/基线、逻辑DML、持续capture/ACK、StreamHouse接入、部署、现场验收和发布仍未通过。
