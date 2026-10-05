# W5 媒体标记与事务结束窄形态候选

## 结论
仅冻结19.3 seq59/60的有限历史结构反例通过：真实V2原始raw及擦除全部schema-family的DERIVED均命中同一SYS目标写入、rc1/stdout0；去锚点DERIVED诊断对照rc0/2个三列UPDATE，其中ADD后缺EXTRA=NULL。不是完整历史字典或产品HTTP/现场发布通过。

## 实现与边界
- 24.4 native为MEDIA RECOVERY MARKER。只识别单向量、validity5、零位置/SCN头、固定16字节字段与已观测低32位SCN范围布局；0x8000位于字段字节4/12，字节6/14为0。范围差0/1且末端早于record SCN。错头/范围/伴随向量拒绝，其他24.x仍拒绝；不使用SQL文本判断。
- flags0x12的两种empty extension（native ext0/1）只保留原slot/sequence/segment/undo tail。扩展首字与尾字段ext一致、时间字段与footer一致、其他保留位为0；不解释额外控制语义。既有sized extension和rollback形态保持，错扩展/flags/tail关联拒绝。
- 无新增运行时依赖、无目标过滤删证据；旧公开decoder继续关闭。完整控制图、一般历史权限和版本支持不能由两个形态推导。

## 核验
- kernel444630a440379742fba3b0586b08413d68251bddb727d1751c40eaaab79899c5，269项0失败/错误/跳过。源码验证发生在提交前工作树；r1执行前pin与r2修正后pin时点分别保留。
- 原始V2及DERIVED擦除两批：TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2509458:object=74862，0事件/0stdout。与上轮相比，原始raw不再被24.4抢先拒绝。
- 去锚点DERIVED V1：2事件；两笔XID/位置、既有三列/提交SQL一致，ADD后实际4列却输出3列。独立native完整3图、4original/0compensated/4live零差异；两条结束record的slot/sequence/status/flags/UBA/ext与native零差异。NUMBER零取原可信获取种子，不伪称新DUMP证明。
- 三个checksum有效派生目标尾/slot/容器变异均结构通过，语义rc1/stdout0（tail mismatch、incomplete identity、before mismatch）。最初变异外来结束记录的tail仍rc0、两事件像字节不变；这是外来无关对照，不能记目标tail拒绝。
- 同candidate旧mixed/empty受影响输出逐字节不变，不重放源DML。

## 失败与保全
kernel r1媒体range标志offset错误；replay r1使用Match切片导致比较器失败；negative r1误选foreign结束、r2假定短record头导致越界。源码/测试报告/原文保全，修复后只续未完成检查。负例r3执行源副本保存后仅删除当前脚本unused assignment，不冒充重新执行；物理helper/比较器源码在执行后封存，和其他执行前pin区分。

[292文件双SHA清单](evidence/DELIVERY-dictionary-end-profiles-20261006.json)。原交付236个文件逐一SHA仍匹配；本轮0源/目标动作，旧seq59/60事务不重放。制品不是新adapter或产品候选，不挪用旧21/735为新产品验证。

[接续获取账与产品V2](DISPATCH_W5_V2_CONTROL_RUNTIME_20261006.md)。P0整体未闭合，独立评审PENDING，部署/现场/发布未通过，goal active。
