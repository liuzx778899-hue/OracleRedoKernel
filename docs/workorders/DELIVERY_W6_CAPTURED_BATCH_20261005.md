# W6 类型化capture完整批次内核接口

生产树e720013；独立lab f7303ac，与e720013的main/test/pom差异为空。新jar SHA256 `15b07ddf4da3f9537b5df8cd996add69e61e6a94b5e92d4e9c7a9c1ed780053f`；245测试，失败/错误/跳过均0。新增单个内核API及五项身份/内容/不可变/回执测试，不修改既有解析类。

## 本轮交付

`CapturedHeapBatch.read`只接原始已核capture、明确可信baseline摘要及leaf；通过完整physical/rows及位置归属后，返回不可变列表、schema/源身份、完整XID与真实起止record SCN、配对undo位置、raw完整before/after和独立event/payload/batch摘要。没有可公开构造的batch实例，不靠调用者拼接多份探针文本来绕过完整门禁。

[接口说明](../interfaces/captured-heap-batch-v1.md)明确undo与forward位置区别、record SCN与commit语义区别，以及产品SPI尚需冻结的Oracle位置映射。Schema摘要不绑baseline行水位；event身份与payload分开，避免同事件内容冲突变成新的事件。生产可信schema/一般DML授权仍未因此通过。

## 真实字节与独立判据

复用原19c seq40—42三关闭journal、baseline2366727及三提交SQL，不执行Oracle动作。新API三完整UPDATE与旧已验收raw rows逐字节相同；同一新API另一次调用全部输出相同，三个eventId/payload摘要及batch摘要稳定。

独立native比较器遍历同PDB原生dump，先从目标heap原undo完整XID与同record 5.2 start绑定segment/slot/sequence，再匹配对应5.4结束；三原undo位置与完整XID、六起止record SCN差异0。实际起止record SCN分别2366730/2366740/2366750，各笔begin/end同record；不推断墙钟commitAt。既有native完整行比较器对新API输出的三UPDATE与三原SQL提交逐列字节差异0；最终state参照复用旧已验收件，不计作新API state输出（本API只返回完整变化）。

新API六项整批拒绝均rc1/stdout0：缺序号、重排、重复、错误baseline可信摘要、最后before不符、最后begin控制缺失。使用原已封存的真实raw派生反例，只测试新API，不重跑旧capture/source轮次。独立metadata比较器对错误endRecordScn和漏事件两反例检出。新jar公共events独立仍rc1/stdout0、`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。

新API batch摘要`b6658e92d88d4a910a5a4b4870189b6ba8fa4d30873ff715f08e35dd3d15aeb8`，schema摘要`b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce`，对应输入及事件完整输出留lab/out，不上传原始样本。

[双次SHA清单](evidence/DELIVERY-captured-batch-20261005.json)关联新完整构建树/jar/245测试XML、执行源码、原journal/baseline、native和SQL独立参照、命令/rc/完整输出及反例。执行源码以LF归档和工作树逐字节核对，没有重建旧240测试制品来伪装同件。

## 接续与验收范围

本轮代码与有限新API针对性验证通过，未交付产品EngineConnector，没有产品部署/现场/发布。下一步：独立Oracle adapter引用二进制API完成严格整批scalar解值和Oracle位置映射；需要commitAt而未知的路径拒绝。随后在真实产品支持方言的专属目标落实同事务幂等/commit凭证及三位置恢复，不能继续用SQLite实验替代。

跨批活跃事务、生产可信baseline/schema交接、历史字典、一般类型/事务矩阵、C++产品agent、实时/高压复用/Windows源/ASM/RAC仍待证。长期目标继续active，公共events拒绝不解除。
