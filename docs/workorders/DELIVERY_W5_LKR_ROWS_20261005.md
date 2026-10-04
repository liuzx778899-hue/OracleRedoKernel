# W5 真实锁等待完整行及LKR物理节点

有限样本验收通过：生产解析使用原始seq37/38/39和真实snapshot，完整保留LKR原始节点，输出A两UPDATE、B一UPDATE三完整行，与native/四SQL时点零差异。一般同一行并发、锁取消、lock-only空事务、持续捕获/ACK、接入、现场与发布尚未通过。

运行时592c817，独立lab6b9fe9d；Java17 `release-lkr/tree` 233测试0失败/错误/跳过，jar SHA256 `db949a68260052b5427cde1500b65231dcf5ede18286855dbfc325379c1729b5`。[封存清单](evidence/DELIVERY-lkr-rows-20261005.json)关联输入/制品/输出/命令/rc并双SHA。源端提交均为此前唯一blocking fixture，本轮无DML/DDL/config/重启，不重放。

## 输入和判据

沿用实际snapshot2362439，获取SHA a5403aeda0ff9765979ef6dabc76876c56089802c4ea5642ca630754d494d30a，source/generation由实际获取绑定。连续seq37/38/39包括前次失败A的完整回滚，原始SHA分别02a4e097…/390cb38e…/337db6af…；必须包含前缀，不能删除补偿来凑正例。全部native参考来自只读全范围dump，不是生产输入。

39:6:16中B forward11.4对应5.1 original vector5，完整UBA02402b14.0108.03/XID10.22.1675。forward/inverse KDO均20字节且XA，slot0，单piece itli1，to字段1/0，没有列字段。限定接受该单行锁布局及0→1配对；其他xtype/extent/额外字段/transition拒绝。`LOCK`保留独立物理kind、显式lockTo，列均ABSENT而非NULL；物理graph保留原始节点和位置。行入口收全pair后验证完整基线存在该行，再保持其全部列字节，LOCK不输出业务变化。

全输入自主发现三物理图：旧失败XID10.7.1674为1/1/0，A10.17.1678为2/0/2，B10.22.1675为2/0/2，共5原始/1补偿/4live，native位置/UBA/顺序零差异。B的两个live包括LKR和URP，只有URP产生事件。输出A concurrent-closed→blocking-open→blocking-a-final，B blocking-a-final→blocking-closed，三完整before/after。

独立native完整生命周期证明A结束RBA早于B begin RBA；v$session真实WAIT绑定B被A阻塞，等待SQL仍见旧值；OLD_BASELINE、WHILE_BLOCKED、AFTER_B_COMMIT、FINAL四SQL时点对照零差异。由此本次是真实锁等待后串行redo窗口，不证明两个已活跃redo窗口同一行交叠的通用处理。最终专属单行ID11/blocking-closed/new-note。

## 反例/回归

六份真实seq39副本重封checksum，scan均rc0。错误forward to、inverse to→UNSUPPORTED_HEAP_LOCK_TRANSITION；slot→ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH；去掉11.4 forward→PHYSICAL_ORIGINAL_PAIR_COUNT；未知锁xtype→UNSUPPORTED_HEAP_LOCK_LAYOUT。上述physical/rows均rc1/stdout0。最后URP inverse before值篡改physical rc0，rows ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0，在两个合法UPDATE及锁节点之后不泄漏前缀。

五比较器反例：漏UPDATE、额外锁伪装UPDATE、错误行顺序、物理图漏锁NODE/LIVE、等待SQL泄漏未提交值，均rc1。public events仍DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0。

同件jar旧seq30/31/32和不同行并发四完整行各native/SQL零差异，seq36rollback-only仍rc0/变化0；真实基线跨事务、晚before、同rowkey派生冲突、相同结束SCN拒绝回归均stdout0。回归驱动最后尝试复用已存在events输出名，防覆盖assert失败且该重复命令未执行；已完成的events结果保留，这不是产品失败/重试，前述各回归rc文件有效。

## 接续主线

LKR的实际锁补偿、其它itli/to、lock-only空事务、index-only指定分支等继续未验收。下一步主线优先W7固定member journal升级为连续捕获：核现有执行包/帧证据，完成日志组切换、record重组、持久续接、真实静默/进度与ACK边界，再接W6。不得把本例有限行解析当持续捕获或发布，也不修改源端supplemental/业务配置。失败应保留raw帧、源身份、制品/输出SHA、rc和可执行下一步。
