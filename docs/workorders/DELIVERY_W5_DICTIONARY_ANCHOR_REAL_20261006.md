# W5 实际 V2 SYS 锚点获取与新 raw 反例

执行跨 2026-10-05/06（Asia/Shanghai）；证据目录保留开始日期 dictionary-anchor-real-20261005。

## 已证事实
- 新唯一专属 fixture ORKLAB.T_DICT_ANCHOR_20261005，heap74862/index74863。既有实验账号只在专属容器新增 SYS.OBJ$/TAB$/COL$ SELECT/FLASHBACK；不是生产零权限配置证明。
- 已封存候选 kernel29a3fd10、adapter9516d4f9，不重新跑已通过265/21。fresh 实际 V2 acquisition SCN2509388、6个锚点/3历史列/1行，内核严格导入；独立同SCN SQL锚点/历史列零差异。
- 新 ADD nullable EXTRA → sparse UPDATE dictionary-added → DROP EXTRA → UPDATE dictionary-restored，两次源事务已结束。seq59/60捕获和归档数据块零差异；源表初末3列/中间4列，相同heap/index世代。旧表11/blocking-closed/new-note与补充日志NO/NO保留，旧源事务不重放。
- 新原始batch rc1/stdout0，首先遇到未知24.4而拒绝。它没有证明锚点屏障是原始批次首个拒绝原因。
- DERIVED只把同PDB/基线后3条schema-family向量（1条24.4、2条24.1）opcode改0.0，重算块checksum/frame SHA；全部SYS/DML字段保留，逐字节差异断言成立，两capture结构通过。V2 batch rc1/stdout0，TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2509458:object=74862。
- 实际基线锚点对应raw 6条OBJ$/TAB$写入及4条同XID新COL$行，全部native角色/DBA/slot零差异。仅位置/同XID关联，不等于完整SYS事务图或一般历史字典。
- 同候选受影响冻结mixed/empty结果逐字节不变。

## 失败与未验收
去掉字典锚点的DERIVED V1诊断对照rc1/stdout0，UNSUPPORTED_TRANSACTION_END_EXTENSION；没有复现该新capture的2个漏列事件，不能将此前旧反例移作本轮结果。定位两条真实5.4原始字段及native：seq59/135:216 SCN2509398、seq59/185:444 SCN2509478，保存为后续独立扩展判据。未知24.4语义、目标完整XID/当前对照完整事件、新获取账/产品HTTP均未验收。

capture收尾与prepare的post-source文件名冲突，collector已rc0/两笔源动作均结束，只做只读收尾和reference补写；未重放。原verifier对24.4拒绝原因预期错误、去锚点对照预期错误、native比较文件名错误均保留源码与输出，只补失败验证。内核与adapter制品未改变。

## 证据与接续
[安全双SHA清单](evidence/DELIVERY-dictionary-anchor-real-20261006.json)；raw/dump/secret留ignored lab/out。所有非secret证据逐文件两遍SHA相同；执行前脚本/class/jar pin与失败后观察记录分别标明，不冒充执行前证据。

[下一单](DISPATCH_W5_DICTIONARY_END_PROFILES_20261006.md)。历史P0未整体关闭，独立评审PENDING，无目标写入/产品部署/现场/发布，goal active，automation保留。
