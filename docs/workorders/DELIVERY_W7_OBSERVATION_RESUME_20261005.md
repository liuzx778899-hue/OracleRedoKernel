# W7 固定member观察journal持久续接

代码00358ce。生产Java内核未改，不重复已通过的233项测试。本轮只在专属Linux容器只读采集；没有DML、DDL、切日志、配置变更或重启。

## 已交付与实测

`lab/resume_online_observation.py`保留ORACAP01原始观察帧，新增源路径/device/inode/文件大小/六字段代际/bounded_blocks绑定、完整帧恢复审计及独占writer flock。每轮journal先flush/fsync，再写receipt临时文件、fsync、原子replace及目录fsync；receipt包含帧边界字节数、前缀SHA256与观察数。恢复核对落盘前缀、全量帧摘要/真实稳定块校验和/块身份、重复与顺序、回执边界及源绑定。完整尾帧领先于回执可以重新验证并续接；残缺尾帧保留且拒绝，不自动截断或覆盖失败输入。

实际CURRENT seq40，redo01.log，DBID1276726821/thread1/resetlogs1245693159；源文件209715712 bytes，只观察前4096块。首次两轮与另一进程两轮续接均rc0，4096帧/3508026 bytes，全部OBSERVED_STABLE，续接原前缀保留。journal SHA256 `d46909048c6814b1e0cc76b93ea4b3daeb252862af702d59586f515e7121b48b`。没有新块版本时去重；sample表示实际存储观察编号，不能当作采集进程心跳或消费进度。

副本回执退至合法空前缀，模拟完整journal落盘而回执未发布；另一采集进程审计已有4096帧并成功续接，原字节保留。独立OS进程持有同一锁时，第二采集器BlockingIOError拒绝/stdout0；锁释放后最终源码续接及审计rc0，journal前缀不变。

13个反例均rc1/stdout0：末字节截断、丢完整末帧、摘要错误、追加重复帧、追加残缺帧、回执计数错误、回执落在帧内、错误inode、错误代际、重封envelope及receipt后真实块校验和错误、重封负块号、缺receipt、双writer。只说明指定拒绝，不泛化为所有故障可靠性。

完整证据在`lab/out/online-resume-20261005`及`lab/out/online-resume-faults-20261005`，[双次SHA清单](evidence/DELIVERY-observation-resume-20261005.json)绑定源码、journal、回执、每条命令/rc/stdout/stderr。首次complete-tail副本从Windows拷入容器后属root，无法打开writer.lock，PermissionError rc1；原文保留。只修实验副本属主并通过明确continuation跳过已通过首次采集/续接；原失败目录未覆盖。

## 尚未验收与接续

这是Linux普通文件、同member同代际、有界前缀观察续接。receipt是本机控制文件的完整性边界，不认证外部签发者；未证明真实断电、介质持久性、无限journal效率或恶意回执重签。代际变化保守拒绝，尚不能跟踪组轮转或补缺口。两次相等读只称OBSERVED_STABLE，不证明LGWR完成或record完整；没有归档全覆盖新证据、完整record游标、消费者ACK、目标提交、Windows/ASM支持或源端持续零侵入全流程验收。CDC事件0，capture_complete及consumer_ack均false。

下一步实现普通文件多member代际跟踪，用一次新的专属受控跨组实验核对原始字节覆盖与缺代际拒绝，再接record重组/恢复边界。之后才对接W6与StreamHouse ACK；不能把本轮恢复审计当成端到端通过。
