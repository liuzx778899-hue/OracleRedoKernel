# W4 索引补偿与 heap+index 有序图交付

新增普通 index5.6 / metadata10.22 与同record10.3 purge匹配，补齐首批冻结样本的三次索引取消。mixed研究驱动将heap和index原始节点共同送入已有UndoHistory，不再只证明heap取消后保留集合。最终完整封存树 `9e80b0f`；内核改动首次提交 `d724233`。本次没有数据库动作或配置变更，CDC继续拒绝。

## 绑定与拒绝边界

`IndexCompensation.match`核对同record/CON、补偿metadata与历史原始undo的slot/wrap、DBA、objn/objd/ts、previous DBA/rci、PURGE逆操作及物理block/ITL/slot/rowSize；历史候选必须唯一，物理purge也必须唯一。之后通过新增index `requireActive`重载核对原始undo的完整XID/PDB活动身份。所有matcher拒绝锁定生命周期，不从purge KTB L/C恢复当前身份。

只支持本批普通5.6 PURGE路径；index5.11 begin/cross、RESTORE补偿、其他index维护和slot移动重定位未实现，明确拒绝或保留待证。匹配不直接取消事件；调用者还必须关闭完整输入覆盖及有序head门禁。

## 三笔混合图证据

固定输入为既有无supplemental的seq8/9；owned inventory自己遍历全量目标完整XID的5.1和活动窗口内所有同PDB的5.6/5.11。mixed驱动逐节点检查位置与身份、逐marker核对清单，在close前确认候选覆盖。遍历前后重新核对归档header/SHA；输出只在全部源、生命周期和history通过后发出。清单仍是活动窗口保守超集，不是并发归属证明。

| 真实完整XID | 原始heap+index节点 | 取消 | 保留 | 场景 |
|---|---:|---:|---:|---|
| 3.4.669 | 2 | 2 | 0 | fixture整事务rollback |
| 10.5.1133 | 5 | 3 | 2 | savepoint回滚；保留heap insert7及对应index insert7 |
| 10.27.1133 | 2 | 2 | 0 | 既有ORA-65040切换失败之后的rollback；不重放失败事务 |
| 合计 | **9** | **7** | **2** | 3条index取消 + 4条heap取消 |

独立native比较器只读取dump，按完整原始XID、起点UBA→segment、metadata、原始分配顺序和取消head重建，结束按同segment/slot/完整sequence绑定。三个NODE/CANCEL/LIVE序列全部零差异；这是所列undo图证据，不是完整逻辑DML或所有redo语义。

## 封存验证

- 从完整Git archive 9e80b0f独立Java17构建，**138项测试，0失败/错误/跳过**。新增5项单测包含历史KTB不同事务、歧义、previous/wrap/slot错误、未活动/相同低wrap而完整sequence不同、record/CON混用及失败锁定。
- 三笔真实事务各派生漏index节点、错误purge slot两种研究输入，共6项均rc1/stdout0。漏项模式省略向UndoHistory提交index entry，原始归档不变；被owned输入顺序或有序head门禁拒绝。slot模式修改已读record副本，由index matcher拒绝。是record/选择层派生，不是归档字节损坏或崩溃恢复证明。
- 同件jar回归14index pair/native零差异及12record派生拒绝；既有18heap pair/4补偿/1保留驱动结果与旧封存行文本一致。六批长事务本轮未重跑，不计新验收。
- `events`仍rc1/stdout0，原因 `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。

本地研发在 `lab/out/index-compensation-20261004/`，首轮封存 `lab/out/release-index-compensation/`，最终封存 `lab/out/release-index-compensation-r2/`。首轮通过结果保留；最终追加混合遍历与owned源的前后绑定后重新封存。[交付清单](evidence/DELIVERY-mixed-undo-20261004.json)包含完整源码树、jar、输入/native、命令、rc、正反输出与SHA256。

## 下一步

W4仍缺index RESTORE补偿真实证据、主键UPDATE/DELETE回滚、index slot变动、并发归属、其他控制/维护与恢复。下一次专属实验以新的隔离fixture执行主键值UPDATE及savepoint/full rollback，先冻结raw/native/SQL，再决定RESTORE布局，禁止用当前3条PURGE经验推断通用index rollback。

W5 SCN字典/基线、逻辑DML完整图像、W7持续capture/续接/ACK、StreamHouse接入、部署/现场验收/发布均未关闭。维护输入不能静默跳过后宣称完整事务通过。
