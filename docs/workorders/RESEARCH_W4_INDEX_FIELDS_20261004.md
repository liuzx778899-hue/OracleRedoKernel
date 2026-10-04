# W4 索引原始输入与字段研究交付

从既有无 supplemental 的 19.3 seq8/9 冻结样本核对 T_BASIC 主键索引。研究源码 `0880e03770be6bd2e2f9d426e2dbcace56357ed5`；生产内核仍为 `076865c`，没有数据库动作或新增配置。

## 判据与选择范围

限定既有 PDB UID677737149 / CON_ID3 / index73025、索引块0x0300008b。原始归档扫描输出字段；完整 native dump 独立选择相同 index 物理操作及 object/dataObject73025 的 5.1/5.6/5.11。按 sequence/block/offset/vector 序号核对全量位置，重复、多项、漏项均失败。此选择器是固定 fixture 研究器，不是通用索引发现或生产字典。

| 输入 | 数量 | 本次核对范围 |
|---|---:|---|
| 5.1 原始 undo | 14 | 完整 XID/UBA、metadata、leaf直接字段、key；其中2条 restore 含 keydata/bitmap |
| 10.2 insert leaf | 12 | itl/sno/row size、模式、key/keydata |
| 10.4 delete leaf | 2 | itl/sno/row size、模式、字段数；本向量没有 key，不能单靠它构造逻辑 DELETE |
| 10.3 purge leaf | 3 | itl/sno/row size、UNDO模式、字段数 |
| 5.6 补偿头 | 3 | metadata、ktuxvoff/ktuxvflg |
| 10.31 create/load index | 1 | 位置/opcode/DBA/record SCN 清单；不宣称语义解析 |

35个位置及所列字段零差异。undo leaf的 sdc 按独立 dump 对应到 offset12 的 u16；观察到32765，不以它推断事务顺序。未解释的字节保留原样。undo第一字节03/05分别与本批 purge/restore 标签一致，尚未推广成通用格式结论。

## 封存与反例

完整 Git archive 导出0880e03，Java17重新编译研究扫描器，绑定既有生产 jar。封存脚本重新运行正样本及6项派生研究反例：遗漏补偿、重复位置、key篡改、keydata篡改、undo sdc篡改、undo操作篡改；比较器均rc1并给出不匹配位置。它们证明研究对账器能发现这些错误，不是生产内核安全拒绝测试。

命令入口：

```text
IndexVectorSurvey 73025 archive-1-8.dbf archive-1-9.dbf
python compare_index_survey.py fields.txt dump-8.trc dump-9.trc
python check_index_survey_negatives.py fields.txt <new-negative-directory> dump-8.trc dump-9.trc
```

本地执行证据在 `lab/out/index-research-20261004/`、`lab/out/release-index-survey/`。输入、制品、脚本、stdout/stderr、rc与反例 SHA256 见 [研究清单](evidence/RESEARCH-index-fields-20261004.json)。Java内核未变，既有124测试不重复运行或计作本次新验证。CDC继续保持拒绝。

## 失败保全

1. 首轮扫描将5.6 metadata误按普通5.1第二字段筛选，仅输出32条；保存原始输出，修正后独立对账首轮输出，确证缺3条补偿，rc1。首次扫描器版本未单独封存，不能把最终源码SHA冒充首次源码SHA。
2. 首轮对账器将sdc误取offset18、未识别native `keydata/bitmap` 标签，出现8条差异、rc1。失败 JSON/日志留存，修正验证器后35条零差异；不是生产解析器修复。

## 接续门禁

下一步以本批14条原始index undo为入口，按完整XID、同record CON/UBA与物理操作绑定；禁止用历史KTB L中的XID/UBA代替当前undo身份。12条insert与2条delete分别验证相反叶操作及key/keydata；3条purge必须回到对应原始undo，并与heap节点共同进入有序history。维护向量和未知布局必须有明确处理，不能静默跳过后声称完整事务通过。

此批尚未验证index配对、取消归属、完整heap+index事务图、并发、恢复、字典/基线、逻辑DML、capture接入或发布。W4仍进行中，W5/W6/W7剩余门禁不因此关闭。
