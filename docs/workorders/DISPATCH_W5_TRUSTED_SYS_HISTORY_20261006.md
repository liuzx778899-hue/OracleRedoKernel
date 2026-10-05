# W5 可信SYS布局获取和完整列历史

前置：646841d/kernelc8586fcb/286，十dictionary index pair、十二语义反例及旧静态V2完整batch不漂移；旧历史V2仍拒绝。204/169/98/173等封存输入不可修改，seq66/67已结束源事务不可重放。现有索引scope仅研究，没有历史授权。

1. 定义明确新版本可信获取：同固定SCN读取OBJ$/TAB$/COL$物理和内部列映射、cluster tableNumber/所需完整行像、I_COL1/2/3 logical/data身份及index DBA，相关父对象和列元数据/位置。校验获取前后源identity、SCN、完整性、唯一性、receipt；不可读/未知类型布局拒绝、不发快照。旧V2兼容静态支持，晚查结果不能回填旧capture权限。内核不引入数据库运行依赖。
2. 利用闭合original/control与cluster/index pair，重建完整SYS行像和三索引生命周期。关联不等于授权：OBJ#/NAME/COL#/INTCOL#/rowid须跨INSERT/rename/ordinal变更/DELETE/slot复用一致，完整original不可跳过。物理segcol与内部intcol分开，ABSENT不能当NULL。先用冻结raw和独立ASOF/native尺子验证历史推演，仍保留旧V2发布拒绝。
3. 建立合法checksum反例：缺任一索引/间接COL$依赖、错误parent/generation/layout/UBA/XID、ABA/slot复用、错commit SCN、未知完整SYS字段，结构可读而语义拒绝、零事件。不靠same XID或single rowid猜父对象。
4. 新可信baseline先获取，之后仅专属新fixture ADD/DROP及稀疏DML各一次，新raw闭合后不重放。实际commit SCN完整列历史和事件须独立ASOF/native核验，包括ADD后的EXTRA=NULL和DROP后恢复；失败原文/输入制品输出SHA与rc/events关联。
5. 正功能后更新隔离adapter/内核依赖和P产品提交树，完整build及真实HTTP→目标原子提交→control checkpoint/持久ACK→重复/重启。旧737和结构/安全拒绝不能当新产品或历史DML通过。

随后继续原跨窗口活跃事务、正式采集/租约、独立目标实例、扩展类型/环境/压力断电、独立评审及现场发布门禁。禁止Luna/子代理，共享checkout只读，AGPL代码不复制；所有Oracle目标完成之前不排查Codex卡顿，goal/automation保持。
