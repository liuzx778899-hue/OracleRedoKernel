# D2 正常索引恢复与跨窗多 savepoint 开发反馈

新专属场景只执行一次：主键0→10008和TXT变更保持未提交，跨77/78代际；两次savepoint分别撤销TXT更新，保留NOTE更新，再恢复原主键/TXT/NOTE并提交。XID10.6.3116。新真实历史HTTP获取101369014298759168、SCN2800585、V3 SHA e4bd8aefb7bd0df6fb041f3d8931b64abfcc641f748cc748d70e2581a08ca1cf；独立SYS6行230cells、3index和业务1row零差异。原始capture与归档逐块比较通过，native dump保留，源NO/NO、旧表不变、实验行恢复。

原候选在完整77/78输入rc1/零事件：UNSUPPORTED_INDEX_ROLLBACK_LAYOUT。真实78:7:328正常10.5包含KTB、6字节leaf及6字节keydata，同记录5.1 undo为mark-deleted（leaf operation4），独立Oracle dump明确restore leaf row。此前该输入被误送补偿分支。本次区分三field正常10.5和两field补偿10.5，正常恢复按UBA精确绑定原undo、block/ITL/XID、keydata及row size；字典既有专属路径保持原门禁。keydata/size/缺undo反例均拒绝，正常restore仍拒绝作为compensation输入。

相关26测试及内核完整332测试全通过（零失败/错误/跳过）。完整输入3个生效UPDATE，顺序与完整图为原行→新主键/TXT→保留NOTE→原行；两个取消TXT更新不存在。真实前缀checkpoint进程halt23，新JVM恢复完整输出逐字节一致。首次验证误省略必需raw前缀导致HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED，rc1/stdout0保留；修正调用而未放宽恢复门禁。正式提交树ea05f06构建rc0、kernel SHA256 71d38849703508c49ac8f37c3b521cc769616d22ee851c1f3ac3e1f8d6cb7992，两次正式制品全量/恢复实际输出与开发验证逐字节一致，未重复测试；[制品安全摘要](evidence/DELIVERY-d2-index-original-restore-20261008.json)。

证据本地目录d2-index-savepoint-20261008已320项双SHA封存；不得追加、重跑source-actions-started标记后的capture脚本或新获取baseline。独立native仅按可信baseline SCN过滤，未参考Java位置/事件；自主发现1事务9original/2compensated/7live，与完整物理图逐项零差异。错误checkpoint摘要与删除pending owner后重算摘要，正式jar均rc1/stdout0；恢复反例2项通过。旧committed-proof的native PENDING是该追加核验前时点，最新native-poison-proof和封存清单证明本有限物理图已过，不扩张到通用逻辑支持。

最初只读快照挂载拒绝、V2获取误用导致独立V3比较失败、路径准备与shell生成错误保留在当轮记录；正式baseline-reference是V3，首V2另存baseline-v2-original。自有两个helper已停143。无产品或目标部署，本场景target/CPACK未验证；独立评审[PR1](https://github.com/liuzx778899-hue/OracleRedoKernel/pull/1)保持draft。下一在新目录升级私有产品候选，只读复用封存原始输入与原获取，实际验证主键变更→目标原子提交/CPACK，再推进D2其他缺口/D3正式C++采集；D4/D5、独立目标/独立评审/现场验收/发布保留。

产品后续追加（2026-10-08）：以上“未部署/CPACK未验证”保留为内核交付时点。私有产品b815f23完整779通过，product5ae245dd嵌入正式kernel71d38849核等；新专属任务101423259819597824真实WAITING_INPUT→3个顺序完整UPDATE→原子目标3/3/1→PUBLISHED/DONE→两窗CONSUMED，CPACK同字节SHAc8c584d07f5bd05fe505cadb8727a9e2f5af2492e1a30c5785f0c03f8a46460a，重复原任务实账稳定，341双SHA封存/helper停止143。未重跑源事务/获取/旧D1轮次；同实例跨schema不算独立目标，生产/现场/发布与独立评审仍待证。最新产品反馈与六列状态以唯一P的REPORT_ORACLE_D2_INDEX_RESTORE_INTEGRATION_20261008.md/STATUS为准；root STATUS恢复并保留移交快照，不作为产品当前入口。
