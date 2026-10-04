# W5 相同结束record SCN拒绝验证

代码未改，复用5bfde64运行时及既有229测试制品，不重跑通过的构建。派生脚本180d640；输入基于已冻结并发seq33/34，未执行数据库动作。最终清单见[evidence](evidence/DELIVERY-commit-tie-20261005.json)。这是派生输入安全拒绝验证，不是实际相同SCN事务支持。

独立native begin UBA/segment与end slot/sequence绑定出A XID10.33.1644结束位置34:8:436、record SCN2349213；B XID9.16.599结束位置34:2:148、record SCN2349206。两方向分别只改一个结束record header SCN为另一个值，重封所在512字节块XOR校验和，保留位置、向量/字段、UBA、事务身份与提交尾。B的结束和原始操作共处一个record，因此该方向同时改变那一个record的所有vector所处record SCN；没有修改vector SCN，也不假称单独修改了commit vector。

| 派生输入 | 修改 | SHA256 |
|---|---|---|
| a_end_to_b | A结束record2349213→2349206 | 3e8e25e8b453c38f610635e9a0e9a934ecc7ffab3cf6e0e0323c6452bdfe2455 |
| b_end_to_a | B所在record2349206→2349213 | cbc0e33bb0838e107b32c3514338d9b6fd33d96a942fc7a35a819a6fd234e6a3 |

两份均scan rc0、完整physical rc0且保持三XID/6原始/0补偿/6live。行入口均明确`AMBIGUOUS_ROW_REPLAY_COMMIT_SCN`、rc1/stdout0。没有用早期结构/物理拒绝代替指定分支验证；没有按XID或原始到达顺序猜测相同SCN的发布顺序。两份输入和所有命令/rc/stdout/stderr、复用jar/原始输入双SHA封存。

尚未证明实际相同结束SCN支持；记录header SCN与通用Oracle提交顺序证据的适用边界继续待证。持续捕获、接入和现场/发布未完成。

## 空结果入口的下一步约束

当前`ArchivedHeapRows`在存活原始heap位置集合为空时抛`NO_ROW_CHANGES_AFTER_BASELINE`，而`ArchivedPhysicalTransactions`在没有发现目标事务时更早抛`NO_PHYSICAL_OBJECT_TRANSACTIONS`。两者不能一起直接改成成功：当前物理窗口内未知opcode/控制审计依赖已发现生命周期，完全无目标事务的输入没有同等目标heap/leaf范围审计。

先实现“所有目标物理事务已完整闭合，但结束均早于固定基线，或结束后的完整回滚图零live”的已验证空结果；仍核source identity、snapshot覆盖、typed角色、完整归档连续性、缺begin/end/补偿关联、双次输入SHA及返回基线完整行。使用一次新的实际只读固定SCN获取和专属归档切换，连续输入覆盖先前已提交事务到新snapshot；独立SQL核完整基线/最终相同，native核闭合与结束时点。不得编辑旧snapshot伪造一次实际获取。

“输入完全没有目标事务”的静默识别需要额外目标向量/未知opcode覆盖证据；未收到end的活跃事务仍为未完成，不能报静默成功。该后续阶段仍必须做，有限闭合批次空结果不能当W7持续静默/续接/ACK验收。新功能至少以真实完整回滚零live、真实最新snapshot、缺结束拒绝、未知输入拒绝和现有并发行/回滚回归可证伪，不只测试一个空List。
