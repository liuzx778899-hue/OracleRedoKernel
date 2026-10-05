# W5 可信获取控制器接续执行包

背景：83cb49f的adapter已通过真实只读源获取，但产品绑定仍用手工批准冻结基线。本包把真实获取结果交接到集中端可信登记；不开放普通options或上传JSON自行授权。

1. 来源连接仅由集中端受控配置解析，绑定sourceId及预期原始source八字段；私有Source DataSource不注册为控制库/目标库全局DataSource。只读权限不足明确失败，不执行grant、补充日志或业务配置fallback。
2. 控制器调用实际Acquisition，先保存原始字节并核SHA，再将获取身份、SCN、scope、schema及authority关联写入获取账。状态初始ACQUIRED_ONLY，不能因SHA或文件存在直接变成可发布；客户端不能提供或更改签发authority。重启可信交接/文件篡改/错source/schema/失败半文件逐项实际验证。
3. 新snapshot必须配新capture范围：独立检查source、完整闭合链及原始header SCN覆盖。当前2480910/15/20晚于旧seq44—51，禁止把旧DML重放成新快照正例。需要新受控一次性源实验时，先封存执行包/唯一动作与失败不得重放规则；旧业务表及已闭合fixture不改作新实验。
4. 发布继续要求可信物理字典世代、精确index leaf/scope和完整事务/行证据；不从native dump或独立SQL真值生成事件、不猜leaf、不把当前metadata当历史字典。历史DDL/ABA、未知世代或跨起点活跃状态未证明时，不进入生产APPROVED。可以实现/验证受控有界登记，但明确保持公开decoder和生产门禁关闭。
5. 实际受控登记再接批准raw→内核→完整事件→独立目标commit→控制checkpoint→ACK。此前源权限、acquisition、capture、target与control SHA/rc/事件数都关联；空结果也要coverage和commit证明。仅spec/结构校验/拒绝或最终行相等不能代替完整DML。

可证伪判据：实际源获取回执不能被相邻文件/普通options替代；错source/SCN范围/对象/物理列/leaf/世代拒绝；获取/存储/登记任一失败无可发布输入；注册后重启仍核原始字节与authority；真实DML中间像、顺序和rollback零事件独立一致。独立人员评审仍PENDING，不将作者自检记作独立评审，不合并/部署共享服务。

其它未验收主线保留：消费者租约/并发、活跃跨窗、C++实际持续采集、独立目标实例、其他控制方言完整HTTP、Windows/ASM/高压/断电、现场及发布。禁止Luna/子代理，长期goal active。
