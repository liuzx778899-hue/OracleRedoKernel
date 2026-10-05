# 三SYS列索引复合键与正逆配对交付

生产代码646841d，Java17完整package测试286/0/0/0；kernel SHA256 `c8586fcb25ff60296f020b5d627cec58ee522182feead245469386b38e1f3292`。新增DictionaryIndexKey和DictionaryIndexUndoPair，复用IndexUndoPair的同record唯一UBA/完整XID/leaf guards，仅私有dictionary模式允许复合键及inline rowid字段省略；公共普通单列入口保持拒绝复合键。

I_COL1解析OBJ#/AL32UTF8 NAME及外部六字节rowid，I_COL2解析OBJ#/COL#/内嵌rowid，I_COL3解析OBJ#/INTCOL#及外部rowid。要求完整NUMBER整数、限定角色、logical/data generation、index DBA、精确key/component/data/rowSize和forward/inverse identity。index DBA为SYS.IND$ FILE#/BLOCK#研究尺子，不额外宣称已证明物理树root。

冻结seq66/67三SYS事务的十组10.22索引original全部配对，完整XID/current UBA/leaf block/ITL、parent75185、列名或序号以及rowid DBA4229540/slot51与原始/native研究结果一致。十二合法checksum DERIVED反例（key/component/rowid/UBA/full XID/logical/generation/index DBA/rowSize/container/wrap/缺forward）均scan rc0、pair rc1/stdout0。全部原文保留。

因修改普通索引共用代码，只做受影响的冻结batch检查：actual静态V2三完整IUD rc0、输出逐字节相同；actual历史V2原始和擦marker两组仍rc1/事件0，准确拒绝TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185。不重放源事务，不重跑无改动产品737或HTTP。

专属源仅固定旧SCN2522819只读查三个索引地址，rc0。旧V2没有可信索引角色/地址/完整SYS布局，晚查询不登记为旧authority；本轮不是V3获取、完整parent生命周期、历史DML或新产品验收。产品仍efd068f/737、application2db7106f，未换新kernel。

[204文件双SHA及旧169不变](evidence/DELIVERY-dictionary-index-pair-20261006.json)包含kernel、46测试报告、正负原始命令/输出/rc和影响检查；大型制品ignored，秘密不输出或哈希。源DDL/DML/配置和目标动作均0。

接续[可信SYS获取与完整历史执行包](DISPATCH_W5_TRUSTED_SYS_HISTORY_20261006.md)。原目标active，跨窗/C++采集租约/独立目标/扩展矩阵压力断电/独立评审现场发布继续；Codex卡顿全部Oracle目标完成后查。
