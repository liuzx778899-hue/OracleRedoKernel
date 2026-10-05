# Cluster正逆行配对与三SYS列索引复合键研究

内核b3455f0/kernel SHA256 `cca2546568837c9af03a2ff19aa85eec21d53c81bc5168d06decf2287924d6d0`，lab9ab3410；Java17对应提交树完整package 282/0/0/0。上一9288afbc的96个class逐字节不变，仅增加ClusterUndoPair及其Current class，公共事件/历史门禁路径未变。没有新产品构建/HTTP/目标动作，产品仍efd068f/737旧候选。

## 配对实现和原始验收

ClusterUndoPair仅绑定同record唯一forward UBA和唯一5.1 header，使用UndoHeader完整XID和受支持当前F/C前缀UBA；要求SYS logical/data角色、tableNumber、block/slot、操作互逆、lock0→2及URP stored/changed列集合和相反size delta。KTB L历史ITL不提供当前身份或前驱，未知前缀拒绝。单个关联不是完整事务/父对象/SCN历史权限，调用者仍需源身份与完整original/control覆盖。

冻结seq66/67三个已核闭合SYS事务中，八组TAB$/COL$正逆行片全部成功关联；role、原始/forward位置、完整XID、current UBA、operation、block/slot/tableNumber与前轮独立native/原始identity零差异。包括current F完整XID与foreign L旧ITL锁，以及没有current XID的C前缀；后者完整身份来自唯一5.1且与归档自主所选identity一致，不把旧ITL当当前身份。

九DERIVED原始归档反例：错forward UBA/slot/tabn/container/完整XID，错undo logical/generation/wrap，错lock transition。均重封checksum后scan rc0、pair rc1/stdout0/事件0；完整失败stderr/rc保留。unit另核重复/缺证据、changed map/extent delta、unknown ITL、foreign L与full sequence低wrap碰撞。只有有限配对，不是完整SYS row image或历史DML。

## 父对象输入新事实

继续核全部22个原始节点，发现列索引不仅I_COL1/2，还包括I_COL3；共有十个10.22原始索引节点。专属源只读查询当前logical/data/name为48/48/I_COL1、49/49/I_COL2、50/50/I_COL3。

十个复合key及rowid与native逐项一致：I_COL1是OBJ#字节+NAME、外部六字节rowid；I_COL2是OBJ#字节+COL#字节+内嵌六字节rowid，无外部data字段；I_COL3是OBJ#字节+内部列序号字节、外部六字节rowid。均见first component `c3083456`（本fixture独立OBJ#75185尺子）和rowid `004089a40033`，指向DBA4229540/slot51。当前普通IndexUndoPair只支持单component key，不能给这些复合键自动放行；尚未实现复合索引typed pair、parent生命周期或generation权限。

这些当前角色查询仅格式研究；旧V2没有可信SYS索引角色/世代，不能将晚查询回填为旧capture authority。不得凭同XID/同块/cluster key index4或单独rowid直接认定parent。I_COL3必须纳入下一轮完整依赖覆盖，不能漏掉以减少工作量。

native keydata/bitmap标签使比较器r1失败，原driver、完整终端原文/rc1已保全；r2仅补标签解析并重读原始冻结结果，十项零差异，不重放任何source动作。

## 保全和接续

[169文件双SHA](evidence/DELIVERY-cluster-pair-20261006.json)，旧98逐项SHA/长度未变。包含新jar/45报告、八配对、九原始反例、只读SQL、十key/native与比较器失败；秘密没有输出或哈希、大件ignored不上传。专属源仅只读一次role查询会话，无源DDL/DML/grant/配置/重启。

[复合索引/可信新版获取执行包](DISPATCH_W5_DICTIONARY_COMPOSITE_INDEX_20261006.md)：生产复合key/pair与三个SYS index角色/世代，明确版本可信获取，再新baseline之后新raw历史DML正功能；保留旧V2历史拒绝。间接依赖/跨窗/C++采集租约/独立目标/矩阵压力断电/独立评审现场发布继续，原goal active，automation保留。Codex卡顿整个Oracle目标完成后查。
