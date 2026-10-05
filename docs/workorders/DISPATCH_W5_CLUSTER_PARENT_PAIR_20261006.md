# W5 clustered行配对/父对象/可信历史schema

前置：内核0bd5005/9288afbc/278，验证5da1b55，O=lab/out/sys-cluster-row-candidate-20261006，S=lab/out/sys-cluster-row-research-20261006。16物理片段/98native列/24ASOF值及DROP消失已核，仍无parent/完整pair/history授权。旧98/173/34/146文件保持，原seq66/67事务闭合不重放。

1. 在冻结22-node完整SYS控制history上绑定正逆cluster patch：同container、完整UndoHeader XID、精确current forward UBA、同物理dba/slot/tableNumber、逻辑/data角色和操作互逆，KTB L旧ITL不当current XID/前驱。LOB/key row/rowpiece/未知type拒绝，合法checksum错tabn/slot/UBA/XID/logical对象与错map必须语义拒绝/零输出。
2. 从真实I_COL1/I_COL2原始index key、rowid与同XID leaf反向/正向证据建立COL$slot→OBJ#映射，并覆盖删除/改名/col#改变和generation。不能把列名、同事务、同块或cluster key index4直接当父对象。独立native/固定SCN SQL验证key数字/名字/rowid，但生产输入只能raw或已信任获取结果。
3. 原V2没有可信SYS索引role/generation和完整SYS布局/行值；不能将本次晚只读查询登记为旧V2生产authority。按明确版本扩展可信获取结果，固定SCN读取系统物理/内部布局及所需索引身份/锚点，校验before/after/source/receipt，未知或不可读拒绝；内核维持Java17无数据库运行依赖。保留旧V2静态DML支持及历史拒绝。
4. 新可信获取SCN之后才新建一次受控ADD/DROP fixture及raw，完整SYS原始/控制/row/key覆盖、列类型/nullability/hidden/physical ordinal/gen变化重放到实际commit SCN。新增EXTRA=NULL和DROP后的完整DML正功能、ABA/错父对象/漏COL依赖/错物理内部映射反例独立核实，不使用晚baseline或最终字典反推历史。
5. 对应提交树全量build/SHA，新隔离依赖更新P产品并完整build；真实认证HTTP→目标commit→controlCP/持久ACK/重复/重启验证，旧737不作新候选证明。然后活跃跨窗/C++采集/租约/独立目标/矩阵压力断电/独立评审现场发布按原目标继续。

专属源环境和共享只读边界、失败追加、输入/制品/输出SHA与rc/events关联、禁止AGPL复制、Luna/子代理。不要把物理片段、结构通过、安全拒绝当完整DML/现场发布。Codex卡顿所有Oracle目标完成后才查，goal/automation保留。
