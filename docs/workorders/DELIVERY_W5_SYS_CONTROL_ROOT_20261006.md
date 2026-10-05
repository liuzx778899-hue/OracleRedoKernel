# SYS非行begin/首行根候选与冻结控制历史

内核代码70c1a12，lab6e8ac66；新kernel SHA256 `5fb55de7f1d75cfdab71307a7734508cc5c49873ac543ee51add0ae9517c6af6`。Java17对应提交树完整package：275 tests、0 failure/error/skip。没有源/目标动作；旧seq66/67源事务不重放。产品efd068f/737/application2db7106f仍为旧候选，没有新产品换装或HTTP通过。

## 最小实现与范围

`TransactionStart`仅增加观测flags51、size80非行profile，绑定同record精确UBA与完整XID后要求obj/data/tsn0、begin5.7、20/76字段及size匹配。`UndoControlRoot`将该分配begin与首个非begin行根分别表示；首次根必须紧接所选分配原始输入、同container/完整XID/同undo块和世代、下一分配record、previous DBA/rci0、低wrap一致，并有同record唯一segment/slot/UBA/size匹配flags2控制及唯一原始header。

归档自主遍历持有全部5.1覆盖，普通非begin rci0仍拒绝。`UndoHistory`将非行begin保留在original分配清单，但不加入行链active head；首行根前驱为null，绝不造33→32边。row-live不表示完整行像或字典授权，也不把控制begin误标补偿。

## 冻结实证

|sequence/XID|原始节点|非行分配控制|闭合live行节点|native差异|
|---|---|---|---|---|
|66 / 10.18.2272|6|0|6|0|
|66 / 3.30.705|10|1|9|0|
|67 / 3.26.705|6|0|6|0|

全部22条identity/UBA/metadata有序清单与独立native一致，原始前驱逐项一致；三笔完整源身份生命周期、全部目标段slot控制窗口、5.4结束RBA/SCN/tail由raw核实并与native独立对照。关键非行事务SCN2522911、tail37749054:436:41；另外两笔结束SCN2522904/2522937。只认证冻结范围内无补偿commit的原始/control history，不宣称一般SYS事务恢复、完整SYS行/SCN历史或DML。

九项DERIVED原始归档反例：缺begin、缺首控制、错控制UBA、错wrap、错slot、错container、首对象0、错rci、漏中间original。全部scan rc0；history rc1/stdout0/事件0；每项完整stdout/stderr/rc保留。单位测试另覆盖重复控制、缺首行、完整sequence碰撞、错size、漏输入与错误begin形态；属于构造反例，不替代原始反例。

新内核读取原actual V2及擦24.x捕获，两者仍rc1/stdout0，原精确 `TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185` 原因不变。只维护历史门禁；安全拒绝不能当历史DML正功能。

## 保全与接续

[173文件双SHA](evidence/DELIVERY-sys-control-candidate-20261006.json)含新制品、43测试报告、执行pin、三笔history/native、九原始反例和两历史拒绝；旧34及146逐项SHA/长度未变。原672的SYS清单拒绝与比较器失败未覆写。不上传大型raw或秘密，不重复已接受产品/源操作。

[下一步SYS行值/SCN历史](DISPATCH_W5_SYS_ROW_HISTORY_20261006.md)。间接COL$链、活跃跨窗、C++采集/租约、独立目标、矩阵/压力断电、独立评审/现场/发布仍待完成。原goal active，automation保留；Codex卡顿整个Oracle目标完成后查。
