# W4 同叶完整操作清单交付

并发slot迁移研究证明，过滤目标XID会漏掉改变目标键位置的B插入。本轮实现由内核拥有的同叶清单，收录目标事务已验证活动record窗口中的所有同叶vector；当前入口只接受已观察的10.2 INSERT、10.4 DELETE、10.3 PURGE、10.5 RESTORE，其余同叶操作拒绝。这是位置重放的输入门禁；**slot重放和并发完整DML未实现，matcher仍拒绝真实slot1→2**。

最终完整源码树 `13a87c009f43f43dc30f8adf3437c54cf82014e5`，含补充唯一回滚叶操作检查fc34771；Java17封存树重建154项测试、0失败/错误/跳过。jar SHA256 `fe8e4973a4110c5d26d1b01eedbd15de5a8f1f375dd5ca912494bfa167ab45b4`。[证据清单](evidence/DELIVERY-leaf-inventory-20261004.json)绑定最终源码、制品、真实输入、native对账、反例与命令，共1000文件逐项重算一致。本轮无数据库动作，使用既有冻结seq8/9/18/19。

## 实现边界

- `ArchivedCompensationInventory` 对外提供独立验证过的begin/end **record**位置，vector=0；范围包含起止record，不凭target SCN数值自行猜测。既有补偿清单行为保留。
- `ArchivedIndexLeafInventory.read` 自主读取每个完整归档，前后SHA/源头绑定既有undo清单并检查来源连续性；在窗口内选定uid/CON/leaf DBA，**不按目标XID过滤物理叶操作**。
- INSERT/DELETE逐个完成同record完整UBA undo配对，保留实际原始XID/UBA、ITL/slot/row size、键和六字节keydata；数组转为不可变hex字符串。INSERT的keydata来自forward，DELETE来自inverse，不伪造缺失字段。
- PURGE/RESTORE严格解码已观察layout并要求同record唯一对应index metadata marker；一个marker不能认证两个回滚叶vector。该关联只是清单描述，不认证marker事务归属、history head或取消成功。
- 清单/源列表不可变，构造器私有；调用者提交的position子集必须与内核清单顺序完全相同，漏项、重复、重排和外source位置均拒绝。未知同叶维护、cleanout等opcode在返回清单前拒绝。

入口仍要求调用者提供index object与leaf DBA；**此映射尚未由SCN字典证明**。当前严格要求objn=objd=指定index object；truncate/segment重建等不在支持范围。清单不是原始输入真实性证明，也不能证明源采集从未缺失；持续capture门禁另行验收。

## 真实对账与门禁

新并发fixture位于专属源，seq19 SHA256 `497d09a556faed6b96307f9a439413e08109d0920b75bbeacb909cccbd582d63`。source动作/最终SQL证据沿用[slot研究](RESEARCH_W4_INDEX_SLOT_SHIFT_20261004.md)，没有重放。

| 目标 | 独立native窗口 | 清单操作 | 结果 |
|---|---|---|---|
| A / 10.23.1220 | 19:225418:148 至 19:225422:456 | DELETE slot1、**B INSERT slot1**、RESTORE slot2 | 3项，窗口及所有输出字段零差异；B完整XID4.2.587/UBA37760419:201:22已保留 |
| B / 4.2.587 | 同一record 19:225419:328 | INSERT slot1 | 1项，窗口/字段零差异；排除窗口外A回滚 |

`compare_index_leaf_inventory.py` 从native的目标5.1 Begin trans和匹配segment/slot/完整sequence的5.4求窗口；枚举范围内全部同叶vector，从native完整UBA找到undo，再核原始XID/键/keydata及补偿metadata。期望不从Java输出生成。未知native操作/歧义直接失败。

最终封存验证运行情况见 `lab/out/release-leaf-inventory-r2/commands.json`、summary及SHA清单；下列交付门禁全部通过：

- A四项调用者选择反例：漏掉B、重复、重排、改archive sequence，全部rc1/stdout0，`INDEX_LEAF_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED`。
- 三项独立比较器反例：漏B、交换操作、改窗口，全部rc1且明确差异。仅是证据对账器校验，不当作生产拒绝。
- 三份真实归档派生反例，B vector10.2改10.31、改4.1、改B index undo的UBA sequence，记录物理偏移并重封Oracle块checksum：结构scan rc0；新清单分别拒绝未知10.31、未知4.1和`MISSING_INDEX_UNDO_UBA`，rc1/stdout0。这不是实际Oracle页维护/cleanout执行证据。
- 既有五笔混合图同件jar/native回归22原始/19补偿/3保留；既有slot迁移matcher仍`MISSING_INDEX_COMPENSATION_ORIGINAL`、rc1/stdout0。
- events同件jar拒绝`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，rc1/stdout0；未开放发布。

六批heap756/430/326沿用此前验收证据，本轮不重跑；这次新增清单未在六批上调用，不能宣称新API对全部长事务/其他叶操作已验收。

## 保全与接续

研究驱动首次误用Xid.undoSegment()访问器而实际为usn()，javac rc1；原源码和stderr保留在 `lab/out/index-leaf-inventory-20261004`，改为usn后另名日志重新编译。源码b61f77c首次封存153测试/真实清单/五笔图全部通过，后来补充一个marker不能认证多个回滚叶vector及一项测试，最终13a87c另建r2封存；前次成功树、制品、命令和清单保留，不混作154测试最终制品。

下一步在此 owned inventory 上重放目标位置，绑定原始pair完整身份和调用点的源位置；不能接受调用者任意slot修正值，也不能放宽已有matcher的静态检查。先对本批A原slot1经B插入得到当前slot2，验证漏B/错误补偿slot/重排/重复会拒绝并锁定；再补受控多次插入、前方PURGE、未知维护/split。全部同叶操作未证实、heap Z回滚/完整图/并发marker归属仍有缺口时，不开放CDC。

代码交付及本批输入清单验证通过，位置模型/完整DML/字典/真实capture接入/部署/现场/发布继续待证。
