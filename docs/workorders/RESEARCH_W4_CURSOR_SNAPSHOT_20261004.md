# W4 独立前驱页CNT佐证

研究源码140711e；[证据清单](evidence/RESEARCH-cursor-snapshot-20261004.json)。读取已有seq10 redo dump、已有16页undo dump和b64a090研究输出，无数据库动作，无生产制品变更。

`lab/compare_cursor_snapshot.py`独立从native redo确定完整XID `0x0001.001.0000025c` 的120个原始undo节点和15个跨块previous DBA；从native页面取得这些前驱块的sequence/CNT，而非从cursor推导前驱记录号。每个引用页面必须有一致cache/disk观察、目标完整页头XID，其CNT必须命中已观察的目标原始undo，页面该记录的slot1/object73043必须一致。不能筛掉不合格的被引用页面再宣称通过。

结果：完整研究cursor的15条边全部匹配；缺节点cursor第一个跨块边期望 `37757297:229:33`，实际 `37757297:229:32`，独立比较rc1、`SNAPSHOT_CNT_PREDECESSOR_DIFF`。这给出区别于“统计120节点”的边值反证。

16页中只有最后一页0x02400089没有被任何跨块previous DBA引用。该页头XID `0x0001.020.0000025b`、sequence0xe7、CNT23，与目标事务结束尾rec4不能混用。原审计对全部16页套用同一页头XID假设而拒绝的事实继续保留；本研究不认证这最后一页，也不把其CNT23当目标提交尾。新比较器将未引用页明确输出，未删除或静默过滤它。

本结果仅说明15个事后前驱页的sequence/CNT观察与冻结样本候选边一致。页面时间一致性、通用CNT跨块链接规则及raw-only生产恢复仍未证明，不能输入runtime或打开事件发布。尤其CNT与IRB在部分页面分离，原始链与补偿后的存活效果必须分别验证。

下一步实施方向：通过内核拥有的完整raw访问路径逐条覆盖5.1，维护独立的块generation/record覆盖账和事务节点集；跨块边必须同时满足控制身份、块覆盖和事务图约束。完整性不得来自调用者传入子集或fixture固定计数。先用当前15条独立页观察佐证块账，再验收遗漏/重复/顺序/相关未知布局反例；不将事后页作为生产依赖。
