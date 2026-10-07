# D2 正常索引恢复与跨窗多 savepoint 开发反馈

新专属场景只执行一次：主键0→10008和TXT变更保持未提交，跨77/78代际；两次savepoint分别撤销TXT更新，保留NOTE更新，再恢复原主键/TXT/NOTE并提交。XID10.6.3116。新真实历史HTTP获取101369014298759168、SCN2800585、V3 SHA e4bd8aefb7bd0df6fb041f3d8931b64abfcc641f748cc748d70e2581a08ca1cf；独立SYS6行230cells、3index和业务1row零差异。原始capture与归档逐块比较通过，native dump保留，源NO/NO、旧表不变、实验行恢复。

原候选在完整77/78输入rc1/零事件：UNSUPPORTED_INDEX_ROLLBACK_LAYOUT。真实78:7:328正常10.5包含KTB、6字节leaf及6字节keydata，同记录5.1 undo为mark-deleted（leaf operation4），独立Oracle dump明确restore leaf row。此前该输入被误送补偿分支。本次区分三field正常10.5和两field补偿10.5，正常恢复按UBA精确绑定原undo、block/ITL/XID、keydata及row size；字典既有专属路径保持原门禁。keydata/size/缺undo反例均拒绝，正常restore仍拒绝作为compensation输入。

相关26测试及内核完整332测试全通过（零失败/错误/跳过）。完整输入3个生效UPDATE，顺序与完整图为原行→新主键/TXT→保留NOTE→原行；两个取消TXT更新不存在。真实前缀checkpoint进程halt23，新JVM恢复完整输出逐字节一致。首次验证误省略必需raw前缀导致HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED，rc1/stdout0保留；修正调用而未放宽恢复门禁。正式提交树ea05f06构建rc0、kernel SHA256 71d38849703508c49ac8f37c3b521cc769616d22ee851c1f3ac3e1f8d6cb7992，两次正式制品全量/恢复实际输出与开发验证逐字节一致，未重复测试；[制品安全摘要](evidence/DELIVERY-d2-index-original-restore-20261008.json)。

证据本地目录d2-index-savepoint-20261008，未封存；不得重跑source-actions-started标记后的capture脚本或新获取baseline。最初只读快照挂载拒绝、V2获取误用导致独立V3比较失败、路径准备与shell生成错误均保留；正式baseline-reference是V3，首V2另存baseline-v2-original。自有两个helper已停143。无产品或目标部署，本场景target/CPACK未验证；独立native完整操作关联与新增恢复状态毒化仍待补，不能将有限开发通过扩大成D2全部支持。下一复用冻结输入完成这些核验，再推进D2其他缺口/D3正式C++采集；D4/D5、独立目标/独立评审/现场验收/发布保留。
