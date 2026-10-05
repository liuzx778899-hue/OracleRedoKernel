# W6 实际目标commit后核验失败窗口 · 2026-10-05

## 已完成

复用上一轮16测试adapter `8de94aae373dba947624e5b34413fc16bfcd23e2480e48a8e47686dc32d76704`、kernel `96095420…`、SPI `fb9c443b…`，本轮不重跑已接受的构建、源DML或强杀轮次。

在专属Oracle实验目标schema新建PCNON/PCNUL/PCMIX/PCEMP各五表，仅承接冻结seq44—50三非空组及seq51独立空组。实际首次业务应用2/2/3/0，APPLIED/MEMBERS/COMMITS状态分别2/2/1、2/2/1、3/3/1、0/0/1；混合三事件最终恢复seed。

每组第三个独立连接（前两次分别为初始凭证查询与实际writer事务）在post-commit核验executeQuery注入SQLException，rc1/stdout0。实际目标已commit，但checkpoint/checkpoint.tmp/ACK均不存在。writer及生产制品未插桩；故障代理仅在lab worker，代表查询不可用实验，不能当物理断电或现场网络断裂。

恢复核验后四组新增业务0、生成完整checkpoint/ACK，重复仍0且文件原字节不变。恢复连接代理禁止commit/rollback/setAutoCommit/createStatement；全目标快照commit未ACK时与最终完全相同，未补写已有业务。空batch也经历实际commit再核验失败，不豁免凭证。

## 独立验证与保全

独立Python比较器按先前冻结raw/native已对账输出核验2/2/3/0事件的全部中间像、操作、有序成员、target/source/schema/batch及checkpoint全部scope/capture位置字段与ACK，四目标零差异；漏成员、错操作、重算checksum后的checkpoint错source三反例均检出。独立程序对账不等于独立人员评审。

首轮比较器空批位置切片误用8:11，rc1；失败脚本及stdout/stderr/rc保留，改为7:10后只补只读比较，不重复目标提交。源业务/原旧表/无补充日志NO/NO的前后只读快照一致；源业务DML0、配置不变、不重启。新实验目标DDL/seed属于受控fixture，不当生产零侵入证明；同实例跨schema不是独立目标实例。helper已退出。

[安全摘要](evidence/DELIVERY-oracle-postcommit-proof-20261005.json)关联ignored本地封存，两次SHA核对；凭据、raw输入、制品不上传。

## 六列与接续

代码复用通过｜针对性验证通过（四有限范围）｜集成有限目标commit→独立核验→checkpoint/ACK通过｜部署未开始｜现场待证｜发布未开始。独立评审PENDING。

该窗口现有实证，不再重复。接续产品Oracle专属目标端口与Facade次序：可信登记验证→目标commit/只读证明→控制库原子账本/窗口消费/任务checkpoint→ACK；控制库失败恢复、跨进程并发和独立目标实例仍需实测。公开decoder及产品Oracle gate继续拒绝；历史字典、跨窗活跃状态、C++真实采集与现场发布门禁继续，goal active。
