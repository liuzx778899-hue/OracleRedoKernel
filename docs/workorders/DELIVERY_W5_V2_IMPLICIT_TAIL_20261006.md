# V2新raw的NULL尾列逆向布局交付

代码391eaf7188195147ef1b4cc9d87cb13d5bf819fe；Java17 `clean verify` 271项通过。kernel SHA256 `672f01bc0c4a78856ce40af4de778bdca3fe881d3c46eb5de3fb069ff433302e`。仅内核有界DML验证通过，产品新制品、HTTP目标提交/CP/ACK尚未通过。

## 新源与冻结输入

专属容器oraredo-19c-lab-20261004，新表ORKLAB.T_V2_DML_20261006、索引PK_V2_DML_20261006，heap75018/index75019，ID/TXT/隐藏NOTE三列；seed0/v2-seed/anchor。产品旧候选ea1975c实际获取V2 acquisitionId100634385207877632、SCN2517052、snapshot SHA a9fc1bb611424c3f59fb2c9ec5cd4ac15d576be0cf11c8eb334e01ab0d909e40，早于新源DML。本轮独立ASOF六锚点/基线逐值核验仍待补，不挪用上一轮获取核验。

三笔源事务已结束，严禁重放：INSERT11/NULL/NULL→UPDATE TXT=v2-after→DELETE11后COMMIT（XID2.13.614）；INSERT12后整事务ROLLBACK；DELETE seed0后整事务ROLLBACK。seq62/63/64分别6695/17/29块，collector rc0，capture/归档零差异。

## 失败与修复

旧444630a4内核 `new-complete-batch` rc1/stdout0：`UNSUPPORTED_UPDATE_TAIL_MARKER`。真实62:6686:76 change8逆向URP为ncol1/nnew0/size-9、marker0、单空字段；change4正向URP为ncol2/nnew1/size+9、唯一物理列1和8字节值。raw字段与native一致，不从SQL生成事件。

新增支持只接受已观察三列/stored1/空映射/marker0/负delta形态；same-record完整UBA、XID、位置配对要求单列增长、相邻extent、对应VALUE及正负长度精确相抵。物理patch仍全ABSENT，不直接猜SQL NULL；完整重放核对既有before extent。9类错误marker/delta/extent/xtype/map/UBA/列数反例拒绝。原有显式尾标记保留。

同冻结输入新kernel输出三完整事件，两个回滚零业务事件。native四完整图11原始/4补偿/7保留零差异；三次完整中间像与native和逐步SQL零差异，NULL/隐藏NOTE保留，最终只剩seed。typed投影通过；wrong-value/missing-delete比较器反例均rc1。

首轮collector因旧remote目录存在，在READY及DML之前terminal，原文/原driver已保全；使用新remote成功，不复执行源事务。旧解析失败、所有比较器输出和rc保留。271测试源树是构建后留存，不冒充构建前pin；两真实重放driver的制品和source hash执行前记录。

## 验收边界与接续

[197文件双次SHA清单](evidence/DELIVERY-v2-implicit-tail-20261006.json)仅覆盖冻结源实验、失败和新kernel重放；不包含秘密文件或live产品日志。大型capture/archive/dump/jar留lab/out/v2-dml-capture-20261006，不入Git。

旧产品helper ork-oracle-v2-dml-20261006实际仍running、装载444内核；本轮没有approved binding、目标写入或CP/ACK，不用旧737项证明新672候选产品通过。新目标前缀V2DML尚待建表前只读核验；不动旧目标/源表。独立评审PENDING，无现场/发布验收。

接续[原V2完整产品派单](DISPATCH_W5_V2_NEW_RAW_RUNTIME_20261006.md)：先补本轮真实基线独立ASOF；新私有672内核接isolated产品完整提交树，全reactor构建验证、pin新application，替换仅owned helper并恢复原acquisition；同seq62—64真正approved绑定实际HTTP→三事件target commit→control checkpoint→ACK、重复/重启独立核验。目标ID沿现有runtime为oracle-dml-V2DML-20261005，不伪改已登记身份。

随后新独立historical fixture V2→DDL/sparse raw→marker擦除DERIVED的产品目标前拒绝；原COL$间接链/活跃跨窗/正式C++采集/租约/独立目标/扩展矩阵/压力断电/独立评审/现场发布仍未完成，goal active，automation保留。
