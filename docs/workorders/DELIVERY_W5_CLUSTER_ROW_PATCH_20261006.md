# SYS clustered物理行片候选与独立列值核对

代码0bd5005、验证5da1b55；kernel SHA256 `9288afbcbd0e53999ed38631121078731e8fd265fb350f26960f998b9ef24557`。Java17对应提交树完整package 278/0/0/0。只增加ClusterRowPatch及其Patch两个class，上一5fb55de7的94个class逐字节一致；历史拒绝路径/行链规则没有放宽，不重复旧三笔控制history或737产品轮次。

## 实现与原始事实

新增独立、明确tableNumber的cluster KDO物理解码器，覆盖已观测TAB$ tab1 URP、COL$ tab5 IRP/DRP/LKR/URP窄XA单片profile。C/F/L flags0x6c与普通heap0x2c分开、IRP52与heap49分开、cluster lock to2不进入普通heap锁。返回稀疏VALUE/NULL/ABSENT、stored/changed extent、slot、cluster key index；不生成XID、UBA、parent object或事件。IRP末尾未存储列仍ABSENT，不补NULL。

专属源只读SQL会话核系统列layout、I_COL1/2角色、固定SCN2522904/2522911/2522937 COL$ DUMP以及旧表/NO/NO。不新增DDL/DML、grant、配置或源重启。SYS.COL$物理seg14为ACDRRESCOL#而内部编号37，DEFAULT$物理seg37、内部编号14；不能假定内部序号等于物理序号。TAB$45列和COL$37列的OBJ#在簇键中，行片对应余下44/36位置；本样本COL$只存32列。I_COL1/2当前logical/data48/48、49/49仅格式研究事实，还不是原V2中的可信index授权。

从actual V2可信SYS role和source固定范围选原始数据：三笔SYS事务共16个forward/inverse cluster片段，98个存储/修改列的原始字节/NULL与native逐项零差异。COL$新slot51从IRP初始SYS_C…名字和property32800，经两次稀疏URP改EXTRA并设可见属性；DROP inverse32列与重放的已存32列精确一致，forward删除后消失。

独立ASOF两个提交SCN对新slot51的COL#/SEGCOL#/SEGCOLLENGTH/OFFSET/NAME/TYPE#/LENGTH/NULL$/INTCOL#/PROPERTY/CHARSETID/CHARSETFORM共24项DUMP字节一致；DROP SCN没有slot51。SQL/native只作尺子，不作为生产patch或事件输入。这个lab重放只有物理slot和值一致性，不授权parent对象或完整SYS语义。

三个unit case另含wrong block/table/fields/fb/column map等变异拒绝；这是构造反例，不宣称完成原始slot/UBA/parent/配对反例。仍缺正逆事务配对、COL$父对象绑定、四ABSENT尾列的完整性和SCN历史schema。

## 保全与下一步

[98文件双SHA](evidence/DELIVERY-cluster-row-candidate-20261006.json)包含制品、44测试报告、source只读SQL/rc、raw和native16片段、24值/删除核对；上一173逐项SHA/长度未变。秘密没有输出或哈希；raw/完整dump仍ignored，不上传Git。业务旧表11/blocking-closed/new-note和NO/NO保持，seq66/67源动作不重放。产品仍efd068f/737/2db7106f，无新HTTP或目标写入。

[接续父对象与行配对](DISPATCH_W5_CLUSTER_PARENT_PAIR_20261006.md)：真实I_COL1/I_COL2原始key/rowid和generation绑定，补typed可信系统布局/索引获取，随后新SCN基线后新raw历史DML正功能。不用晚快照回填旧captured数据；不能凭同block、同XID或cluster key index直接认定obj75185。

原完整goal active；跨窗/采集/租约/独立目标/矩阵压力断电/独立评审现场发布未完成，automation保留。Codex卡顿全部Oracle目标完成后查。
