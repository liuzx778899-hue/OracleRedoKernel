# W7 capture→完整物理事务→基线完整行

输入适配8cc8e21、最终生产b712d56、独立lab b3490b8。最终jar SHA256 `7d4345f1f79c7b6eeb75a9dc1702308a0162f72faa4d4cbf6c3987d77b65d1fc`，Java17 clean verify 240测试/失败0/错误0/跳过0。全流程仍未完成，public events仍关闭。

## 实现

`RedoBatch`使原undo/补偿/对象发现/叶位置/物理图/基线重放路径读取同一种有界输入。既有归档API签名保留，仍在各遍历前后重扫真实文件SHA；capture读取`CaptureJournal.Verified`持有的私有冻结字节，每次重新解析，避免既有可变vector field数组污染后续图遍历。新增`ArchivedPhysicalTransactions.readCaptured`与`ArchivedHeapRows.readCaptured`，前者无caller XID清单，后者仍要求实际baseline及可信获取SHA。

复用原完整门禁，没有省略begin/end、original/compensation/live、控制、typed世代、UBA、extent、before或SCN覆盖。capture的`sources.sha256/bytes`明确对应journal envelope；blocks及source/first-next SCN来自原始关闭头。`ArchivedRedo.Header`沿用作为共用关闭源元数据，不说明capture已变成归档。没有生成中间归档或改写online头。

初版238测试通过，但实际capture physical被seq41 inverse short packed marker3拒绝。真实字节17 bytes首字节0x10，native确认16-byte before capture-follow-a与after capture-follow-b。最终只新增XA/ncol3/dictionary3/physical column1/16-byte这一实测short marker3 profile；XR、其它列、未知短长度仍拒绝，原extended marker3保持。两项真实field测试与坏长度/列/XR反例加入最终240测试。不宣称generic KDOM2短值支持。

## 真实有界结果

只复用已经执行的seq40/41/42 journal、native dump、三步SQL及实际baseline2366727（获取SHA646ed950…），本轮无源DML、dump、切换或配置动作。

| XID | 原始/取消/live | 完整变化 |
|---|---|---|
| 10.2.1680 | 1/0/1 | 40:14744:16:4 blocking-closed→capture-follow-a |
| 2.22.596 | 1/0/1 | 41:2:16:4 capture-follow-a→capture-follow-b |
| 10.16.1679 | 1/0/1 | 42:2:16:4 capture-follow-b→blocking-closed |

完整三个自主发现物理图3/0/3与typed native所有位置/UBA/生命周期零差异。三UPDATE的完整before/after均保留ID NUMBER c10c和NOTE new-note，独立native逆向值/原始值与三个实际提交后SQL快照零差异；最终完整一行与最后SQL一致。独立比较器明确只用于owned ID11/三物理列/三个URP实验，不拿实验SQL作生产输入。

同制品capture与archive的physical、rows、state stdout逐字节一致。为了核验共用输入改动，旧真实seq37—39 LKR/失败全回滚以及seq32 indexed partial rollback各图/完整行只读回归一次，stdout与旧已通过输出完全一致；不重验旧源动作，也不当新支持矩阵。

## 拒绝与保全

六项capture graph/row反例均rc1/stdout0：缺sequence、乱序、重复sequence、错误baseline receipt、第三笔晚before破坏、第三笔begin control 5.2→5.0。两原始语义副本均重封raw checksum/frame/payload/receipt，不能只靠坏CRC拒绝；late before仍physical rc0/3完整图，但rows指定ROW_REPLAY_BEFORE_VALUE_MISMATCH，前两笔合法变化没有泄漏。missing begin在完整窗口门禁拒绝。

四个独立行比较器反例（漏末行、顺序倒置、错误after、错误完整state）均rc1。events对真实41仍DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED rc1/stdout0。

保留初版capture physical UNSUPPORTED_KDOM2_VALUE_LENGTH及238构建/制品/源码zip。第二轮复用相同原始输入，只修已证字节profile；没有重放DML。反例驱动最初将baseline拒绝原因字符串误写，产品实际SNAPSHOT_ACQUISITION_DIGEST_MISMATCH rc1/stdout0；驱动停在断言，失败说明和原命令/输出保留。明确continuation核旧原文后跳过已执行四项，只推进未执行项。

[双次SHA清单](evidence/DELIVERY-capture-rows-20261005.json)关联两轮源码/制品/测试、真实journal/获取回执/native/SQL、输出及全部拒绝。raw与大型制品留lab/out。

## 下一步

有限关闭代际capture→typed完整事务图→可信获取基线下三次完整UPDATE集成通过。实时未关闭record、跨组collector重启、跨批活跃事务持久化、消费位置/目标提交/ACK、StreamHouse接入、历史schema及生产基线信任交接、完整类型/Windows/ASM/NOARCHIVELOG/现场/发布未通过。

接续三个独立位置：capture已持久边界、解析可重放消费边界、目标提交后ACK。先建立有界崩溃/重启的失败与重放证据，再对接W6。不要把上述三UPDATE或结构/图完整当作整体CDC已开放。
