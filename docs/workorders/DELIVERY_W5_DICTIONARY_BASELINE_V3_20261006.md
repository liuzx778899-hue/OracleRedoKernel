# V3完整SYS基线获取与导入候选

代码6b68786；Java17完整内核290/0/0/0，隔离适配器22/0/0/0。kernel SHA256 `97dd395fd958c0fbb71a5442dead3f5d27bf8aeeefb6f9d3bacc884cfd861bb4`，adapter `e095822b13e843e45d761d529f0f10c9e1313ce19aeaf2e4ecdb6fa284e9ae1e`，SPI仍fb9c443b。未构建或换装产品，P仍efd068f/737旧候选。

## 新增可信获取内容

ORK_HEAP_SNAPSHOT_V3沿用来源、固定SCN、目标heap/index、列与行、SYS锚点、前后身份及外部SHA回执；新增OBJ$/TAB$/COL$物理/内部列映射、cluster tableNumber、三个SYS列索引logical/data/index DBA以及六个SYS完整行像。内核只导入字节，没有数据库运行时依赖；适配器在同一受控源连接固定SCN读取值字节，生产获取不使用redo dump或LogMiner。

导入要求顺序/完整字段/唯一内部列/唯一索引身份与地址、完整行位置与原锚点一一一致、父对象值以及OBJ$/TAB$/COL$像与声明的对象/列元数据一致。支持已核实NUMBER/VARCHAR2/DATE/RAW值；非空LONG及尚未验证的timestamp值拒绝，NULL也必须符合SYS必填约束。ABSENT不充当NULL。TAB$的COLS按可见列数检查，INTCOLS/KERNELCOLS按完整存储列数检查。

V1/V2静态入口兼容：同一冻结actual V2三完整IUD rc0、输出逐字节相同；原始/擦marker历史V2仍rc1/事件0、DICT_WRITE2522903/object75185原因相同。V3完整基线也不自动解除历史SYS写入门禁，单位反例明确验证；完整历史重放未实现、未签发历史事件。

## 实际来源与独立验证

旧SCN2522819研究取得六SYS行230字段，JDBC字节与独立SQL DUMP全项一致；这批研究结果不回填旧V2权限。由其组成的V3测试资源是合成fixture，不是实际获取账或旧历史授权。

随后真实新获取SCN2533047、SHA256 `518c0f906c03f548f58f4ce6c5017399e6b9431935c46a7db38e68947c3070f9`，路径lab/out/dictionary-baseline-candidate-20261006/new-v3-r2.snapshot。独立同SCN六SYS行230字段、三个索引和业务基线行逐值/位置/布局零差异，旧11/blocking-closed/new-note和logging NO/NO保留。连接成功路径确认关闭，没有目标动作或业务DDL/DML。

既有实验读取账号初次因SYS.IND$读取权限缺失ORA-00942拒绝，stdout0、无快照。仅在专属oraredo-19c-lab-20261004补实验账号IND$ SELECT/FLASHBACK后再新SCN获取；权限实验不是生产零侵入证明，也未修改共享库。新获取尚未写入StreamHouse持久获取账、实际产品HTTP及历史DML链。

## 失败保全和接续

[219非secret文件双SHA](evidence/DELIVERY-dictionary-baseline-candidate-20261006.json)，旧204逐项不变。大型jar/原始结果留ignored，秘密和私有Maven缓存不哈希或上传。保留首次宿主连接ORA-12514（没有执行源查询，随后改为owned container网络）、RAW23布局拒绝、SQL重复OBJ#排序ORA-00960、错误TAB$计数、测试header和CRLF反例未实际变异、默认Maven缺私有依赖及实际权限拒绝原文；只修相应假设/执行路径，不重放源事务。

接续[完整SYS列历史执行包](DISPATCH_W5_COMPLETE_SYS_HISTORY_20261006.md)：完整原始/control/row/index依赖重放，再新baseline之后新历史raw正功能、产品获取账/真实HTTP→目标commit→CP/ACK。原goal active，跨窗/正式采集租约/独立目标/完整类型环境矩阵/压力断电/独立评审现场发布仍未通过。Codex卡顿全部Oracle目标完成后查。
