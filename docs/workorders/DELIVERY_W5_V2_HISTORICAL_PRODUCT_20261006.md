# V2历史原始/擦标记输入的实际产品拒绝

同件产品efd068f/737（本轮不重跑）、application SHA `2db7106fecbb443c92b62a29e41e12dc74d71cdd099df30ad0af49b25b0f6d90`、kernel672f01bc。driver提交96e6526，未改生产内核或产品逻辑。目标保持active，独立评审/现场/发布待证。

## 新源与真实获取

专属oraredo-19c-lab-20261004，新表ORKLAB.T_V2_HIST_20261006/index PK_V2_HIST_20261006（75185/75186），seed0/dictionary-seed/anchor。新控制ORKV2HIST_20261006、target前缀V2HIST、source oracle-v2-history、consumer v2-history-consumer、helper ork-oracle-v2-history-20261006/port37223。

同product实际HTTP获取acquisitionId100644812901937152/SCN2522819，V2 SHA `e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27`；六锚点/三历史列/基线seed行位置及字节独立ASOF零差异，早于新DDL/DML。ADD EXTRA nullable→稀疏UPDATE dictionary-added→COMMIT，随后DROP EXTRA→UPDATE dictionary-restored→COMMIT，两源事务已结束，严禁重放。seq66/67为6731/23块，collector rc0、capture/归档零差异；最终源0/dictionary-restored/anchor、旧表及NO/NO保持。

## 原始与DERIVED拒绝

同kernel原始V2 rc1/stdout0：`TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185`。对获取SCN之后三条24.x schema-family vector只擦opcode、重封块checksum/帧SHA的DERIVED仍结构通过，语义同原因rc1/stdout0；SYS/业务字段未改。native核对六条目标OBJ$/TAB$锚点写入、四条同XID新COL$行位置关联零差异，仅位置/身份关联，不当完整SYS事务历史通过。

去锚点V1诊断DERIVED输出2个三列事件，缺ADD期间EXTRA=NULL，保留其错误投影。产品负例登记仅借其候选事件/摘要作为敌对声明，明确重新绑定真实V2 acquisition及SHA；V1永不获得authority。原始和DERIVED capture各自真实SHA/大小绑定；不能把该负登记称完整正投影通过。

实际认证HTTP任务100646230979342336首次原始、换DERIVED后同task恢复、同件服务重启后恢复三次实库REFUSED/rc2/event0，独立hex还原三次准确原因均为 `ORACLE_APPROVED_INPUT_REFUSED: TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185`，不是外围绑定/哈希错误拒绝。目标seed不变，APPLIED/MEMBERS/COMMITS/ledger0；CP空、窗口PENDING、ACK无文件。三比较反例event-count/target-row/wrong-reason检出。

## 失败原文与保全

重启首次snapshot读到RUNNING/两批，而随后的reason已读到三批，比较器失败；只读复查同task得到FAILED/三拒绝，不再次恢复任务。r2终态比较器因放入out目录相对root错误在读取前失败；r3改为workspace root完成验证。原snapshot、两失败trace、执行driver/修复driver保留；纳管restart脚本增加同task有界终态观察，未重跑已通过轮次。

[334非secret文件双SHA清单](evidence/DELIVERY-v2-historical-product-20261006.json)，上一轮446文件逐项未变。执行后driver留存与执行前hash/真实capture副本区分；秘密、大型raw/dump/jar留ignored。helper exited143、旧目标/旧源及源服务保留，不改共享checkout；新target同实例跨schema，不是独立生产目标。

## 接续与验收边界

V2_HISTORICAL_PRODUCT第1—3项及第4项位置关联/比较器已具实证；本轮authority/SCN/role/anchor获取身份实库反例矩阵尚待补，完整SYS事务链/间接COL$历史尚未认证。本轮只是产品安全拒绝，不算历史DML支持/完整DML/现场发布。

接续[获取身份反例与完整字典链派单](DISPATCH_W5_V2_IDENTITY_DICTIONARY_HISTORY_20261006.md)，同件不重复构建；随后完整COL$依赖及SCN历史、活跃跨窗/C++持续采集/租约/独立目标/矩阵压力断电/独立评审现场发布均仍原目标。Codex卡顿按用户指令在整个目标完成之后才排查，automation保留。
