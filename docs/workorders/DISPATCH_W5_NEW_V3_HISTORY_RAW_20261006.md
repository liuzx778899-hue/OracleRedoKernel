# W5 新 V3 后真实历史 raw 正功能

接续 a3fb674/kernel58a6e56e/295 的归档自主 SYS history 候选。原目标不缩减；Codex 最后查，无 Luna、无子代理。

1. 先核 owned source 标签/完整身份、NO/NO、旧表与已冻结 fixture 的现状；确认没有同 checkout/source 的在跑执行。新证据目录独立，源动作一次性锁文件先落盘。
2. 使用 e095822b adapter 与新内核对专属 fixture 做实际当前 SCN V3 获取；三 SYS 角色/layout/完整行与三 index 身份/DBA、业务行全部独立核验并关联 trusted acquisition receipt。获取必须先于新日志中的目标 schema/DML，不准使用旧 SCN synthetic 文件。选定当前 sequence 连续覆盖 baseline SCN；覆盖不足保留失败，不能晚回填 authority。
3. 只在 `oraredo-19c-lab-20261004` 新受控 ADD/DROP 和业务 UPDATE/commit；保留完整 native dump、关闭采集、archive/capture SHA/差异、XID/commitSCN/真值。既有冻结 seq66/67 和此次结束后的源动作一律不重放。
4. 新 V3 + 新原始 capture 驱动历史 factory，要求自主完整 original/control/row/index 覆盖、每个提交完整值、真实 schemaAt，并与独立 ASOF 核验。擦 marker 仍可还原完整历史；缺 row/index/control/parent/逆向值、generation/XID/SCN 等合法 checksum 反例拒绝，事件/目标/CP/ACK 不推进。SYS rollback/跨基线/其它不支持 profile 明确拒绝，不能当正功能。
5. 在上述真实可信历史正功能成立后，才实现按事件时点 schema 的完整业务 before/after；新增列 NULL 与 DROP 后完整像必须独立验证，不用末态相等代替事件语义。旧 V2 继续历史拒绝。
6. 随后实现产品 V3 获取账/回执/持久恢复和实际 HTTP、目标原子提交→control checkpoint→ACK；仅私有制品，不换装共享产品，不将737旧结果套新制品。

交付：代码/制品 SHA、针对性和必要影响检查、输入/输出 SHA/rc/事件数、全部失败原文、新 STATUS/handoff、独立评审门禁。大样本/secret 留 lab/out，不上传。原全目标的跨窗、采集、独立目标、完整类型环境压力断电矩阵和现场发布仍是后续验收。
