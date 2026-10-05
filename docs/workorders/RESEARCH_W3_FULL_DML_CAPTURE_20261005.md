# W3 新 INSERT/DELETE capture：基线前结束向量缺尾引用

本轮是失败正样本与边界定位，完整 DML 未通过。生产源码与运行时 ec3b430 不改；已有 245/15 测试、目标消费与强杀轮次不重跑。证据目录 `lab/out/full-dml-capture-20261005`，输入、制品、输出及失败原文见[双次 SHA 清单](evidence/RESEARCH-full-dml-capture-20261005.json)。

只在专属 `oraredo-19c-lab-20261004` 创建新表 `ORKLAB.T_DML_CAPTURE_20261005`：NUMBER ID、VARCHAR2 TXT、invisible VARCHAR2 NOTE，seed ID0/dml-seed/anchor。独立获取完整基线 SCN2421228，snapshot SHA d88ee8211dd7c1cc65cb17b9983e7a86d18179939b9f8ffc4f2b3d3e53f5a3cd；heap logical/data73252、index logical/data73253。源 supplemental NO/NO；实验 fixture DDL 不能当生产零侵入证明。

基线后 INSERT ID11/insert-visible/insert-note（XID10.6.1790）、DELETE 同一完整值（XID10.19.1789）各执行并提交一次，各切换/归档一次。关闭 collector rc0：seq44共225507块、seq45共9块，next46；两 journal 与原始归档数据块零差异。seq44 SHA a7da3920c57981e00ed693f3b4fb44cce536240834f35c4b276dd8f1b095adcb，seq45 SHA 3ab7b526c15fb0970559f98e0c676c90a615049dffb6fe73d095c086a9d9b56f。原始同 record index/undo 配对唯一发现 leaf50333091（0x030005a3），未用 dump 提供 leaf 或事件输入。

完整 CapturedHeapBatch rc1、stdout0、事件0，失败原文 `TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE`。不能算 INSERT/DELETE capture 支持通过。原始对象发现及结束向量研究定位：

| 事务 | end RBA | end record SCN | flags/fields | tail |
|---|---|---|---|---|
| 基线前 seed10.7.1787 | 44:225482:132 | 2421224 | 0/2 | 无引用，通用图拒绝 |
| INSERT10.6.1790 | 44:225502:16 | 2421252 | 2/3 | 37760291:264:17 |
| DELETE10.19.1789 | 45:4:148 | 2421268 | 2/3 | 37760291:264:19 |

独立完整 Oracle dump 中抽取上述三个 record；可执行比较器核 RBA、record SCN、slot、sequence、flags、tail UBA 或其缺失，零差异。三个 native SCN/flags/tail 篡改反例均 rc1 且明确检出对应位置。此比较不认证完整 DML，不把 end record SCN 宣称为已认证 commit SCN，也不从 ktucm 中补造 USN。原 `proof.json` 的 native pending 是初次失败时点；后续结论追加到 `end-research-proof.json` 和比较日志，保留历史原文。

最终新表仅 seed0/dml-seed/anchor，旧 fixture 仍11/blocking-closed/new-note。本定位与比较只读，未重放已提交 DML。首次错误 Python launcher 路径发生在 driver 执行之前；正确路径运行 driver rc0，内层 parser rc1 已保存。原始样本/dump 不入 Git，口令不上传。

下一步按[可信基线物理边界修复门禁](DISPATCH_W3_BASELINE_PHYSICAL_SCOPE_20261005.md)，将已覆盖旧事务与基线后待重放事务的职责明确，不能捕获异常后继续、删掉 seed bytes、从 SQL/dump 补尾或无依据取最后 UBA。通用完整图无 tail 拒绝、跨基线拒绝、补偿外来归属及输入覆盖必须保留。完整 DML、公开 decoder、产品 pipeline、部署、现场、发布仍未通过。
