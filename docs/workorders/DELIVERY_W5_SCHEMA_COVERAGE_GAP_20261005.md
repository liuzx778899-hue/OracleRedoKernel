# W5 marker覆盖缺口与字典关联证据 · 未验收

发现：对seq56/57两条基线后marker分别改为24.0或0.0、重封块checksum/帧SHA/journal SHA，其余字典/DML字节不变；原9275ab90两组DERIVED均结构通过且rc0输出2个伪完整事件。这是派生验证，不是实库自然缺失日志，不把伪造opcode称支持的Oracle格式。

本轮只补未知24.x拒绝；新kernel SHA256 `bb0225bc0819ca11c0465cff0cd0ab270178bc820f693c845f5fe94372e609a2`，260/0/0/0测试，同24.0派生rc1/stdout0，受影响静态混合结果字节一致。**0.0擦除派生仍rc0/2事件，P0未关闭；新制品未装产品、未做新的HTTP集成，不能引用旧735证明本轮制品。** 不以SQL文本/任意数字匹配或盲拒绝全SYS凑通过。

## 实际只读字典定位

专属源SYS只读获取原基线SCN2501884的ASOF行位置成功rc0，当前位置单独记录；不是Acquisition控制账或事件生产输入。

| 实库角色 | logical/data | 基线物理位置 | 目标 |
|---|---|---|---|
| SYS.OBJ$ | 18/18 | file1/block29640/slot24、25 | heap74695/index74696 |
| SYS.TAB$ | 4/2 | file1/block35216/slot7 | heap74695 |
| SYS.COL$ | 21/2 | file1/block35216/slot49、50、51 | ID/TXT/NOTE |

从原始5.1字段、KDO行位置、XID推导六条OBJ$/TAB$目标锚点写入，关联四条同XID新增/移除COL$行；每条角色/DBA/slot由native独立复核零差异，相关字典字节在marker变异中保留。仅物理位置及XID关联，不证明SYS完整事务解码或全历史覆盖。TAB$/COL$共享data2，不能只按data object路由；新增COL$slot52不在原三列anchors，不能假装已有键。

无源DDL/DML、无目标/产品登记，不重放seq52—57，源NO/NO和旧11/blocking-closed/new-note保持，未改共享产品checkout。独立评审PENDING；代码候选｜针对性有限通过且擦除反例失败｜集成待证｜部署未开始｜现场待证｜发布未开始。原产品草稿#12的有限真实ADD/DROP/HTTP拒绝事实保留，不升级为一般覆盖完备。

## 留证与接续

唯一原文 `lab/out/schema-coverage-gap-20261005`：派生变异精确位置/输入输出SHA、结构及语义输出/rc、固定SCN/当前SQL尺子、六条原始锚点及四条XID关联、260报告、新jar和源码时点。首次比较器括号错误、误要求新COL$行属于旧anchors、clustered IRP slot偏移错误的失败均保留；native尺子否决错slot后修正，不宣称完整clustered解码。新jar是执行工作树构建后提交，源码末次封存与执行pin分开。

接续 [可信SYS锚点获取](DISPATCH_W5_TRUSTED_DICTIONARY_ANCHORS_20261005.md)：实际fresh同SCN获取/格式/权限/获取账闭合，再新raw字典屏障和产品前拒绝；禁止把事后SQL锚点补旧V1并自算SHA冒充authority。完整历史/跨窗、V731六种迁移、采集/租约/独立目标/Windows/ASM/压力/断电/评审/现场/发布原主线全部保留。
