# SYS完整历史像与OBJ$九列更新候选

代码b65ade5；Java17完整kernel292/0/0/0，SHA256 `f78ad0f997ae81a6096c8380b0462532dcdcaf23d40eae0385e86e7637f98c36`。新增DictionaryObjectUndoPair，限定SYS.OBJ$/37列/完整logical-data世代和F/C当前UBA、完整XID；增加已实证九列URP 30字节KDO profile。普通HeapRowPatch/HeapUndoPair入口仍不放行该30字节profile，既有行为不泛化。

## 格式证据和生产候选

三SYS OBJ$更新实际changed6/8/9、stored26、KDO29/29/30；九列对应两字节bitmap扩展和观察到的0x20标记，前两例0x10。正逆changed集合/extent、slot、current UBA与UndoHeader完整XID严格一致；inverse KTB L属历史ITL，不提供当前身份。未知表号、位图/标记、xtype/当前前缀、列集合/世代/完整XID拒绝。初始普通入口对DROP的30字节记录拒绝/stdout0原文保留。

十一组SYS行pair（八TAB$/COL$及三个OBJ$）全部解析；另十索引original和一个非行控制original显式处置，与先前归档自主清单全部22位置一致，不以部分行清单替代全图。生产变化仅OBJ$ scoped pair；完整历史重放仍是研究脚本，没有新增事件发布权限。

七个合法checksum原始归档反例：bitmap/current full XID/UBA/logical/generation/table/inverse column map，全部scan rc0、完整row-pair入口rc1/stdout0/事件0，完整stderr/rc及原始差异保存。Unit还核普通入口拒绝、同record重复、错layout及generation。修改共享heap代码后，旧实际V2三完整IUD结果字节不变，原/擦marker历史V2仍按DICT_WRITE2522903/object75185拒绝，未重跑无变化适配器22/产品737。

## 完整历史诊断对照

只读取得冻结三个SYS commit SCN2522904/2522911/2522937的完整OBJ$/TAB$/COL$像，分别7/7/6行、267/267/230字段。新COL$ slot51全部四个尾字段NULL得到独立实库尺子；stored32对应payload36，其中最后已存SPARE10为NUMBER0。物理/内部映射37/45/37及cluster key OBJ#与payload偏移独立保留。

研究重放以旧SCN2522819的六行230字段尺子开始，消费十一行pair、十索引增删和一个控制original，正逆值及I_COL1 NAME/I_COL2 COL#+rowid/I_COL3 INTCOL#生命周期一致；完整SYS像与三个真实commit SCN共764字段零差异。缺索引/错parent/错before/缺行四个比较反例全部检出。这不是生产历史入口，也不是可信旧V3获取；旧研究字段、晚查询和诊断IRP尾NULL假设均不提供事件权限。旧V2历史门禁保持。

## 保全和下一步

[220非secret文件双SHA](evidence/DELIVERY-sys-full-history-20261006.json)，旧219逐项未变。包含新jar/48kernel报告、完整历史SQL值尺子、行pair、七语义反例和初始profile拒绝；首次mutation定位器把跨块metadata当连续字节导致AssertionError，未生成DERIVED输入，执行driver和工具原文副本保留；r2仅在剥离block header的payload上定位，再映射回原始offset/checksum。秘密不输出或哈希，大件ignored。

本轮没有业务DDL/DML、配置变更、目标动作或新产品构建。上一V3读取权限实验仍不当生产零侵入证明。接续[生产历史内核执行包](DISPATCH_W5_SYS_HISTORY_KERNEL_20261006.md)，然后新V3基线之后新raw历史DML/产品获取账HTTP→目标commit/CPACK。原goal active，跨窗/采集租约/独立目标/类型环境矩阵压力断电/独立评审现场发布继续；Codex所有Oracle任务完成后才查。
