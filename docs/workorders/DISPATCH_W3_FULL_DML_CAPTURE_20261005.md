# W3 完整 INSERT/DELETE capture 执行包（未执行/未验收）

恢复门禁已有有限闭合窗口证据，回到用户“解析优先”主线。kernel ArchivedHeapRows 已有 INSERT/DELETE分支，但近期raw capture→SPI→Oracle target只实际验证UPDATE；分支存在和结构解析不能当DML通过。优先验证真实完整行与事务语义，再接已有消费协议，不先解开public decoder。

## 范围与源实验

仅专属oraredo-19c-lab-20261004，另建项目fixture，保留T_BASELINE_20261004及旧原始输入/基线/控制表。NUMBER主键、VARCHAR2、可NULL隐藏NOTE；最小种子保证heap/index已分配。先复用只读基线获取/回执导入，记录完整source、SCN、logical/data-object、列/hidden/nullability、物理rowKey、获取SHA；leaf必须来源于明确元数据或自主raw index发现，dump/SQL truth仅独立验证，不当生产事件输入。

新fixture受控DDL/DML不证明生产零数据库配置；保留源补充日志NO/NO，不开配置。每笔事务只执行一次，已提交的失败实验不重放。只补读取/解析/比较及尚未执行动作。

## 首轮真实案例与判据

1. 基线后INSERT新键，commit：1事件，before为空，after三列完整，源SQL/native完整XID/位置对齐；hidden/NULL必须保持，与目标实际INSERT一次及重放0新增对应。
2. 上笔已插入行DELETE，commit：1事件，before完整，after为空；目标删除与应用/成员/commit同事务，不能因最终行回到基线而输出0事件。
3. 同事务INSERT→UPDATE→DELETE，commit：业务顺序和完整中间像按真实证据判定；不把最终行相等当“无变化”，不泛化D/I合成UPDATE。若只能支持限定事件语义，明确限制/拒绝。
4. INSERT后rollback、DELETE后rollback：原始forward/undo补偿节点和完整图独立native核验；无提交DML输出、baseline/cache回到原状态；空提交凭证/消费位置独立验证。

逐事务保存baseline/checkpoint边界、raw journal/归档、独立完整dump与SQL各时点、rc/stdout/stderr、XID/对象世代/rowKey/操作计数。首先检查跨事务/record/vector/undo pairing/完整行；不能绕过未知flags/rowpiece/复用slot/页维护拒绝。一个事务窗口失败时整批stdout0，旧已验收样本不回退。

## 修复与交付

先冻结真实失败正样本，确定字段来自原始字节+独立dump，最小修改kernel及必要scalar/extent，新增有意义反例：不完整INSERT像、错DELETE before、槽位复用/对象世代冲突、补偿归属错误、晚事务失败无部分输出。保持Java17/kernel无JDBC/Flink/Spring；不复制AGPL源码。

新制品针对性测试后，对同批raw完整native/SQL中间像零差异，再actual SPI projection及专属新target消费、重复0新增；不重跑其它已关闭轮次。保存输入/制品/输出双次SHA、执行源码/commit、失败原文与明确接受范围，更新STATUS六列。没有证据的同key并发/多页/复杂类型/历史DDL继续拒绝。完整产品Oracle窗口/position SPI、生产字典可信授权、实时agent/现场/发布保留独立后续门禁。
