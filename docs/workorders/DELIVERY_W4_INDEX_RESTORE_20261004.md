# W4 index RESTORE 与主键回滚混合图交付

新增普通5.6 / metadata10.22 / 10.5 RESTORE绑定；同时补实际阻挡整事务图的heap KTB0x12 cleanout-C DELETE入口。最终完整提交树 `5002f181b04e3a64ff6a92c69808dafff3a60f9a`，Java17独立构建jar SHA256 `7421e383e62da6d9eef65af94f6c8cc218861b0f949f93781f9f685d045602b9`。本次仅使用既有冻结输入，没有源端动作或配置变更；CDC仍拒绝。

## 实现与边界

- IndexCompensation将10.3 PURGE和10.5 RESTORE解码为有类型的LeafRollback；仍保留严格的旧decodePurge接口。普通5.6匹配同record/CON、唯一原始undo、metadata、block/ITL/slot/rowSize及活动完整XID。RESTORE必须对应原始DELETE及RESTORE undo，不能拿INSERT/PURGE替代；拒绝锁定生命周期。
- 两类物理rollback向量均不含key/keydata；RESTORE关联依据是原始undo和metadata/位置约束，不伪造当前forward键值。历史KTB C/L不作为当前回滚身份。
- 新遇到的heap0x12只支持真实观察的70字节KTB、版本0x0d、11.3/20字节DRP/XA。当前UBA从C前缀offset8取得，XID保持缺失并由匹配原始undo核对；cleanout尾部保持opaque，不认证其语义。其他长度/版本/操作/模式拒绝。
- MixedUndoProbe增加固定restore fixture模式（heap73059/index73060/2列/seq18），仍由owned inventory核对原始和活动窗口候选覆盖、源SHA与有序head。fixture选择不替代生产SCN字典或通用来源配置。

## 最终封存验证

完整Git archive 5002f18独立构建：**147项测试，0失败/错误/跳过**，新增6项RESTORE和3项cleanout-C测试。测试覆盖真实字节正绑定、原始UBA与历史C引用不同、操作互逆、slot/size/mode/KTB、重复候选/物理向量、活动身份、拒绝锁定及cleanout-C错误长度/版本/操作/UBA。

| 真实新事务 | 原始heap+index | 取消 | 保留 | native |
|---|---:|---:|---:|---|
| 10.17.1215 / savepoint | 8 | 7 | 1 | NODE/CANCEL/LIVE有序序列零差异，保留heap TXT=`keep`更新 |
| 10.14.1214 / full rollback | 5 | 5 | 0 | 零差异，heap+index全部撤销 |
| 合计 | **13** | **12** | **1** | 4 RESTORE + 3 PURGE索引取消；5 heap取消 |

新样本35个index相关位置/直接字段同件jar再核对，native零差异。9对新heap正向/逆向identity及逐列VALUE/NULL/ABSENT、字节零差异（包含基线3对；不是9个逻辑事件）。独立native图比较器按完整原始XID、begin UBA→segment、metadata、分配/取消head和结束segment/slot/sequence重建，不读取Java候选序列决定预期。

两笔新事务分别派生：省略向history提交index entry、错误index leaf slot、错误leaf模式、重复提交同一index补偿，共 **8项rc1/stdout0**。raw文件不变；前者与重复取消由owned输入顺序/有序head门禁拒绝，slot/mode由index matcher拒绝。它们是record/选择/API层派生，不是原始归档损坏或重启恢复实验。

同件jar回归：

- 旧三笔混合图9/7/2及6项派生拒绝；新旧五笔合计22/19/3，范围仍有限。
- 旧14index pair/native零差异及12record拒绝；18heap pair/native零差异、4补偿/1保留与旧封存行文本相同（平台换行不作字节等价，分别保存输出SHA）。
- 六批长heap事务 **756原始/430补偿/326保留** 及native inverse/retained字节、已具备SQL的最终行全部零差异；不是新的数据库执行。
- events seq18仍rc1、stdout0，原因 `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。

## 失败保全

首次仅加RESTORE后144项测试通过、savepoint图通过；full图在同record另一条heap DELETE处失败：`UNSUPPORTED_KTB_OPERATION`。native明确op0x12 C并带cleanout，raw字段显示长度70、UBA0x02402d7a.00f2.32。保留失败stdout/stderr/rc、原始字段、研发jar（SHA `3bd37eeab5c7769a6cd9f5e9ab2c03793cede1ea97d3ab56b2d7fefa158becb2`）及工作副本diff。第一轮没有作为完整提交树封存，不把最终5002f18 SHA套到它的失败结果。

补有限真实C入口后两图通过；最终树重新构建及上述完整封存回归通过。没有重放原fixture或绕过不认识的heap向量。

本地研发 `lab/out/index-restore-impl-20261004/`；最终封存 `lab/out/release-index-restore/`。[交付清单](evidence/DELIVERY-index-restore-20261004.json)绑定输入、完整源码树、jar、命令、测试、正反输出、失败原文和SHA256。

## 未关闭门禁与下一步

支持仍限定普通5.6、单叶已观察布局；index begin/cross5.11、slot迁移/页split/维护、并发归属和恢复未通过。字典/基线没有建立，DELETE forward缺键、PURGE undo缺keydata等信息边界仍存在；没有开放事件。

下一步补索引slot变化/页操作反例及活动窗口内其他控制的完整覆盖，再进入W5 SCN字典与基线闭环，使完整行和逻辑DML有来源绑定及可证伪证据。持续capture/续接/ACK、StreamHouse接入、部署/现场验收/发布均未完成。有限结构、配对、图和拒绝证据不充当完整DML或现场GO。
