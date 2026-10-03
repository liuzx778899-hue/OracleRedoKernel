# W4 原始undo选择覆盖交付

此前局部cursor可在选择阶段漏掉跨块前最后一个undo仍通过控制及提交尾检查。新增`ArchivedUndoInventory.read`自己扫描完整输入归档，按CON_UID/CON_ID及完整XID收集每个5.1的位置、UBA和元数据引用；`requireCompleteSelection`要求下游位置列表与扫描结果逐项相同，遗漏、重复、重排或混入其它归档位置均拒绝。生产事件出口继续关闭。

源码main `9ffc19b42bdd8e88c7e44887eecaf844b3ff359a`；[封存清单](evidence/DELIVERY-9ffc19b.json)。完整Git archive提交树、Java17独立构建，同件jar SHA256 `501b19f7a47ef45f3007d0ca4d2c7764725d18a0441c08fb7cbb0e9c7cc47125`。98项测试，0失败/错误/跳过。未部署；本轮无Oracle数据库动作，未重放任何fixture DML。

## 验收处置

| 项目 | 当前证据 | 处置 |
|---|---|---|
| 有界源码/制品交付 | 独立提交树重建，98项测试 | PASS代码交付 |
| 原始5.1位置/UBA/previous DBA/rci/opcode覆盖 | seq10/11/12各120，共360节点，与native零差异 | PASS有界针对性验证 |
| 已知完整cursor选择 | 三批位置列表均接受，输出等于自行扫描清单 | PASS |
| 跨块前驱选择漏项 | 三个119节点反例均rc1、stdout空，`UNDO_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED` | PASS安全拒绝 |
| 全XID低wrap冲突、相关未知flag、不可读容器身份、源归档重复/缺目标 | 5个新单元测试覆盖；合成输入明确标记 | PASS单元边界，不代替真实矩阵 |
| 有界补偿回归 | 同件jar三批180补偿/180保留patch含23跨块，native零差异；旧18pair/4补偿及SQL row7保持通过 | PASS原有有界支持 |
| 事件出口 | 三批`events` rc1、stdout0 | 继续拒绝 |
| 完整事务图、补偿生效集合、index效果、恢复/W5 | 无完整证据 | HIGH待证 |

实现返回不可变typed列表，不保存外部可修改的vector byte数组。输入在读取前后各做结构/checksum/SHA扫描；归档之间继续核对来源及sequence/SCN连续性。读取失败不会返回局部inventory。目标容器内无法读取完整5.1身份时不能排除其可能属于目标事务，直接拒绝；能读身份但完整XID不同的记录只作排除，不冒充其语义支持。

首轮低wrap冲突单元样本写错偏移，破坏字段表并报`TRUNCATED_VECTOR_FIELD`。改正到实际sequence字段后5项针对性测试通过；原失败XML与成功日志均保留在SHA清单。这是测试样本修正。

## 范围与下一步

“覆盖完整”仅指绑定输入文件内目标5.1节点的位置列表完整，不能扩张为事务从begin到end完整。该API不接收调用者提供的raw vector子集；但提供一组连续归档仍可能截掉事务起点/结束，必须另经已有生命周期和图门禁。它也不认证所有事务相关opcode、块分配/重用、跨块完整前驱和补偿后的存活行。位置覆盖比较本身无事件授权，immutable inventory也不是可发布事务凭证。

读取前后SHA一致只适用于冻结输入检查，不代替在线capture一致性和来源认证。未声称可以抵御读取中被恶意替换后恢复的文件。raw块generation/record账和跨块前驱规则还需实现与独立验证；已封存seq10的15条页CNT观察可作验证器证据，事后页不进入生产路径。

下一阶段把这个完整访问路径与begin/end身份、原始图、相关未知操作拒绝组合，再验证缺节点/控制错配/槽位复用和恢复；保留当前`UndoLink`对未证跨块边的拒绝。W5字典/基线及StreamHouse接入门禁不变。
