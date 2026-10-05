# W7 消费 checkpoint 执行包（未实现/未验收）

目标：把已验证原始 capture、完整事务/行窗口与目标提交凭证关联到可恢复消费位置。前提是 W6 原始闭合窗口跨批两旧一新通过，且真实目标成员/内容/提交计数/来源身份反例完成并恢复。结构解析、最终行相等或目标凭证计数不能替代这些前提。本包不授权删除 capture，不解除公开 decoder。

## 实现范围与位置含义

Java17 独立 Oracle adapter 内实现，kernel 不加 JDBC；保持现有模块边界。消费者使用实际 CapturedHeapBatch 原始输入及可信 baseline，从不可变 factory 输出提取 source/schema/对象世代/leaf/baseline、各 capture 摘要与闭合 sequence/block范围、起止 SCN。通过类型化接口传递，不伪造 SQL Server LSN/GUID/commitAt。

三位置分开保存：capture 已持久化的输入摘要与闭合代际；固定 source/schema/对象/leaf/baseline scope 的完整解析窗口；目标实际提交的批 identity、全部 event内容/有序members/commitReceipt。有限 source.nextScn 是关闭日志范围上界，不是 Oracle commit SCN，不宣称全库已消费。

首次实现只支持现有闭合 fixed-schema scope；任意活跃事务跨窗/历史字典仍是独立门禁。所有输入从同一可信控制配置读取。摘要保护内容而非认证来源；不可将调用者自报新 SHA 当生产可信授权。

## 恢复不变量

1. 生命周期独占锁覆盖读取旧 checkpoint、核源、解析、目标验证、写 ACK/checkpoint，第二消费者在任何消费状态修改前拒绝。目标 IDENT 独占不能替代文件 checkpoint 全生命周期锁。
2. 新 checkpoint 必须由完整原始窗口及实际目标验证产生。目标 commit 完成之前不推进；已有 ACK/checkpoint 而对应 target receipt缺失/成员缺失/内容错误/来源冲突时，requireExistingCommit=true，拒绝且保留所有状态，不能重放 DML掩盖。
3. 旧窗口凭证恢复核验必须使用其绑定输入与 baseline，不由新窗口推断。缺旧输入时拒绝，不执行清理或从当前源行倒推历史。
4. checkpoint 保留 source、target、scope、batch、输入摘要、完整已验证窗口/目标凭证链。序列/SCN移动按同 incarnation/同 thread、范围连续性核验；前缀40—41后完整40—42可前进，完整批后重放前缀不回退；同redo不同观察摘要只能新增批凭证，不虚构前沿前进。
5. 用临时文件 force、原子替换、目录 force；旧文件与半写临时文件均保留。读回并重新验证，不以 rc0或文件存在当通过。断电证明独立待证。
6. ACK 与 checkpoint 各有身份：任何一个先落盘时重启均能从实际 target receipt恢复另一项；文件超前目标必须拒绝。捕获者 nextSequence 不改，raw retention无删除动作。

## 有界实际验收顺序

先新模块针对性测试：错误source/schema/target/baseline、缺输入、缺成员、凭证冲突、序号回退、范围缺口、第二消费者、坏checkpoint/半写temp；断言零成功输出、文件原字节保留及目标无新增DML。测试必须覆盖恢复状态，不能只镜像序列化实现。

再专属 target 新namespace执行原始40—41→40—42，两批2+1新增、重复0；checkpoint对独立journal/native范围、目标SQL及ACK逐项零差异。用真实消费者SIGKILL覆盖目标提交前、目标提交后checkpoint前、checkpoint临时force后原子替换前、checkpoint替换后ACK前；每次独立SQL/文件/进程证据确认后，仅续跑未验收状态。上一轮target三强杀不重跑。

保存完整 command/rc/stdout/stderr，输入/制品/输出双次SHA、执行源码字节与Git树关系；所有口令排除上传。最后独立对账并更新 STATUS六列。仍不等于 StreamHouse全产品pipeline、生产可信字典、实时高压、Windows/ASM、独立目标实例、部署、现场或发布。
