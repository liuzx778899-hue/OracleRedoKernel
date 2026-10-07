# W4 pending undo图恢复候选

kernel候选 SHA256 **b6d5d792dec12cb854dac9825a777ffaa2c93f4e9a28598406aefd2dbd58c9fa**，完整 **322/0/0/0**。新增UndoHistory Action class，UndoHistory及Result class字节改变（Result源码未改，声明行移动影响调试信息），另121个class不变。无新依赖、源动作/获取、目标发布或产品换装。

`UndoHistory.checkpoint()`保存已验证append/compensate操作序列、源header摘要、角色/control-root摘要及每条原始entry摘要。`restore(inventory,bytes,trustedSha256)`先核外部可信摘要，再在源字节生成的inventory上重放原append/compensate检查；不相信文件声明的head或自选vector。允许同源连续的inventory扩展，但原source前缀与entry必须精确保留，checkpoint操作不能引用新增suffix中的entry。原顺序、selector覆盖、同block/跨block前驱、补偿active-head、allocation-tail与end约束不变。调用方仍需保存可信摘要和原始输入；checkpoint不是事件授权。

冻结73/74真实实验，源事务不重放：

- JVM1仅从73形成完整选定XID inventory（一个original），导出identity及graph，force(true)后Runtime.halt(23)。graph SHA `217b8119b58651750750a1a5e8affd8049a01c73f0e23e8dfc677a38479ccd89`。
- 独立JVM2读取同原73及74的扩展inventory，恢复原identity/graph并仅处理74续写。73:219164:16:3 UBA `024007e2.0136.0a`，74:19:16:1 UBA `024007e2.0136.0b`/rci10，native 5.4 tail `024007e2.0136.0b`全部一致；同XID10.14.2869、begin record SCN2743312/end2743344。完整original2/live2/compensated0、allocated=head record11；rc0。
- 实际graph字节毒化保留原可信摘要，rc1/零stdout，`UNDO_CHECKPOINT_DIGEST_MISMATCH`。五项单位验证覆盖补偿后续写及allocation-tail/active-head分离、全回滚pending恢复、重复/缺头/截断/错摘要、未完整close/失败锁定，以及源输入重封合法checksum后的SHA改变：entries完全相同仍拒绝复用旧checkpoint。

native比对只作独立尺子，不进入生产恢复输入。这里为受控JVM硬退出，不当主机断电durability证明。真实恢复probe只验证identity和undo图；补偿恢复的本轮正例属于单位fixture，未冒充真实跨窗savepoint/rollback验收。

封存 `lab/out/cross-window-undo-20261007`：[36文件双SHA证据](evidence/DELIVERY-undo-graph-resume-20261007.json)，写后逐项复核，前42+88+182完整不变。全部已通过轮次不重跑。完整目标尚未完成：历史字典/索引/完整行、产品发布/目标/CPACK跨窗口持久恢复仍待证，0事件输出。下一接这些状态及raw前缀绑定、恢复后完整事件对账，再私有产品集成；正式C++、一般矩阵、独立目标/评审/部署现场发布未过。
