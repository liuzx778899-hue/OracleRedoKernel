# W3 可信基线物理范围修复执行门禁

输入固定为[本轮失败正样本](RESEARCH_W3_FULL_DML_CAPTURE_20261005.md) seq44/45 与实际获取基线2421228。只读复用，不重新执行 fixture/INSERT/DELETE/切换；不截断或修改 capture。先解决解析，再扩展 NULL/混合操作/回滚及目标链路。

现状：CapturedHeapBatch 与 ArchivedHeapRows 都先调用完整物理图，后者随后才按 endRecordScn>baseline.scn 重放。seed 在基线前结束但无 tail，使两入口提前拒绝。应明确选择可信基线重放路径后再实施，普通通用物理 API 仍必须覆盖全部对象事务。

1. 全量原始对象发现、事务生命周期/结束身份与源连续性验证仍必需。选择依据来自 raw 绑定的实际结束记录和可信获取基线；不得直接信任调用者裸 SCN、可编辑旁置 checksum 或 dump/SQL。FrozenHeapBaseline.Snapshot 是公开 record，直接把该类型当不可伪造权限是不成立的。
2. 基线前事务可以被基线重放路径覆盖，但跨基线活跃事务不得因此消失；跨窗、起止缺失、未知控制必须拒绝。补偿路由可能跨上下文，移除旧 owner 前必须证明保留窗口中的 foreign marker 仍完整绑定；不能静默漏路由。
3. 两个完整 batch/rows 入口使用一致物理选择；避免仅修其中一个。保留原始输入摘要、事件身份和整批失败无输出。不要泛化无 tail commit 的关闭规则，不用最后观察 UBA 补引用。
4. 同冻结 seq44/45 验证完整 INSERT/DELETE 两事件、四完整 before/after 与 SQL/native 独立对照及最终 seed 状态；失败继续保留原文，不偷改计数。
5. 必需反例：同 seed 无 tail 被纳入基线后范围仍拒绝；generic full graph 仍拒绝；可信基线或源绑定错误；begin<=baseline<end 仍拒绝；选择遗漏补偿/foreign owner 拒绝；晚语义失败整批 stdout0。既有 UPDATE 输出与摘要回归、已有 closed-empty/rollback-only 和跨基线拒绝应维持。实际改生产后运行适当内核测试，原源动作不重放。

交付必须包含代码/制品/输入/输出 SHA256、rc、失败原文、独立对照、明确有限支持范围及 STATUS 更新。通过后才推进 capture 全 DML 矩阵与目标消费；不得开放 public decoder 或宣称现场/发布通过。
