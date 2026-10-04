# W7 多member持久续接与真实collector崩溃

初版084cf37、最终collector/独立lab c689b7a。Java内核未改，沿用上一轮240测试制品，不重复构建。捕获恢复这一层推进，不代表消费者或目标ACK通过。

## 协议与边界

`follow_closed_members.py ... --resume <trusted-receipt-SHA256>`在同一目录取得独占flock，核对实际成员path/device/inode/bytes/source身份与已存绑定、外部传入的总回执SHA。之后逐件审计完整journal：帧边界/摘要、有序唯一块、metadata/source/sample、raw摘要/checksum/index/sequence、实际关闭NAB/SCN及online容量；整个既有链通过后才READY。

写入顺序：完整关闭范围核验 → journal flush/fsync → 逐代receipt原子持久化 → 总receipt原子持久化。恢复可接续总receipt之后连续的完整journal+逐代receipt，重新验证原始关闭范围和SCN链；缺逐代回执、半帧、孤立代际、回执不一致拒绝，保留原件不截断。旧已完整验证总ledger可补逐代回执，不靠源日志仍保留原代际来证明旧journal；原40已复用，保存字节仍可独立审计。

总回执SHA属于可信控制进程交接，逐代缓存属于该进程所有的本地存储；本方案不认证恶意管理员重新签发回执。不是多机分布式锁/外部签名协议。capture next_sequence只是已持久关闭代际边界，不是消费offset或目标ACK。

## 真实多member重启

专属Linux 19c源Oracle持续运行。原实际40—42目录从已停止collector重启，READY next_sequence43；一次专属实验archive log current关闭43，没有业务DML/DDL/配置修改。随后保存seq43的9163块，next_sequence44，完整终止rc0；旧40/41/42 journal逐字节不变。

seq43 first SCN2366759与既有42 next SCN一致。独立archive43的9161个数据块全部byte覆盖，缺块/差异0。此实验用了既有ARCHIVELOG及受控关闭，不证明自然日志切换延迟或NOARCHIVELOG。初版执行文件已冻结并核容器SHA；最终增加metadata数值类型/时间检查后，在以下真实43读取、恢复与既有40—43续接上验证。

## 两处真实SIGKILL

独立实验wrapper复用现有关闭seq43，读取真实member；只向`os.getpid()`发SIGKILL，不杀Oracle、LGWR或宿主机，没有新归档/DML。

1. journal已fsync、逐代receipt已原子持久化，在总receipt更新前SIGKILL：实际rc137；总ledger仍next43/captured空，完整43 journal及逐代receipt在。另一个进程用旧可信总receipt SHA恢复，核完整帧/关闭范围后next44。journal字节不重写，与独立archive43的9161数据块零差异。
2. journal第一帧只写100 bytes并flush/fsync后SIGKILL：实际rc137，无逐代receipt。另一个进程指定旧总receipt SHA恢复，CAPTURE_UNCOMMITTED_GENERATION rc1/stdout0；100-byte文件及总receipt前后完全相同。没有自动丢弃或伪造完整关闭范围。

SIGKILL后的锁可由新进程获得。结束后独立/proc核到LGWR pid5601/ora_lgwr_ork19仍存在；collector kill不当成源库重启。只证明实际进程死亡边界，不证明真实断电、内核崩溃、磁盘介质或初始目录父目录断电持久性。

## 反例

11项续接反例均READY之前rc1/stdout0：错误总receipt SHA、截断已收journal、错误成员inode、重新授权但next_sequence提前、错误SCN、逐代receipt与总ledger不符、逐代receipt缺journal、孤立45缺44、重新计算帧/ledger摘要而raw checksum坏、source thread写成JSON boolean、另一实际OS进程持同一writer锁。释放锁后最终代码只读续接原40—43目录rc0，总receipt与既有journal不变。重新授权副本只用于内层结构检查，不算签发者认证。

最终执行文件从实验冻结副本与工作树逐字节核对；[双次SHA清单](evidence/DELIVERY-closed-resume-20261005.json)关联执行脚本、三个实验目录、两处kill wrapper、命令/rc/完整stdout/stderr、原始输入、总/逐代回执及archive。原始大数据留lab/out，未上传。

## 接续

有限同源Linux regular-file、多member关闭代际重启/总ledger落后恢复通过；半帧保全拒绝，不是自动修复完整性或无限可用性证明。高压复用、实时未关闭record、Windows/ASM/RAC及跨批活跃事务状态仍待证。

下一步消费checkpoint、重放与目标commit/ACK分别持久化，复用已通过capture与完整行输入做真实提交前后故障验证，再W6/StreamHouse接入；现场与发布未通过，capture_complete/consumer_ack仍false。
