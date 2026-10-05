# W5 时点 schema 完整业务事件

接续真实 V3/seq68—69 SYS 历史验收；保留原完整 goal，无 Luna、无子代理，Codex 最后查。

## 已封存输入

O=`lab/out/v3-historical-raw-20261006`，实际基线SCN2536302/SHA226c9322；`new-kernel.jar` d7ee0dfd/c9f340d/296，actual-history-proof.json三SYS提交2536394/6401/6419、22节点/764值、native/ASOF0差异；四marker擦除一致，七合法checksum raw语义拒绝。N=`lab/out/v3-history-ckix-candidate-20261006`，旧V2静态影响检查通过。374双SHA，源ADD/DROP/两UPDATE结束，**不能重跑源动作**。基线业务ID0/TXT dictionary-restored/隐藏NOTE anchor；ADD后EXTRA_V3 nullable NULL，DROP后恢复3物理列。

## 实现与可证伪判据

1. 历史物理/完整行入口只能接由 raw factory 创建的 timeline，与完全相同的 baseline SHA/source/gen/输入headers绑定。旧固定schema入口继续历史拒绝；不能把typed Timeline/SCN数组当可外部手填 authority。
2. 事务发现、original/compensation/control完整图沿用已验证内核；对已在可信基线前闭合的事务沿用基线覆盖规则。解行正逆pair、补偿与extent的列数取其真正record SCN schema；不套当前最终3列，也不把整批一律4列。
3. 按commit重放前将全行状态从前schema映射到可信时点schema。新增尾nullable/default-null列完成NULL的依据必须来自完整COL$/布局和值；维持NULL/ABSENT区别。DROP/字段identity或generation变化不能凭列数猜测；非空default/其它类型/不可证映射拒绝。未知临时schema拒绝，不能跳过事务、依赖或事件。事务跨schema/活跃重叠未支持必须显式拒绝，后续仍需实现原目标。
4. 对冻结68/69两次UPDATE生成完整before/after，第一笔4列明确EXTRA_V3=NULL、第二笔3列，隐藏NOTE仍anchor；事件SCN/完整XID/位置/字段与native、独立ASOF和实际源事务逐项相符。完整image与schema digest随事件携带；批次不能只有最终固定schema。全部输入/语义门禁通过后才交付事件，没有部分输出/目标推进。
5. 反例覆盖错baseline/source/gen/输入、缺SYS历史/marker擦除、错schema时点/新增列完成、wrong-before/原始完整覆盖/回滚与补偿。真实原/擦marker positive应相同；破坏证据0发布。必要旧固定schema影响检查，已过轮次不重跑源DML。
6. 然后产品V3获取账/可信回执/持久恢复、adapter逐事件完整schema/字段、实际HTTP发布→目标原子commit→controlCP→ACK，所有字段独立核验、重复/重启稳定。源事件完整性与目标字段映射分开；不能隐去EXTRA_V3以让固定schema通过。未换装/不改共享环境，产品新候选必须有自己的必要构建与运行证据。

提交安全代码/测试/摘要与派单，样本/secret留lab/out，保全所有失败原文、制品/input/output SHA、rc/事件数。跨窗活跃状态、正式C++/租约、独立目标、环境类型压力断电、评审现场发布仍属于原目标，不能以本有界通过缩减。
