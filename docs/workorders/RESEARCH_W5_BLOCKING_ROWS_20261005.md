# W5 真实同一行锁等待样本：解析未通过

源脚本57bb80f，修正Oracle保留字后b7a49ac；生产运行时仍55c331a，jar SHA256 cf339689693b34acd252652dc16dd39c3545844eaba092f95ffc2d2c8ecf1021。不修改生产拒绝条件，不重跑已通过构建。原始输入/命令/失败原文双SHA见[evidence](evidence/RESEARCH-blocking-rows-20261005.json)。当前仅完成实际样本与失败定位，完整物理图/逻辑行/现场/发布均未验收。

首次目录`oracle19-blocking-rows-20261005`：A自有未提交更新READY XID10.7.1674；B PL/SQL变量误命名PRIOR导致ORA-06550/ORA-00936，B rc1，A finally执行rollback/exit failure且rc1；独立观察仅见旧行，没有WAIT证据。没有已提交DML。原目录保留，未覆盖。

修正变量名locked_txt后，在全新r2目录重新获取实际基线2362439/SHA a5403aeda0ff9765979ef6dabc76876c56089802c4ea5642ca630754d494d30a，先核source/generation/原行状态。A第一次CAS TXT concurrent-closed→blocking-open保持未提交；B SELECT FOR UPDATE同一行，获取锁后才校验A最终值并CAS更新，避免以尚未成立的TXT谓词导致根本不等锁。

独立v$session证据`WAIT|5|19976|enq: TX - row lock contention|WAITING|3631|3631`，B模块ORK_BLOCK_B确实被A模块ORK_BLOCK_A阻塞，WHILE_BLOCKED仍原值concurrent-closed。A第二次CAS至blocking-a-final然后提交，B获取锁、校验值后更新blocking-closed并提交。两个会话终态rc0，完整XID A10.17.1678/B10.22.1675。无DDL/config/supplemental/restart，唯一已提交实验未重放。

冻结seq38（7680字节，SHA390cb38eac4844f009f72eca1d0b3ca7d2b08c206dd67163b7ea25323aae3bce）/seq39（6144字节，SHA337db6af29d6798df632f55d0f54da01501a36657387d3a9760eb2260ff3fe89），连续覆盖2362407→2362451→2362469。最终专属表一行ID11/blocking-closed/new-note；下次源脚本不得再假定concurrent-closed。

复用现有制品，compile rc0；physical/rows均`UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT`、rc1/stdout0。native dump在A begin位置38:14:16观察5.2 `flg:0x004a`，现有decoder仅支持0x52/0x452；这不是“同rowkey并发拒绝”分支，也不是逻辑行通过。需要从原始字段/native独立论证0x4a实际布局/UBA/parent/完整XID，再最小扩展并加真实字段反例。不要仅把flag加白名单；不得从SQL最终值一致推断未知开始控制已支持。源动作完成后只读继续，严禁重放已提交A/B。

准备验证命令第一次PowerShell/python -c引号错误，未创建验证器/未启动解析；改为文件生成器后运行。原始源失败与两次解析拒绝日志完整保留。接续优先修真实0x4a开始控制及全物理图，随后独立native顺序/SQL三更新完整before/after与拒绝；一般同一行并发、无目标静默、W7持续capture/ACK、W6接入等继续待证。
