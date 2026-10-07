# W7 跨日志范围profile与完整两UPDATE候选

kernel SHA256 **0bcb39e694892089408b3cbb748a0140c1de98ba23f6db3912f87679e82fcae4**，完整 **313/0/0/0**，针对性 HistoricalSchemaBarrier **13/0/0/0**。两个生产class改变、121个字节不变；无新依赖。原73/74冻结182文件再次全部核验未变，无源业务动作/重放/获取或目标/产品换装。

只读原字节/native研究确认，74:3:16 standalone24.4字段首端2743309对应73中的另一笔事务结束record，并非业务XID10.14.2869的begin。不得把marker当事务或schema authority。没有宣称解释全部范围标志语义。

最小修复仅在完整历史发现入口支持已观察mixed profile：原固定头/VLD5/唯一vector/零地址等校验不变；首高字0000、末高字8000、两padding零、范围有序；首端必须大于可信baseline SCN且在已读同container UID记录SCN集中，末端必须等于marker SCN−1。旧双8000/跨度≤1路径不变；无上下文的profile方法及固定schema屏障仍拒绝mixed。所有事务、SYS历史、范围连续、undo及完整行门禁独立执行，marker不生产事件。

冻结实际73/74完整capture入口rc0，两UPDATE同XID10.14.2869、begin record SCN2743312、commit record SCN2743344：

- 73:219164:16:3，history-product-restored → cross-window-stage-one。
- 74:19:16:1，cross-window-stage-one → history-product-restored。

两事件各含完整三列ID/文本/隐藏NOTE；ID0/anchor由原独立校验可信baseline保留，四个文本前后变化由native undo/forward字节逐项比对，native begin/end/RBA/XID一致。schema均9a0aac66；事件ID/payload独立且稳定，batch31c68b10。重复读取输出字节相同；四种比较器毒化（XID、commit SCN、NOTE、位置）均检出。

仅73依然rc1/零输出 INCOMPLETE_TRANSACTION_IDENTITIES:1。三个DERIVED capture反例（未观察首端、末端不相邻、padding）重封块checksum/帧SHA/metadata SHA/输入SHA，完整入口均rc1/零输出，精确MEDIA_RECOVERY_MARKER_RANGE原因。它们不是新源真实事务。首比较器把首端XOR1变成已观察SCN2743308，候选合理输出两事件，因此不满足“未观察端点”反例条件；该比较器源码、AssertionError原文、输入及完整输出保留，r2改XOR2到未观察2743311后通过，没有修改生产代码来满足比较器。

因本次修改涉及历史发现，使用新候选读取冻结旧70/71作为改动回归，完整输出（包括batch/schema/ID/payload/行）与旧a84候选逐字节一致。没有重放旧源动作，也不重跑旧产品/目标已验收轮次。

封存 `lab/out/cross-window-range-20261007`：[88文件双SHA证据](evidence/DELIVERY-cross-window-range-20261007.json)，写后再次逐项核对。完整目标仍active；这里证明完整闭合跨两个日志的有限内核解析，**尚未证明跨发布窗口持久活跃状态/字典状态、崩溃恢复、产品HTTP/target/CPACK或正式C++采集**。下一实现持久状态/恢复协议及相应真实输入、故障反例，不能将拼接文件或待提交拒绝当恢复通过；独立目标/一般矩阵/扩展类型/评审/部署现场发布仍待证。
