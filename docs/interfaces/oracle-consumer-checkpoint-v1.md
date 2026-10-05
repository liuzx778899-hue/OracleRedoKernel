# 固定范围 Oracle 消费 checkpoint v1

OracleConsumerCheckpoint.consume(directory, target, Inputs, currentBatchSha, Connections) 属独立 Java17 adapter；Inputs 在持有文件消费锁之后读取可信配置对应的全部保留窗口，必须返回已验证 CapturedHeapBatch（固定source/schema/baseline/leaf，同一capture锚点）。Connections 每次返回全新autoCommit=true专属目标连接，由目标 writer拥有关闭。目录必须事先存在；Windows目录force、其它文件系统及源信任授权未验收，当前实际验证Linux。

全生命周期 consumer.lock 跨读取旧checkpoint/temp、读取capture并解析、target核验和消费状态落盘；第二消费者在读输入或连接target前拒绝。目录是受信控制边界，摘要不认证管理员或签发者。

checkpoint保存目标owner/prefix/targetId、已提交批列表、固定范围source/schema/baseline/SCN/leaf、每capture序号/摘要、事件数、关闭sequence/blocks/endExclusiveScn及最大覆盖批FRONTIER，尾部SHA256保护内容一致性。所有字段从已验证输入重建逐字节比对；不可用文件声明新SHA绕过。当前位置仅固定scope闭合日志上界，不是Oracle commit SCN、全库消费点或删除许可。

恢复重新核全部注册窗口的实际Oracle source身份、事件内容/成员/提交凭证。已checkpoint/ACK/完整temp发布的batch必须已有目标commit；缺失即拒绝，不通过重放DML掩盖。仅未发布且尚未commit的当前批允许首次执行DML。目录unknown ACK或未知注册输入拒绝。registered target commit即使local checkpoint丢失或落后仍可恢复；缺原capture/baseline拒绝。新批先actual target commit，再写checkpoint、再写独立每批ACK。

固定范围窗口关系保证前缀重放不回退；同range不同观察摘要不虚构位置前进。所有完整已提交注册批重新验证后才计算最大前缀覆盖。未知起点、range缺口及scope漂移拒绝。

临时文件CREATE_NEW、force、原子replace、目录force。已有完整checkpoint.tmp或ACK.tmp先严格解码/对输入和target核验，成功后原子改名retained-UUID保留；半写、错误checksum、错误target/范围/成员等拒绝且原文件保留。此恢复不代表断电认证。新DML、各落盘边界真实SIGKILL仍需独立实验。

当前实际证据：目标MIXD既有完整两批commit恢复、前沿42不回退、完整temp恢复保留、旧前缀checkpoint从注册目标commit追回42、四文件反例拒绝保全。完整产品pipeline、生产trusted dictionary、任意活跃窗口、实时capture、raw retention、部署/现场/发布仍未通过。
