# 中央 redo 恢复切点与安全回收进展

测试环境验收前优先包开发中。已完成原两窗的可信封存、恢复切点登记、中央旧前缀副本回收及删除后新进程恢复；正常新窗口接续和集中验收仍在执行，不标整包完成。

- 内核：`codex/d2-normal-index-restore` / `d2b1ea3`；产品：`codex/oracle-dictionary-acquisition` / `d634666`、恢复接线修复 `3658012`，均已推送。产品固定坐标 `0.1.0-readcut-6368cd5e0f00`，内核 SHA256 `6368cd5e0f006657760fe6f1b4cf6f14dc3e2c13767a13e724e32a9db38cfd61`；修复候选 app SHA256 `0327f08dfbd2097f8f5b060f4734e4ce4c10a8b36e901924b70889eda09a74cf`。适配器、C++ 沿用原可信制品。
- 产品先在源锁/事务内核验实际目标提交、事件账、原 CP/ACK 和 producer，再登记不可变历史锚与 raw 派生快照。客户端不能指定切点或删除路径；真正删除另走服务端文件清单核验。历史审计不依赖被回收 raw，目标完整像及实际 ACK 仍独立核验。
- 冻结 100/101 生成切点 SCN `3090136`，保留边界 101，新快照 SHA256 `bb85c2d9207188a5dbc872aa1c687409422357cdba820722c8d0737ea6e38953`，20 行来自原 redo 状态。先持久化后删除单独测试目录中的 100，实际移除 1 文件 / `209715712` 字节；原冻结 raw/ACK 未变。
- 缺 ACK 和客户端路径请求拒绝。删除后独立进程核验范围 101–101，发现仅 101，重复回收移除 0；目标 applied/member/commit/ledger 保持 `2/2/2/2`，新增事件 0。`resume-r2.rc=0`，helper 删除和自有库停止 rc0。
- 相关产品 52 项通过；新增切换首窗编号/前置 ACK 检查后该类 7 项通过；恢复修复后提交核验类 14 项通过。原核验、未改动路径复用，未跑全套故障矩阵。
- 首次回收后的重启核验拒绝原文保留：`ORACLE_RETENTION_REFUSED → ORACLE_COMMITTED_PREFIX_REFUSED → ORACLE_APPROVED_INPUT_REFUSED → NoSuchFileException`。实际缺口为 JDBC 目标核验再次读旧 raw；修复后用已认证复原投影核验实际目标，未恢复删除文件或重放源 DML。诊断类加载失败、脚本 SQL 失败同样保留，不当通过。

范围：仅正常静态 schema、无 LOB 且相关事务/字典闭合的读取切点；不支持的状态继续保留原始前缀并拒绝切点。只回收已批准消费前缀清单，未宣称所有未批准观察文件或长期磁盘占用已受控。长期平台、生产现场、D5 未启动，TDE Issue #2 搁置。

下一步：正常 102/103 两窗及下一次回收。首条未来 UPDATE 已提交，首次归档命令在 PDB 被拒绝；保留失败，只补根容器归档，不重放 UPDATE。第二条未来 UPDATE 尚待首次执行。用户已授权只读子代理独立评审，但该代理额度不足未运行，独立评审/测试环境验收尚未完成。

证据入口：`lab/out/read-cut-20261010/reclaim-proof.json`、`candidate-r2.json`、各命令日志/rc；旧通过样本与失败均在原目录。此反馈是当前进展，后续统一补最终新窗口与集中验收结果。

正常续接补齐（2026-10-10）：首条 UPDATE 未重放，根容器归档已闭合 102。首次产品续接暴露只读 staging 配置及缺目录，修正测试接线后完成现有队列传输；失败日志保留。冻结 102 又暴露 SYS I_COL1 的双 cleanout KTB、101 闭合头 byte37、TAB$ 三种物理范围形态及三条单主对象 24.1 marker。仅依据实际字节和有界原生诊断补读取：当前 undo 身份单独核验，TAB$ 行槽位/双向链接/覆盖目的地检查所有共享簇字典锚点；触及锚点仍走完整历史，不宣称通用簇解析。marker 只识别已观察精确形态，目标对象仍拒绝。原生 dump 不作事件输入，诊断结束自有库停止 rc0。

两个读取修复相关 14 项通过；字典范围/marker 相关最终 30 项通过。冻结 101/102 正常完整入口 `next-projection-r3.rc=0`，唯一 UPDATE ID=1，NOTE anchor→readcut-next-1，完整 before/after，pending0，事件 payload SHA256 `cf9b23715cb1fa64ccb7859ac958751b5c8b8e7b2b3d1037bca483eb5231c195`。`next-frozen-proof.json` 为离线结果，尚未表示新产品窗/目标提交/CPACK通过；第二条 UPDATE 未执行。下一步更新正式制品接回产品消费既有 102，再执行第二条新变化。独立评审仍待证。

首条未来产品窗已闭合：内核 e3ac633（固定53fa9a98275d），产品37ee409，正式app SHA256 `3e90524a8122d0cafd47251e0271c0715b8379f5f27daf32c7790b1561e8b90b`。raw-stream-21002-3 CONSUMED / PUBLISHED rc0 / 1事件，实际目标 applied/member/commit/ledger `3/3/3/3`、20行零差异，规范化完整前后像一致。实际DONE任务102287965065310208的3605字节CP与ACK完全一致，producer SHA256 `5b1914f2d198d3eefb8ef9952b7b3ea70dca567580087f6ddd32ab783fda9487`、decodedThrough102/pending0；见 `future-window-1-proof.json`。源首条UPDATE重放0，第二条继续执行中。

观察脚本失败单独保留：目标行像为规范化串，JSON筛选错误导致首轮校验无行；随后证据文件Path拼接TypeError、collector停止等待超时原文保留。第一窗产品发布和实际凭证均已核对，脚本错误不当产品通过/失败证明。自有库停止rc0，固定helper另保存日志后清除；已通过首窗不重复整套验收。后续第二窗/再回收/切点毒化/独立评审仍待，不宣称全程同PID或整包验收。

2026-10-10 独立只读评审发现并修复回收前置条件：已消费不等于已封存。删除前要求所有引用受删raw的历史绑定均属于已逐项核验SEALED_COMMIT的前驱链；链外有限窗口拒绝回收。修复独立复核通过，相关16项测试rc0；正式修复制品/实库回收尚待。第二条未来UPDATE ID2仅执行一次，103已闭合并登记窗口4；当前GATED rc0/1事件、任务RUNNING，目标/CPACK待闭合。观察超时保留运行进程，未重放源DML，不标测试环境验收。

2026-10-10 第二条未来窗口实际闭合：raw-stream-21002-4 / batch102311534864363520，CONSUMED/PUBLISHED rc0/1事件；目标applied/member/commit/ledger 4/4/4/4、20行与规范化完整前后像一致，实际DONE任务CP与ACK逐字节相同，producer2f032ce7读至103/pending0。future-window-2-proof.json、windows-r10-verify.rc0；两条新UPDATE各执行一次/旧DML重放0，helper清理/源停止rc0。独立评审P2修复7eaca10已推送，16相关测试通过、复核通过；正式app-r4 SHA256 6b26d0852e15b6b6227f43b734f3c437911f971d2f38e5a507648aa648e5f32f 构建rc0，尚未运行该修复候选。剩余只做新切点保留103/回收批准101与102、一次恢复及切点毒化拒绝，再统一验收反馈；不重复已通过窗口，不启动长期平台/性能调优/D5。

2026-10-10 最终修复候选7eaca10/app6b26d085实库闭环通过：切点102319102395412480/SCN3160456保留103，raw派生snapshot SHA91fb4cdabdee1b03f262c3f781131701a662505fdf0d97dfde773c4f076cb163；只回收批准101/102两副本419431424字节。独立新进程水位103/目标4/4/4/4/20行一致；切点毒化业务409拒绝、零删除、精确恢复原snapshot，ACK不变/新增DML与事件0。final-closeout.rc0，helper删除/库停止rc0。36→34文件、约7.55→7.13GB：未批准OPEN观察文件仍占用，不能宣称全部中央存储有界。已完成代码独立评审及既有D1/D2/D4/背压/两未来窗原CPHEX与实际ACK集中只读核对；最终本次终态独立核对与统一验收结论待收尾，不新增实验，D5不启。
