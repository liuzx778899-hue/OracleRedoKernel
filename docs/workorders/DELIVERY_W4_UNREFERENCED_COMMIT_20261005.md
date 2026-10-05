# W4 flags0 普通提交无 native tail：有限物理闭合

生产/lab69256c9，kernel SHA256 `2bcea2cb214aea301788a098bc4b19dab8e3b982695b26cb8e79952aafa959af`，完整249测试0失败/错误/跳过。[双次 SHA 清单](evidence/DELIVERY-unreferenced-commit-20261005.json)，实际输入引用[NULL研究封存](evidence/RESEARCH-null-dml-capture-20261005.json)，目录 `lab/out/unreferenced-commit-20261005`。没有数据库动作或源DML重放。

前一轮真实 baseline 后 DELETE 使用 flags0、两字段、没有 ktucf tail，完整生命周期有效而旧图关闭要求 tail 导致拒绝。现在 ArchivedPhysicalTransactions 在原始 lifecycle 正常结束、完整 marker/undo 覆盖核验后，仅对 decode 成功的 flags0/两字段 commit 调用独立 `closeCommitWithoutNativeTail`。该方法校完整XID/PDB身份、全部节点输入、无补偿、activeHead==allocationTail，同一 undo DBA及sequence；不调用 `close(lastSeenUba)`，也不把分配历史尾称作 native 提交尾。跨 undo 块、有补偿的此布局仍拒绝；带引用布局仍用原 tail 校验，rollback 用原完整补偿闭合。

完整冻结 seq46/47 的三事务（seed、INSERT、DELETE）共6原始/0补偿/6保留，source-owned自主物理输出与独立 native 完整节点/位置/UBA/对象/顺序零差异。原三个 native end 的布局与身份此前已独立对照，flags0 DELETE 没有尾引用的事实保留。generic入口现在可以支持这类合法普通无引用提交，上一轮“一切缺tail都拒绝”的观察只属于旧制品；不再把它当不变的产品需求。flags2两字段缺tail仍拒绝。

六真实 archive 重封反例均结构scan rc0、物理入口rc1/stdout0：引用布局缺tail、缺5.2 start、缺heap undo、错end slot、未知end flags、错同块前驱。缺任一项不能靠普通commit标记关闭。独立 native graph 比较器删除最后LIVE输出反例 rc1，明确检出缺失。原两非空 INSERT/DELETE 完整 batch 与旧三 UPDATE batch 输出逐字节不变；原 rollback-only 仍空，实际跨基线仍拒绝，public events仍拒绝。

完整 NULL batch 现在越过物理闭合门禁，停在 `ROW_REPLAY_INCOMPLETE_INSERT`，rc1/stdout0/事件0。这是已验证的物理图进展，不是 NULL DML 通过。未改物理 ABSENT 语义或补值，没有更新已验收 adapter 私有依赖、target消费或部署。新无引用路径的更复杂并发/多块/补偿提交布局尚未验收；历史字典、初始恢复、实时agent、全产品/现场/发布继续未通过。

下一步按[NULL完整行研究门禁](RESEARCH_W3_NULL_CAPTURE_20261005.md)：保持完整F/L physical patch的ABSENT，只在可信实际schema验证nullable及省略尾范围后组成完整像；非nullable或中间缺列仍拒绝，DELETE before/extent仍须一致。复用46/47，不重放源操作，先完整两NULL事件/native/SQL，再混合/rollback capture与目标消费。
