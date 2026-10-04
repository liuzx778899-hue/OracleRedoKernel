# W7 固定member实时原始块观察journal实验

从“CURRENT文件能打开”推进到实际运行中原始块观察及文件刷盘。新增Linux 19.3 lab Python observer、协调动作脚本和独立journal/归档对账器；内核Java及既有生产制品不变。这里的journal是有界观察记录，不是完成的ldfcap等价实现或可发布日志流。

源码封存7669d7c，完整Git archive树；[证据清单](evidence/DELIVERY-observation-20261004.json)。Java内核沿用076865c制品，SHA256 `2dcd626c84e1bc001d8a97e00475d88730605ed732a93610d351864a8d3135df`。没有重建Java或重跑此前124测试；本轮验证的是observer、协调窗口、journal拒绝和有限字节覆盖。

## 实现与成功窗口

`observe_online_redo.py`只读一个既有member的前32768块（16MiB），每次两遍读取。记录源路径/inode/device/文件长度及redo源身份、sequence、块号、原始512字节、状态、时间和摘要。相同身份/位置/内容/状态去重；两遍不等、generation变化、checksum或块号错误单独标记。每帧有metadata/payload长度及SHA256，每批flush+fsync后报告观察数。未知/不完整frame由独立读取器拒绝。记录观察不是stable/complete record证书；没有生成解析cursor、ACK或CDC。

协调脚本确认首批观察已输出且Popen仍非终态后，才执行一次性新fixture。专属19.3 MIN/ALL均NO，既有ARCHIVELOG；固定`redo02.log`从seq17 CURRENT开始。新表`T_CAPTURE_R3_20261004`插入40行900字节，每行commit及0.1秒间隔，再执行实验切换归档，CURRENT变seq18。未开启配置、重启实例或重放第一轮表。

| 项目 | 结果 | 处置 |
|---|---|---|
| 协调窗口 | observer在fixture前后均有活进程；journal还保留动作host-time区间内观察及sample编号 | PASS实验时间交叠；容器/宿主钟未独立认证 |
| journal | 432观察帧，40轮、一个generation，样本全部OBSERVED_STABLE | PASS此观察；未实测半写/generation异常 |
| source SQL | 40行、36,000字节；seq17→18 | PASS专属受控源动作 |
| 独立归档对账 | 429个归档数据块均在journal中有完全相同原始字节，missing/different均0 | PASS有限归档数据块覆盖 |
| 归档头 | 明确不比较/不归一成相等；在线原始头保留 | 不构造伪造的closed archive |
| 六项真实journal派生拒绝 | 截断1字节、错magic、错frame摘要、漏最后数据块、负块号、改payload后重算摘要但checksum错误 | 全部rc1；缺块有明确JSON，其他stdout空 |
| 错过DML窗口反例 | 第一轮journal相对随后seq16归档missing284 | rc1，保持失败 |
| 内核边界 | 同一既有jar scan seq16/17 rc0；events seq17 rc1/stdout0 | 未接入journal解析或CDC |

## 保留失败

首次collector用了Python3.7才有的time_ns，而容器既有Python为3.6.8，AttributeError及空/未完成目录保留。改为显式秒级Unix时间，不伪称纳秒精度。

第二轮60次采样在第一次表`T_CAPTURE_20261004`的DML之前已结束，实验编排错误。该表40次commit已经成功，不能重跑；保留journal和seq16，并用其284缺块验证覆盖失败。第三轮使用独立新表，协调脚本检查实际存活句柄，而不凭“已启动”或状态文件猜正在采样。

首次封存验证因Windows工作副本CRLF与Git archive LF导致源码SHA不同而断言失败。之后分别保留sealed/executed SHA、CRLF计数，只对源码做换行归一内容核对；原始redo/journal字节不改变。执行脚本从容器导出绑定证据，失败stdout/stderr均保留。

## 未验收范围及接续

`capture_complete=false`始终保留。两次相等读取和checksum只能说明观察状态，不能证明一致快照或源日志完成。固定member/16MiB不会自动跟随下一日志组；此次范围足以覆盖归档不证明长期不会落后。存下曾匹配归档的块版本也不证明捕获了每个中间版本。

fsync为每批文件刷盘调用，不证明目录持久性、断电语义、崩溃恢复或checkpoint/ACK闭环。没有restart/resume、自动捕获缺口/复用恢复、完整record游标、NOARCHIVELOG、Windows/ASM、生产部署或StreamHouse接入支持。下一步按[W7执行包](DISPATCH_W7_ONLINE_CAPTURE_20261004.md)补日志组跟随与generation/reuse门禁、独立异常观察、崩溃续接及原始capture到解析的可验证边界，再做ACK集成。W4 index/恢复与W5仍需继续，不能因字节覆盖而开放事件。
