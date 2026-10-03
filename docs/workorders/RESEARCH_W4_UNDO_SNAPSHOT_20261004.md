# W4 事后undo页面不能冒充归档时点前驱证据

针对冻结seq10的16个undo block，只读Oracle diagnostic dump datafile11的129..137与8561..8567；没有DDL/DML、重启或配置变更。`lab/inspect_undo_blocks.py`仅是native研究审计，绝不作为运行时解码输入。

当前页头的`cnt`与`irb`确有分离：如0x02400082的sequence0xe6、cnt8、irb1，不能用回滚后的irb直接冒充原始图尾。0x02400089页头XID为0x0001.020.0000025b，归档长事务为0x0001.001.0000025c；但页头XID是否应等于每条undo记录XID尚未证明，研究器采用了这一保守假设。因此拒绝本身不能证明页面已被复用，也不能证明页面不可包含目标历史记录。

更正：上一版声称0x02400088归档节点sequence0xe6、事后变成0xe7，事实不符。raw seq10及同件native redo dump中，目标事务在0x02400088和0x02400089的原始节点均已是0xe7，序号差异不能作为反例。错误文字移除，原始执行/拒绝日志不删。页头/记录身份语义与快照时点一致性仍待证，不能将保守拒绝冒充Oracle页复用验收。

审计rc1、stdout空，保留原文：`selected snapshot full XID changed: DBA=0x02400089 expected=0x0001.001.0000025c actual=0x0001.020.0000025b`。不删除失败，不将其它XID页面筛掉后宣称完整性通过。未来若使用页图像，必须有归档时点来源/sequence/完整XID/捕获一致性证据；当前生产路线保持raw redo，跨块previous DBA不足时继续拒绝。

本轮研究未改生产parser，不改变6985d9c封存制品或其302patch/82测试结论。正式证据清单不回写，[附加清单](evidence/RESEARCH-undo-snapshot-20261004.json)单独记录SQL、trace、两次失败日志SHA。W4跨块前驱仍待证，CDC事件0。
