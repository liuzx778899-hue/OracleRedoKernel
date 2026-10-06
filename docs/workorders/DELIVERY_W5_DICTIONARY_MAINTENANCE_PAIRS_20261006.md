# I_COL3锁／链接／branch typed pair候选

新增DictionaryIndexMaintenancePair，仅支持独立真实字节确认的I_COL3 10.6 pre-split lock、10.11 previous-link、10.15 branch insert及对应10.21 undo。绑定完整current XID/UBA、角色/世代/tablespace、可信segment地址、同记录block、ITL与逆向操作；不把undo中的foreign历史ITL认成当前事务。不支持未知profile，不授权事件。

本次内核完整clean package **305/0失败/0错误/0跳过**，SHA256 `1911a0cd7727ff62deddef78375d1f148d24f3b5e299d8b39a64c01149829d6a`。新增四测试覆盖真实link/branch解码及错current identity/UBA、重复pair、action/segment/generation/slot、separator末标志、未知link flags/未改变link拒绝。真实lock正向另由完整冻结记录验证；这不是所有维护负例已验收。

原实际产品V3基线和seq70真实字节，三对typed输出与native独立0差异：

| 原始位置 | 类型 | 核验结果 |
|---|---|---|
| 70:126592:380 | LOCK | XID9.9.660 / UBA02402f4f.e6.1，old leaf0040512d，itl1/pre-split5 |
| 70:126619:224 | PREVIOUS_LINK | neighbor0040512e，previous0040512d→0040512f，UBA02402f51.e6.2 |
| 70:126619:460 | BRANCH_INSERT | branch004001c1，slot299/child0040512f，separator raw04c3083552fe/parent75281，UBA02402f51.e6.3 |

旧115个production class逐字节未变，仅新增Pair与Kind两class。完整历史工厂尚未接入此Pair，保持上一候选索引覆盖拒绝；本次没有重复跑同一工厂。三个局部pair不是五original及结束重建的完整维护闭包，不能据此发布两业务事件。

首轮研究探针在判断scope前解码范围外undo，rc1/stdout0/UNSUPPORTED_UNDO_HEADER_FLAGS；原探针源码/class/命令/失败日志已保留。r2先识别scope，再严格解码范围内identity；只修研究探针，不放宽生产flags。r2真实三对rc0，诊断events0。

[139项双SHA/写后核验清单](evidence/DELIVERY-dictionary-maintenance-pairs-20261006.json)，前130+205+407逐项不变。0源动作/配置/重启/目标/产品构建换装；禁止源重放和晚baseline回填，lab/out与secret不入Git。原完整goal active，现场/发布未通过，Codex最后查。

## 下一执行包：完整split与恢复槽

P0继续冻结70/71，不重源动作、获取或旧测试；新输出目录，不覆盖139/130/205/407。

1. typed解码70:126593:240的10.9/8032字节前像、70:126610:328 new-leaf10.8、70:126620:204 end同记录old-leaf10.8。两类10.8 forward字段不同，不能统一猜测。按真实目录/行数/边界校验，前像445行→220+226，仅一0101删除标记新增；full row bytes/NULL/key/rowid不得从dump/SQL输入。
2. 与本三Pair及上一完整生命周期在同一个自主维护上下文结合：全五original、6记录含独立end重建、segment/source/generation/block/ITL/UBA、链接互指、branch child和separator、全行守恒、commit位置完整闭合。任何未知/缺失记录拒绝；不是只把三Pair记为controls跳过。
3. 以闭合old-leaf原始槽7绑定主字典事务70:126628:476的10.5/10.22 key与rowid；物理删除标记行恢复应为逻辑索引INSERT，维护本身不制造COL$新增。接入完整历史发现/全original分类/三索引状态后，同基线两业务UPDATE/schema4→3才能放行。
4. 错link/child/separator/行/槽/rowid/UBA/generation、缺中间或end重建记录做合法checksum全入口反例/rc非0/stdout0；独立native/阶段SQL验证两完整事件。再适配器/私有产品真实嵌入、HTTP→target→CPACK/重复/重启及错receipt零推进，独立目标/现场发布另验。

六状态：代码交付=三局部typed Pair候选；针对性验证=305与真实三Pair通过；集成验证=完整维护闭包未通过；部署=未换装；现场验收=未通过；发布=未开始。
