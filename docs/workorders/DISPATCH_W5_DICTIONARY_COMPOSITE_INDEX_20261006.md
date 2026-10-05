# W5 三SYS列索引复合key/pair与可信历史获取

前置：b3455f0/kernelcca25465/282、lab9ab3410，O=lab/out/sys-cluster-pair-20261006；八cluster pair及九合法checksum语义拒绝通过，十index key/rowid和三index角色仅格式研究。旧169/98/173/34/146文件保持，seq66/67源事务闭合不重放。

1. 最小实现限定I_COL1/I_COL2/I_COL3的short composite key解析，分别两component+外部rowid、三component含rowid且外部data省略、两component+外部rowid。NUMBER、AL32UTF8 NAME、big-endian六字节rowid逐字节独立native/SQL尺子核实；缺component/截断/NULL/错rowid/type/未知layout拒绝。普通单列IndexUndoPair门禁不得广泛放宽。
2. 实现同record typed dictionary index forward/inverse pair，完整XID/current UBA、leaf block/ITL/key/keydata/rowSize、logical/data index generation、root DBA与角色校验；PURGE/RESTORE及inline data省略区别实证。覆盖全部十索引original，不凭固定XID/同block/单列名认定parent，非目标角色/未知5.1不能偷删。
3. 将已闭合SYS original/control、cluster pair、index pair和OBJ$/TAB$已信任锚点关联成COL$ row→OBJ#/列键的完整变化史；I_COL3不可漏掉。删除/改名/col#变化与slot重用/ABA/低wrap碰撞、错logical/gen/parent/UBA/XID/key、漏依赖须合法checksum raw结构通过/语义拒绝，零输出。仍缺权限时维持原V2历史拒绝，不签发事件。
4. 明确新版可信获取格式，固定SCN读取完整SYS物理/内部布局、三个索引身份/generation/所需锚点及可恢复列元数据；before/after/source/持久receipt校验，unknown/不可读拒绝；内核无数据库运行依赖。晚研究查询不能登记为旧V2authority；旧静态V2支持保持。
5. 新获取SCN后才新的专属fixture ADD/DROP一次、新raw和独立ASOF/native完整尺子。完整SYS/control/row/key依赖重放到实际commit SCN，EXTRA=NULL和DROP后完整DML正功能、错物理内部映射/ABA/漏COL等反例通过后，明确新kernel/adapter隔离依赖、P提交树完整build、实际HTTP→目标commit/controlCP/持久ACK/重复/重启。旧737不当新候选证明。

之后原跨窗/C++/租约/独立目标/完整矩阵压力断电/独立评审现场发布继续。专属环境/共享只读、AGPL禁止复制、秘密/大件不入Git、失败追加/SHA/rc/events关联、禁止Luna/子代理。原goal active/automation保留，Codex全部Oracle完成后查。
