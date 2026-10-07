# 自主I_COL3物理split闭包候选

最终r3从实际输入字节发现维护XID，不用手工XID/RBA清单做生产选择。内核完整309/0失败/0错误/0跳过，SHA256 `1d5f91fcac6a1e3f184f5749e393b258585ae6f9741e6bce6c1acf2440afb984`。旧119个production class字节不变，仅新增自主reader/Context/私有构造Closed三class，内核无数据库/Flink/Spring依赖。

同原实际产品V3 SCN2566695/SHA323fd35a和冻结70/71：自主发现1个有限19.3 profile，五original、两个undo-block advance、非事务freelist控制、commit同记录OLD重建闭合。Archive/capture输出完全一致，重复一致；capture_complete仍不是本轮声明。

| 位置 | 原始证据处置 |
|---|---|
| 70:126592:380 | 10.6 LOCK / 同记录begin与10.21 undo |
| 70:126593:240 | 10.9当前R ITL / 8032字节前像 |
| 70:126610:188 | 13.7可信segment freelist + 13.6邻叶lock-clear |
| 70:126610:328 | 10.8 NEW / 同记录empty-leaf inverse |
| 70:126619:224 | 10.11邻叶prev-link / 同记录inverse |
| 70:126619:460 | 10.15 branch child/separator / 同记录inverse |
| 70:126620:204 | 5.4 commit/tail + 10.8 OLD重建 |

事务身份XID9.9.660、beginSCN2567426/end2567427，五original/live、tail02402f51.e6.3。13.7/13.6是物理控制关联，不伪造为该XID的undo；ccnt47十六进制保持opaque，不当XID。其可信segment004001c0、file/类别/字段形态、head0040512f与NEW、neighbor0040512e与既有链接相互绑定，未知profile拒绝。

OLD/NEW/neighbor/branch互指及range校验、全部行守恒通过。前像445→NEW220+OLD226，无丢行，仅新增0101删除标记行，OLD slot7/key75185/internal4/rowid004089a4:51；OLD prev仍未知=null。已完成独立891行/native字节、NUMBER、rowid、状态核验的旧证据复核不变。所有已知受影响页在完整维护窗口另做全输入审计，未分类写入拒绝。

## 安全、失败与边界

- 最终12个合法checksum原始派生输入：Oracle块scanner rc0，自主完整split入口均rc1/stdout0。包括wrong link/child/separator/current R XID/before rowid/generation/directory、缺before forward/end rebuild、freelist head/clear/control。是此物理gate的有限反例，不当产品HTTP或全部DML安全矩阵通过。
- 首候选与九负例曾通过，但审查发现缺受影响页审计；不接受它为完整闭包。r2添加审计后准确拒绝13.7/13.6未知控制，rc1/stdout0；再补typed控制形成r3。所有jar、报告、正/负输出及拒绝原文保留。
- 验证驱动首次classpath重复导致ClassNotFoundException、8380字节前像记录的opcode定位范围仅200字节、手工field table offset28误用的struct.error均记录/保全；前两实际命令/冻结驱动在capsule，第三工具原文与处置说明保留。无源重放。
- [549项双SHA与写后核验清单](evidence/DELIVERY-autonomous-dictionary-split-20261006.json)，前129+133+139+130+205+407逐项SHA/大小不变。只提交安全源码/脚本/摘要，不上传raw/大型制品/secret。
- 本轮0源动作/配置/服务重启/目标/产品构建换装。**业务历史工厂尚未接入；没有两业务事件、历史产品HTTP或CPACK验收。** 原完整goal active，评审/独立目标/现场/发布仍待证，Codex最后查。

## 下一执行包：恢复依赖与完整历史入口

P0同冻结70/71/早V3，在新输出目录继续；不能重复源fixture、晚获取回填或覆盖549项。禁止Luna/子代理，共享StreamHouse只读。

1. 用本自主Closed证明主字典事务70:126628:476的10.5/10.22恢复：完整current XID/UBA、segment/block/ITL/slot7/size18、inverse key与slot raw key/rowid必须一致。从commit到恢复的同页任何未分类或会改变slot的写入必须拒绝；不可从前像原slot7或其他索引/SQL/dump补rowid。
2. 保留物理RESTORE类型，再在可信字典生命周期中明确其逻辑索引INSERT；只有SYS I_COL3且持完整Closed凭证路径允许，普通业务index入口不放宽。主事务必须完整begin/original/end/三索引与COL$行状态一致。
3. 历史自主发现/回放以完整Closed处置五物理original和两独立控制记录，不能直接跳过未知maintenance或伪造逻辑SYS commit。严格绑定同baseline/input来源、对象/页世代与原位置，不接受来自其他窗口的Closed。
4. 同实际V3完整70/71历史入口放行两完整UPDATE/schema4→3，native/阶段SQL独立核hidden NOTE、完整像、SCN/XID/位置；archive/capture/擦marker一致。错恢复key/rowid/slot/UBA/身份、跨窗凭证、缺恢复/维护、插入未知页写入等合法checksum完整历史入口拒绝，rc非0/stdout0。
5. 再完整事件投影→新adapter/私有product真实嵌入→历史HTTP→target原子commit→控制CPACK及重复/同件重启/错receipt零推进。继续跨窗、正式采集、独立目标、类型/故障矩阵及现场发布，不能以当前物理通过替代。

六状态：代码交付=自主物理split候选；针对性验证=309与真实七记录/12反例通过；集成验证=完整业务历史/产品链未通过；部署=未换装；现场验收=未通过；发布=未开始。
