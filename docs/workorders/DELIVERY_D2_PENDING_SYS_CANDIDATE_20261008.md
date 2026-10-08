# D2 pending SYS 字典恢复候选

代码 **de4344a4f269beff8b5ee501a9aa804ab14d62b4**，分支 codex/d2-normal-index-restore。完整内核测试 **337/0/0/0**；完整提交树独立打包 kernel SHA256 **174f935533a4a27c4340cea9bf7fd3472df0b599590221a4c285bf30f5be9c7d**。打包跳过测试，测试结果来自此前同提交内容的完整回归；不把打包成功算再次测试。

## 行为

此前 SYS 字典读取强制完整事务结束，恢复显式拒绝跨 pending SYS。现在前缀入口只记录已核原始覆盖的活跃 SYS 身份、图状态及来源凭证，计入统一等待数；未提交字典不生效，schemaAt 从其开始 SCN 起拒绝。恢复重新解析必需的原始前缀并逐字节核凭证，再用完整来源重建闭合字典。即使调用方删除 pending 行并重新核 SHA，也不能绕过原始字节权威。

仅 SYS、无业务事务的 pending 前缀可生成等待凭证，完成事务数0；仅字典闭合仍保持原 NO_PHYSICAL_OBJECT_TRANSACTIONS 拒绝，不包装成成功空窗。业务与未提交 SYS 重叠时仍保守拒绝未知 schema；本候选不扩大 SYS 补偿、相同 commit SCN 或未知字典布局支持。

## 证据与限定

新增三项测试使用观察到的 OBJ$ 原生向量作**合成跨成员放置**，验证等待/原始前缀恢复、重算 SHA 的状态删改拒绝及统一等待入口。不是新 Oracle 实际跨成员 SYS 验收。冻结真实68/69只读回归：旧 kernel2690e584 与本候选均rc0，完整字典/身份/原始节点/逐列值/schema输出同字节；源重放、新获取和目标操作均0。

证据 **lab/out/d2-pending-sys-checkpoint-r2-20261008**，142项双SHA全部复核，manifest SHA256 **4dd31c02d1a220ba750b0feef1181a885c9e3ffe16761f7c09a29c69540c361f**。不可追加。初始编译访问器错误、统一入口 NO_PHYSICAL_OBJECT_TRANSACTIONS 失败及封存脚本的大小写/日志追加错误保留。旧工作目录封存无效，不引用其 SEALED 为成功；最终独立快照已复核。

产品仍嵌入已证 kernel2690e584；本候选未替换产品制品或部署。独立评审、真实跨成员 SYS→产品等待/闭合→完整事件→目标CPACK仍待证，PR1仍draft。root STATUS保留移交快照。

## 下一包

Docker 已由用户恢复；D3原传输会话尾部已完成，另见产品反馈。D2下一先以专属实验容器生成一次真实跨成员 SYS 状态，取先行可信历史 baseline，冻结原始成员，避免晚SCN回填；修真实差异后再集成产品。初始水位/一般事务同key、相同commit SCN/跨块及D3常驻采集、D4类型LOB、D5RAC/ASM/TDE、独立目标与评审/约定部署/用户生产验证/现场发布完整保留。
