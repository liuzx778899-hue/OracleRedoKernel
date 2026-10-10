# 常规混合字段 · 产品接线反馈 2026-10-10

首包内核 1db6e50 的常规九列能力已完成新产品正常链：BasicFile/SecureFile 各一次新 HTTP 历史基线、一次不同于旧操作的 TXT UPDATE、完整原始日志投影、真实目标提交和实际 CPACK/ACK 字节核等。不是回填旧基线或重放旧源 DML。源库停止 rc0、固定 helper 移除；数据库和卷保留。

## 交付与验证

- 产品代码提交 c8382f8576428f1429234fdf97cae18c510f2bcb；完整 reactor 861 tests、0 failure/error/skip，构建 rc0。
- 固定内核 `com.streamhouse:oracle-redo-kernel:0.1.0-common-3a9ccc651b60`，SHA256 `3a9ccc651b60b7513693f55e3289c664175ab8f8d142a65b71e412ab05dbfe2a`。
- 固定适配器 `com.streamhouse:streamhouse-engine-oracle:0.1.0-common-3a9ccc651b60-87eb1e9064de`，SHA256 `87eb1e9064dee46e580853ac889453b458a9fb59a1d79cb40742c64e554938fb`；POM 固定依赖上述内核与原 SPI。
- 同件产品 jar SHA256 `a49de18c678c9027c3e2fefeb7a8ad58f48c15863f62b8e0a871d0b7b0b7e198`，两个嵌入 jar 全 SHA 核等。
- 修复主仓 Oracle 新装 V729–V731 的 8 处 DEFAULT/NOT NULL 顺序错误；实际首次新装 ORA-00907 原文保留，未执行源业务 UPDATE。只调整 Oracle 语法；MySQL/SQL Server 脚本未变。

| 存储 | HTTP 获取 ID / SCN | 新事件 / 目标 applied-member-commit-ledger | 九列差异 | 实际 ACK SHA256 |
|---|---|---|---|---|
| BasicFile | 102387839601504256 / 3193226 | 1 / 1-1-1-1 | 0 | dabc65d1583ed22064ae15d53fc20a4871fb65cbc511e5ff8e5e725af95f3ace |
| SecureFile | 102388197870567424 / 3196031 | 1 / 1-1-1-1 | 0 | 4999e412ecad7a1d7a2ce32186b41c0f3a0175fff5eb6aa83c5c66e31332b906 |

两任务 DONE、窗口 CONSUMED、批次 PUBLISHED/rc0。NUMBER/VARCHAR2/RAW/DATE/TIMESTAMP/BLOB/CLOB 九列全量匹配；BLOB 10000 bytes，普通 CLOB 5000 chars，JSON 5012 chars、XML 5007 chars，仍以字符 LOB 内容处理，不声明原生 JSON/XMLTYPE。

输入原始日志 SHA：104 `6658fce9c8f41e836e901c39954469abadf45926f1a756658f46ccd18fdd6290`；105 `5c9f93b6a92f2053e5f51f7454b10e0f91ae0824fec9a42c1e4e202c346ef282`；106 `b5584b51870d6f7da2471f12360f4a98b7efe1d9fe23c76ee5b53f7ceda00a0b`。前包大字段新写入、JDBC 独立提交核验/恢复复读及受影响回归复用，不重复。

证据：`lab/out/common-fields-20261010/product-build-r3/{candidate.json,test-package.rc,test-package.stdout}` 与 `product-runtime-r3/{proof.json,B-proof.json,S-proof.json,*-actual.stdout,source-stop.rc}`。r1 保留新装失败；r2 只读盘点误查尚未创建的表 ORA-00942 原文保留，未删除控制库或执行源业务 DML；r3 确认无绑定表/事件0后只清理本次空失败控制 schema。build-r2 构建 rc0 后日志编码错误，单独 finalize 收尾，没有重复构建。

## 下一步和边界

StreamHouse main 合并、重新编译部署清单及本地固定坐标交付继续办理。用户最新部署回执 988ee03 只新增文档，已完整保留。当前包不是现场部署或生产验收结论。

ARRAY 的 QMI/QMD 多行绑定已开始离线实现，尚未接通完整事务/索引/事件链，不标支持；LOB 历史/恢复回收、类型变体及 D5 继续按原执行单推进。长期/压力/现场验证后移模拟生产；TDE 保持搁置。

六状态：代码交付通过｜针对性验证通过｜有限正常集成通过｜部署待证｜现场验收待证｜发布未开始。完整 D1–D5 未完成。
