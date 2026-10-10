# ARRAY 部分执行失败及 pending 恢复

专属实验单次执行：INSERT SELECT20行，第11个索引键冲突；随后新事务INSERT20行并用savepoint撤销20行UPDATE，保持事务跨归档后提交。冻结基线SCN3247067，heap80594/index80595。SQL最终仅seed1及ID200–219，完整值`kept/committed`。原SQLPlus输出断言失败保留，不重放动作；先保留真实pending归档，再只读冻结原实验。`set serveroutput on`已单独成行修正，实际执行版本保存在lab/out。

原始字节及native核等：失败INSERT已产生20堆行/10索引键，分别全撤销；10键清除位图`00fc`，QMI ITL/lock2，undo及XR Z填充`030d24000b00ffff`。补齐这些实际布局；键数必须与原owned索引数量、PURGE槽及总长度一致，未知位图/错误锁仍拒绝。更新实际是20个单行URP补偿，不能作为11.19回滚证据。

- raw112：5069824字节，SHA256 `d3f613838299f02376548d34113f074092de826f0ac475420891e4e4267dd714`。
- raw113：2269696字节，SHA256 `f76ea9f3c38a2c9d4275a0d70086f6d5dbf5458502dd8f6272e550c4fc3f7b1d`。
- 基线SHA256 `33132fea409d593978fce203d55d3853072a688e296e81e8a14c4f3383e175d3`。
- V6 pending首段1事务/0事件，checkpoint `286de5c0325c3c3389392216a6b83c507e315e8a21b6bf219005714c010e5e68`。
- 独立JVM恢复112+113：20唯一完整INSERT，SQL完整值差异0/撤销事件0；与直接历史工厂读取事件、批次摘要、投影证据完全一致。历史envelope SHA256 `f9a4e66e62b22022cc618f64cb4979ab72b0fd608ce287d4769af9b13f7d385b`。SQL/native不作为事件输入。
- 最新34项相关测试失败0，tests-r5 rc0；pending/resume/resume-values rc0。专属源停rc0、固定helper自动移除、Docker Desktop已停。
- 最终内核package rc0，SHA256 `acf311f32006846d854846817eaec8ddb574ac7579f557634188ae1d7c211841`；分支`codex/d2-normal-index-restore`。尚未更新产品固定坐标或部署。

本包证明部分执行失败及savepoint补偿后的跨归档pending恢复。不证明一个owned undo节点只撤销部分行的任意布局，亦不证明11.19 UPDATE补偿。固定schema工厂诊断另拒绝`UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE`，日志保留；可信历史工厂与恢复正常通过，未放宽固定schema门禁。产品新HTTP/checkpoint/ACK仍待，其余四项目标继续，D5搁置；未开始模拟生产验收。
