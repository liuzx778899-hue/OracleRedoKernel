# D4 LOB 正常功能开发反馈 · 2026-10-09

当前代码：`codex/d2-normal-index-restore` / `8b9349d2f19809cdbec4be3172bb5bd348307c9c`（前件 `328e63a`）。本工作包完成失败续接、冻结样本、LOB locator/页/填充解码与有限正向 undo 配对；完整 LOB 事件及产品链仍在开发，不判验收。

后续离线补齐：三列 LOB 专用锁/undo 入口支持实测 ITL1/2 与 rollback unlock，普通堆表入口门禁不变；新增 SecureFile 小内联 locator。全量四个 fixture 堆块共57正向配对（13 LOCK/40 UPDATE/2 INSERT/2 DELETE）与 native 完整 XID/UBA/对象零差异；9回滚行仅结构识别，未完成补偿归属或事务闭合。新增4内联值/4空LOB/4NULL与逐步SQL真值相同，原16完整值在最终class上仍零差异。最终20相关测试 rc0，失败/错误/跳过0。

最新证据：`continue-r2/heap-candidate-proof.json`、`inventory-r15/heap-comparison.json`、`inventory-r15/full-values-comparison.json`，路径前缀为 `lab/out/d4-lob-source-20261009/`。冻结开发class168个，树SHA256 `a1291a5f27ded5d5db634f5916a60afb63e854ab867b51e122578fdfeceacb87`，文件在 `continue-r2/heap-candidate/`。测试日志 `heap-final-tests-r14.log/.rc`；原 `inventory-r10` 小内联拒绝、比较器误读LWN位置及遗漏空值的失败保留。未重放源、未启动库或换装；最新只读确认库仍 exited。事件0，完整事务/可信基线/产品集成均未通过。

## 实证结果

- 专属库只读确认两表完整初值与早期证据相等；原 SQLPlus `exit failure rollback` 生效。ORA-22921 根因已实测：RPAD 返回 500 字符却请求 1000；改明确构造 1000 字符并用实际长度写入。首续接字节数假设错误在只读阶段停止，未写业务；失败保留后续接未执行步骤。
- BasicFile/SecureFile 各一次大值、插入、空 LOB、NULL、删除、局部写入、截断与回滚，16 步 SQL rc0且逐步完整值相等；14 commit / 2 rollback。未重建表、未重放旧动作或源75–96。
- 冻结原始97：SHA256 `9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995`；bounded native SHA256 `807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472`。范围 SCN3038645–3043748，原失败及续接均保留。SQL/native仅独立比较，不参与事件生产。
- BasicFile 18页/146376 payload字节，SecureFile 12填充/8144字节（含2撤销）及4页镜像/11880字节与 native 全字段/完整字节零差异。类型化 locator 与真实页范围闭合后，大值→局部修改→截断→回滚共16完整 BLOB/CLOB 值与 SQL 真值零差异；BLOB 10000字节/CLOB 5000字符大值覆盖两页。
- SecureFile 10个原始正向/undo配对按同记录完整 UBA、对象/代际、当前 XID、LOB ID和覆盖范围严格绑定；追加逆操作恢复旧 high-water，局部写逆向保留完整旧字节。native owner零差异：10.13.4260 / 10.17.4266 / 10.26.4262；后者回滚处置尚未授权。旧 ITL不是当前事务身份。
- 前件12个不同相关测试通过（4+2+3+3，rc0/失败错误跳过0）；原编译/API、page offset、比较器、测试截断及追加undo flags拒绝均保留。只重试受影响项，没有全链部署或故障矩阵重跑；本次最终20相关测试见首部。

证据入口：`lab/out/d4-lob-source-20261009/continue-r2/candidate-proof.json`（开发class树摘要/代码commit/事件0）、`source-frozen-proof.json`、`inventory-r2/page-comparison.json`、`inventory-r3/fill-comparison.json`、`inventory-r6/full-values-comparison.json`、`inventory-r8/owner-comparison.json`，以及同目录完整命令/stdout/stderr/rc与失败原文。大型字节和制品不上Git。

## 状态与下一判据

| 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|
| 进行中：LOB结构/有限owner候选 | 通过：上述有限真实样本 | 待证：LOB完整事件/目标CPACK | 未开始：本候选未换装 | 待证 | 未开始 |

专属库 stop rc0 / exited，数据库和卷保留；无helper残留。产品仍原DATE/TIMESTAMP候选，本轮目标/CPACK动作0。早期 SCN3038645仍 `diagnostic-only-not-approved-kernel-baseline`，不批准回填旧97。

唯一作者继续完成：BasicFile页→LOB index/locator→完整owner绑定；SecureFile页镜像/填充/补偿与事务闭合和可见locator绑定；在原有完整行、字典与恢复链接入 LOB 值；面向后续日志补可信早期获取，禁止晚SCN回填批准旧日志；随后集中一次正常产品投影/JDBC/目标/CPACK实证。未知或缺字节仍零事件拒绝，不把本次事件0当LOB功能支持。

完整D1–D5、独立目标/独立评审、约定部署、用户生产验证、现场验收和发布继续保留。当前总状态开发中，不自审验收，不自合并；当前入口为产品仓 `docs/workorders/STATUS.md` 首部。
