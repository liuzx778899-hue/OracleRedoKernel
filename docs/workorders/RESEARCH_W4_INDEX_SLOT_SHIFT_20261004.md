# W4 并发索引 slot 迁移反例

同一叶页的并发插入会改变另一事务待回滚键的 slot。现有 `IndexCompensation.match` 要求原 DELETE 和回滚 RESTORE 的 slot 相同，因此对本批真实输入安全拒绝；该路径的功能支持未通过。不能删除 slot 条件来冒充解决。

研究脚本/驱动封存提交 `b19e5a25f211c8f2af95350405908990f7c2b3c2`；[SHA清单](evidence/RESEARCH-index-slot-shift-20261004.json)共569个文件逐项重算一致，包含原始归档、dump、两会话命令、首次失败、完整Git archive源码树、编译classes、比较器和输出。生产内核仍为 `5002f181b04e3a64ff6a92c69808dafff3a60f9a`；沿用jar SHA256 `7421e383e62da6d9eef65af94f6c8cc218861b0f949f93781f9f685d045602b9`，既有147测试证据不重跑。本轮没有修改生产解析器。

## 一次性专属动作

脚本 `lab/index_slot_shift19.py --execute` 仅绑定 `oraredo-19c-lab-20261004` 的owner、镜像、运行状态、DBID1276726821、既有ARCHIVELOG、MIN/ALL均NO。新建 `ORKLAB.T_INDEX_SHIFT_20261004`，heap73061、PK73062；基线键1/3提交。没有源端配置变更或重启。输出目录已存在即拒绝，已执行DML不得重放。

1. A删除键3，XID **10.23.1220**，记录SCN2255335；创建就绪标记后有界sleep20秒。
2. 协调器确认标记不存在于实验前、标记出现后A进程句柄仍存活，再执行B插入键2并提交，XID **4.2.587**，提交后SCN2255339。B完成时A仍存活，日志保全。
3. A回滚前SCN2255346，rollback后事务NONE，源SQL最终为1/one、2/two、3/three。
4. 一次性归档、只读copy及独立Oracle dump，SCN范围[2255300,2255359]。归档seq19，115421696字节，SHA256 `497d09a556faed6b96307f9a439413e08109d0920b75bbeacb909cccbd582d63`。采集和dump命令均rc0。

宿主时间只是辅助；上述顺序还由同一redo流的RBA和record SCN独立确认。最终SQL相等不替代解析或事务验收。

## 原始字节和native的证据

同一index叶DBA **0x0300013b**；native与字节研究器的11个相关vector位置/操作/直接字段零差异（10.31×1、5.1×4、10.2×3、10.4×1、10.5×1、5.6×1）。10.31只做清单，不表示已支持页维护。

| 行为 | RBA十进制 seq:block:offset / vector | record SCN | ITL | slot | row size |
|---|---|---|---|---|---|
| A DELETE键3 / 10.4 | 19:225418:148 / 3 | 2255335 | 2 | **1** | 13 |
| B INSERT键2 / 10.2 | 19:225419:328 / 3 | 2255336 | 3 | **1** | 13 |
| A RESTORE键3 / 10.5 | 19:225422:16 / 1 | 2255346 | 2 | **2** | 13 |

A原始5.1：完整UBA **37749016:244:12**，metadata index73062、slot23/wrap1220、previousDBA0/rci11；inverse RESTORE含键 `02c104`、keydata `030001330001`。A回滚同record有5.6、metadata相同；历史KTB L属于基线XID10.22.1219，不能当作当前回滚身份。生产配对器先建立A的完整IndexUndoPair和活动身份，再调用既有matcher。

原 DELETE 的leaf字段 `020001000d00`；B INSERT `030001000d00`；回滚 RESTORE `020102000d00`。原 slot1 → 回滚 slot2 是真实输入，不是派生修改。

| 门禁 | 本轮结果 | 状态 |
|---|---|---|
| native位置/直接字段 | 11/11、零差异 | PASS有限结构研究 |
| 并发顺序/迁移独立比较器 | raw/native、RBA/SCN、两会话SCN、最终SQL、拒绝输出均检查，零差异 | PASS此反例证据 |
| 比较器反例 | 改RESTORE slot、改DELETE slot、漏B INSERT，各rc1且明确差异 | PASS三项证据篡改检出；不是生产拒绝测试 |
| 既有matcher | rc1、stdout0，`MISSING_INDEX_COMPENSATION_ORIGINAL` | SAFE_REFUSAL；真实功能缺口 |
| events | 同件jar rc1、stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED` | 未开放CDC |

研究probe只针对新冻结fixture调用索引配对/匹配；没有证明完整补偿输入覆盖、所有控制、完整并发事务取消图或逻辑DML。本轮不拿probe拒绝代替完整DML验收。

## 追加失败

首次 `setup` 在只读身份检查PL/SQL声明使用保留字MODE，ORA-06550/PLS-00103、rc1，发生在建表之前。原目录 `lab/out/oracle19-index-slot-shift-20261004` 和SQL/日志保留；修正为lab_mode后另建r2目录执行一次，原目录不覆盖。

首次封存驱动编译虽然设置子进程PATH，Windows可执行文件解析仍命中Java8，class61/52与HexFormat错误、rc1。`lab/out/release-index-shift` 保留完整失败树/命令/脚本。修正验证器为绝对JDK17 java/javac路径，独立 `release-index-shift-r2` 重编译、读取同一冻结输入，所有本轮结果与569项摘要重算一致。未重放任何源动作。

## 接续门禁：W4-SLOT

先按原始叶页完整操作流跟踪目标键位置，再决定matcher入口。候选最小模型：从已配对原操作的slot起步；后续同叶INSERT在该位置或之前插入时移动目标位置；物理PURGE在之前移除时反向移动；DELETE/RESTORE是否保留位置需要逐项实验。完整有序覆盖、源身份、目标原始UBA、生命周期和history head仍必须同时成立。这个模型现在是待验证方案，不是格式或生产支持结论。

必须交付：

- 由内核拥有的输入/操作清单及位置状态，不能让调用者随意传一个“修正slot”；同叶其他事务操作必须参与，漏掉B、重排、重复或漏页维护均拒绝。
- 对本批A的两节点heap+index取消图和B的提交图做独立native对账；当前heap Z/控制/并发候选归属若有其他缺口，逐项保全，不能只验证index成功就关图。
- 新实测反例覆盖多次较小键插入、较大键插入不移动、前方键物理PURGE、错误补偿slot、跨叶/页split与未知维护。未经证实的维护必须锁定拒绝。
- 同件jar回归原五笔混合图22/19/3、六批heap756/430/326及既有真实派生拒绝；events在W5字典/基线和完整事务门禁通过前继续拒绝。

W4并发支持未验收，W5/持续capture/StreamHouse/现场/发布均保持原状态。
