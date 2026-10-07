# W4 活跃物理前缀状态候选

kernel SHA256 `7ff4a5e72abcf379b66091e75be71c9a9dc294ead488cce0003dd46ca5706f89`，最终完整 **328/0/0/0**，新增六项针对性测试。六个生产源码文件最小传递内部 pending 模式；七 class 字节变化（Context 仅调试声明行变化）、新增三个内部状态 record、117 class 不变。无新依赖或源/目标/产品动作。

统一恢复的前置缺口是旧对象发现、补偿 inventory/association、index leaf/position 与 physical factory 在前缀未结束时先拒绝，无法生成经过原物理门禁的 pending 状态。新增内部 readPending/readHistoryPrefix 路径，仅允许一个已经真实 begin、完整选定原始 undo、未遇 end 的事务保留到已核源末端；complete=false 明确标记，窗口末端采用已核 header 的最后块/最大 offset 保守覆盖，不假造 commit。默认完整读取不变，仍须 finish/闭合。

原补偿归属、完整 XID、对象 generation、活跃 slot 冲突、begin 绑定、原始节点完整覆盖、物理 heap/index pair、index 槽序、未知页面操作、未绑定控制、补偿 active-head、selector 和来源复核仍执行。物理前缀分开返回已闭合事务和 pending graph；pending 含 identity checkpoint、segment、已消费位置计数、完整 pairs/操作/marker 与 undo checkpoint，不产生完整业务事件。

真实冻结73只读验证：已闭合0、pending1，XID10.14.2869/segment37749008/begin2743312，一个原始73:219164:16:3、heap pair1/index0/marker0。identity 与 graph 文本均和此前真实独立 JVM 验证过的 state 字节完全相同；graph SHA `217b8119b58651750750a1a5e8affd8049a01c73f0e23e8dfc677a38479ccd89`。默认完整事件入口同件73仍 INCOMPLETE_TRANSACTION_IDENTITIES:1、rc1/stdout0。原73+74前缀工厂返回闭合1/pending0，同 begin/end2743344；同候选完整事件工厂产出原两 UPDATE，stdout 与原完整候选逐字节相同。

六项单位验证覆盖：pending 窗口末端包含所有后续候选 marker/default 缺 end 拒绝；缺 begin与活跃 slot 冲突拒绝；完整两 XID/foreign 补偿路由；未知 marker与无 original 的相关补偿拒绝；活跃 index pair与另一已闭合 owner 分离；未知 index page 操作拒绝。index pending 属单位 fixture，不冒充真实跨窗 index 验收。

首完整测试 **326/1/0/0** 的失败原因是新 fixture 删除 end 时也删掉同记录补偿 marker，预期仍要求该 marker；首源码、完整测试原文/rc和报告保存于 r1。修正只保留 marker/删除 end，并补两 index 测试后最终通过；未放宽生产规则，已过真实输入轮次不重跑。

[45文件双SHA清单](evidence/DELIVERY-physical-prefix-20261007.json)封存 `lab/out/physical-prefix-20261007`，前86+36+42+88+182逐项未变。仅开发/有限验证：内部状态当前在内存中生成，尚未统一序列化、恢复并仅续后缀；没有 pending SYS 字典恢复、完整行持久恢复、跨发布产品/CPACK、主机断电或现场发布证明。原完整 goal active。

下一步将此前 dictionary receipt 与本内部 physical prefix 状态装成源绑定恢复凭证，恢复时从冻结 raw 前缀核对 typed pairs/操作与已闭合结果，恢复 identity/undo 后仅向其送入后缀，再由恢复图进入完整行/事件及指定私有产品。正式C++、一般矩阵、独立目标、评审/部署/现场/发布继续未完成；Oracle 全部目标后才查 Codex 卡顿。
