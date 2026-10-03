# W4 事后undo页面不能冒充归档时点前驱证据

针对冻结seq10的16个undo block，只读Oracle diagnostic dump datafile11的129..137与8561..8567；没有DDL/DML、重启或配置变更。`lab/inspect_undo_blocks.py`仅是native研究审计，绝不作为运行时解码输入。

当前页头的`cnt`与`irb`确有分离：如0x02400082的sequence0xe6、cnt8、irb1，不能用回滚后的irb直接冒充原始图尾。更关键的是事后页面已不是归档时点图像：0x02400089当前完整XID为0x0001.020.0000025b，归档长事务为0x0001.001.0000025c；另0x02400088当前sequence0xe7，归档原始长事务节点sequence0xe6。该观察与页头变化/历史复用一致，但未单凭事后dump断言具体复用机制。

审计rc1、stdout空，保留原文：`selected snapshot full XID changed: DBA=0x02400089 expected=0x0001.001.0000025c actual=0x0001.020.0000025b`。不删除失败，不将其它XID页面筛掉后宣称完整性通过。未来若使用页图像，必须有归档时点来源/sequence/完整XID/捕获一致性证据；当前生产路线保持raw redo，跨块previous DBA不足时继续拒绝。

本轮研究未改生产parser，不改变6985d9c封存制品或其302patch/82测试结论。正式证据清单不回写，[附加清单](evidence/RESEARCH-undo-snapshot-20261004.json)单独记录SQL、trace、两次失败日志SHA。W4跨块前驱仍待证，CDC事件0。
