# CapturedHeapBatch V1：固定schema、有界capture接入接口

状态：内核API交付，有限实验验证；**不是公共CDC发布授权**。生产可信交接/历史schema/一般事务与类型矩阵仍待验收。坐标`com.streamhouse.oracle:oracle-redo-kernel:0.1.0-SNAPSHOT`，Java17，无数据库、Spring或Flink运行时依赖。制品版本/完整树及SHA须一并冻结，不能仅凭SNAPSHOT名称认定二进制相同。

## 调用与失败语义

```java
var verified = new ArrayList<CaptureJournal.Verified>();
for (var input : trustedInputs) {
    verified.add(new CaptureJournal().read(input.path(), input.acquisitionSha256()));
}
var batch = CapturedHeapBatch.read(verified, baselinePath, trustedBaselineSha256, indexLeaf);
// 此处才可取得 batch.events()；捕获、图、baseline/完整行或归属失败会抛异常。
```

可信摘要由外部受控交接提供，不从当前文件自身摘要或可编辑相邻manifest认定来源。baseline/indexLeaf仍是明确限定scope，不是历史字典认证。失败不返回半批；适配器必须转换成非零decoder rc、空events、无events产物、零目标写入/无ACK推进。不得捕获异常后消费之前遍历的部分结果。

当前实现复用完整physical与rows门禁，额外核source一致及每个live行的完整事务归属。为保持已有解析不变，physical会再次遍历；此有限batch接口没有高吞吐/实时SLO承诺。输入保持原始journal，不改online头、不造归档。

## 返回信息

- `source()`：DBID、activation、compatibility、thread、resetlogs ID/SCN、PDB UID/ID。它们是Oracle类型化身份，不冒充SQLServer GUID/recovery fork。
- `baselineSha256()/baselineScn()`：实际可信基线输入绑定；`schema()`记录heap/index owner/name/logical/data generation及全部physical column的segment/internal/name/type/nullable/hidden。
- `captures()`：按输入链顺序的原journal SHA/bytes、关闭序号/SCN范围等；journal摘要属于原始envelope，不能当作归档文件摘要。
- `events()`：不可变完整列表，包含eventId、payloadSha256、pairedUndoPosition、完整ClosedIdentity、物理row key、操作及完整raw cell before/after。INSERT的before为空、DELETE的after为空代表确实不存在；其余像必须保留NULL/VALUE，不能混成ABSENT。隐藏列仍在完整physical像中。
- `sha256()`：整个消费batch内容/输入绑定；与capture接收水位、解析offset、目标commit receipt分别记录。

`pairedUndoPosition`是配对原始5.1 undo vector的archiveSequence/block/offset/vector，**不是forward vector位置**。`ClosedIdentity.beginRecordScn/endRecordScn`是实际起止record SCN，不改名推断commit SCN；没有commit墙钟时间字段。不得用捕获时间/native dump时间/系统当前时间补造commitAt。

Schema摘要独立于baseline行值、baseline SHA/SCN，包含AL32UTF8、heap/index两角色及全部physical列属性；固定schema变更会改变该摘要。它不是历史schema时间轴证明。

eventId绑定Oracle完整source、schema摘要、pairedUndoPosition、完整XID及data-object/DBA/slot；不包含before/after或baseline行水位，便于同身份不同payload明确冲突。payload摘要绑定操作、起止record SCN及完整before/after的状态和值。batch摘要再绑定baseline摘要、schema、leaf、按序全部journal摘要及eventId/payload摘要。

三个摘要分别使用版本域`ORK_FIXED_HEAP_SCHEMA_V1`、`ORK_CAPTURED_HEAP_EVENT_V1`、`ORK_CAPTURED_HEAP_PAYLOAD_V1`，batch使用`ORK_CAPTURED_HEAP_BATCH_V1`；Java DataOutputStream大端数值及UTF8字节长度前缀字符串，不依赖Java record.toString/JSON字段顺序。调用方使用API返回的摘要；更改协议编码必须显式升版本并提供重放迁移判据。

## 产品边界

接入层独立artifact引用此jar；不将探针ROW stdout、native/SQL truth或源码复制进产品。raw cell仍须按schema调用严格Oracle scalar转换器，**整批所有值**转换成功后才构造产品事件；未知类型/字符集、非法值或required commitAt缺失应整批拒绝。

现有StreamHouse `EventEnvelope.TransactionRef`使用beginLsn/recordLsn/commitLsn名称；Oracle adapter须冻结明确的Oracle命名空间/位置契约，不能悄悄把endRecordScn解释成已证明的commitLsn。未知Source.server/databaseGuid/recoveryFork保持null，不能从DBID推导GUID。`TargetApplyResult.applied`和stream append接收ACK都不构成可恢复目标commit证明。

当前公共`events`命令仍rc1、stdout0、`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。独立连接器、真实产品支持方言的同事务target receipt、跨批恢复、部署/现场/发布仍待证。
