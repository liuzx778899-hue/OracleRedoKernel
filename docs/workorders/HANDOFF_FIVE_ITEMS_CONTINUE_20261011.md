# 五项开发续接：建议换窗口

当前完整目标不变：完成ARRAY回滚/部分行补偿、相同commit SCN排序恢复、LOB历史/恢复/安全回收、时间类型变体、StreamHouse正常产品接线，然后合并main、清理自己的分支、提供重新编译部署清单，进入模拟测试。D5搁置，TDE不启动。不是只完成本次ARRAY即可结束。

## 当前权威状态

- 内核分支`codex/d2-normal-index-restore`，HEAD `9f7ee18`已推送。STATUS入口`docs/workorders/STATUS.md`。
- 产品隔离树`D:\work\vibecode\StreamHouse-wt\oracle-main-merge-20261010`，分支`codex/oracle-array-temporal-integration-20261011`，HEAD `4e2d12d`已推送。基于main dc593c6，未合并main。
- 新候选core SHA256 `86cb737b55bcffe9c56b8f9d0f709b04b04dc0a583b41a75508958552d253789`；adapter `ddf291e4186a00f3ab14e474d74b6125443cf6c1e69423b84a33cd1fe7722900`；app `fca2520735887401b543f64acc8e907cb3a1d2dbc062960416db48985dd07f7e`。产品完整862项通过，ARRAY20/时间3完整事件、目标零差异、实际CPACK/ACK同字节；复用通过证据，不重跑源动作。见DELIVERY_ARRAY_TEMPORAL_PRODUCT_20261011.md。
- 相同SCN不同XID排序→状态恢复→事务发布边界离线已通过；合成测试不冒充真实同SCN实库双窗。LOB目前静态schema；readCut明确拒绝READ_CUT_LOB_NOT_IMPLEMENTED。历史DDL、代际、页值原点恢复和安全回收仍开发缺口。
- 所有进程句柄已终止：源实验session41349、开发tests/projection session35663、Docker stop session64760。source-stop.rc0，Desktop stop成功，helper不存在。不要重启先前动作。

## 当前未提交工作，下一步直接接这里

源脚本`lab/array_update_rollback_source.py`只执行了一次；新表ORKLAB.T_D2_AURP_20261011，三列ID/TZ/LTZ。三个seed之后可信V4基线，再批量UPDATE3整笔回滚，批量UPDATE3保留后savepoint批量UPDATE3撤销并提交。独立SQL最终三行Paris/UTC01:30纳秒1，after.snapshot仅比较尺子，不能作为事件输入。

冻结目录`lab/out/array-update-rollback-20261011/source`：raw116 `cab026f8824bbcb288b261d6ceff601224b2ce7995c8859f876442564ef90e3b`，6910464字节；baseline.snapshot/snapshot.json、after.snapshot、116.trc、pair-fields.stdout、actions.sql/rc0、source-stop.rc0。不要重执行DDL/DML、获取或归档；只离线用raw116继续。

本轮自己的未提交修改：HeapArrayUpdatePair.java、HeapCompensation.java、ArchivedPhysicalTransactions.java、HeapArrayUpdatePairTest.java。新增两个lab文件ArrayUpdateRollbackFieldsProbe.java/array_update_rollback_source.py。他人旧untracked HANDOFF_D4_LOB_EVENTS_STOP_20261009.md、HANDOFF_LOB_CONTINUE_20261009.md保留。

已经补：ARRAY inverse允许实际16字节KTB C；三行18字节聚合头；新11.19 XR完整补偿matcher，按归属/行数/槽/全部旧值匹配；已证明旧33项回归通过。但完整raw116仍拒绝，不能提交为通过。

第一失败：projection-first ARRAY_UPDATE_UNDO_IDENTITY；projection-r2 inverse行锁拒绝；projection-r3 ARRAY_UPDATE_ROW_PROFILE_OR_LENGTH，栈HeapArrayUpdatePair.rows→matchRollback。最后新增正反例`raw116FullArrayUpdateRollbackRequiresEveryOwnedOldValue`尚未执行。

下一步定位：raw116两个XR11.19分别RBA13486/372、13490/436。前者KTB Z `030d1c004c160000`，每行KDO byte17=0；后者KTB C `020d000000000000e70f4002b3010600`，每行KDO byte17=2（恢复原锁），聚合头18字节/xtype2/ITL2。当前matchRollback固定rows(...,true)要求byte17=0，疑为第二XR拒绝；必须用原字节/native核对后按实际KTB分支修复，不放宽任意值。inverse已用ktb[0]!=2分支区分。新matcher还须确认生命周期/graph完整撤销只输出保留3条，不把整笔回滚和savepoint撤销写出。

离线验证命令：Java17 `D:\work\Java\jdk-17.0.7`，Maven `D:\work\Java\apache-maven-3.9.11\bin\mvn.cmd`，显式JAVA_HOME。classpath先source/classes，再target/classes，最后source/libs/*（旧candidatecore不能先于新classes）。ArrayTemporalProductProbe baseline.snapshot [snapshot.json SHA] 3 inputs/116.raw。之后ArrayRecoveryProbe enroll/resume两独立JVM，核对完整值及事件一致；保留原失败，输出用新r编号。

## 工作纪律

先正常功能，相关逻辑改动才复验，已过窗口不重DML、不重复全套部署。专属oraredo-19c-lab-20261004仅按需启，结束停；helper固定ork-product-runtime，保存日志后删除。共享数据库不动；不清理无关镜像/卷。Java内核只由redo字节出事件，SQL/native只比较尺子。产品仅消费固定二进制，无源码/研究复制。明确文件stage，保留他人dirty。D5不是当前阻塞。

评估：近期已经从新HTTP产品链前进到真实11.19回滚缺口，并非在重复极端测试；但上下文已跨多轮压缩、混有大量D5和历史工作，继续同窗容易混淆当前候选及未完成项，建议新窗口作为唯一写入者。旧窗口交接后停止编辑/轮询。当前goal保持active，不得以部分包通过标五项完成。

## 交接前最后进展（以此段覆盖上述旧失败状态）

- tests-r2 已结束：34项，失败/错误/跳过均0；session3875已exit0，无遗留运行命令。
- projection-r4.rc=0、stderr为空，冻结raw116只输出保留事务10.20.5217的3条完整UPDATE，整笔回滚和savepoint撤销未发布。before/after完整，TZ为Europe/Paris、LTZ为UTC，纳秒1。尚须独立after.snapshot值比较及两JVM恢复一致性核验，不能把这些后续标完成。
- matchRollback已按ktb[0]!=2区分Z的锁0和C的恢复锁2。最新新增8字节Z/16字节C严格KTB守卫是在tests-r2编译之后修改，须新窗口重新编译并做针对回归；projection-r4不证明该最后守卫已编译。
- 当前仍只支持整组归属节点补偿；任意部分行补偿及LOB历史恢复/安全回收等剩余功能仍必须完成。
- 用户明确授权新开续接聊天，新聊天作为唯一写入者；旧聊天完成交接后停止开发。交接本身不代表五项任务完成。
