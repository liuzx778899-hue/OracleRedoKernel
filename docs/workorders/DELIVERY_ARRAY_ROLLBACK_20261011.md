# ARRAY 回滚首包反馈

当前目标仍为完成用户列出的五项后启动模拟测试；D5 RAC/ASM 搁置。本包只关闭真实 raw108 中的整笔 ARRAY 回滚和保存点撤销完整 ARRAY 节点，不关闭部分行补偿、跨窗口恢复或产品接线。

## 实现与验证

- 分支 `codex/d2-normal-index-restore`。新增 XR QMD 行槽解析、多键 PURGE 顺序重放及完整节点绑定；重复索引槽表示连续删除后前移，不能当作重复键。5.6 第三字段只接收本次已观察的20键清除位图，并要求20个原始键、总长度和每次删除位置全部吻合。较短列表不允许撤销整个 undo 节点。
- QMI/index ARRAY 的 KTB-C 只提供 UBA，XID 从同记录、同 UBA 的原始 undo 绑定；不从历史 ITL 猜事务。基线初始化候选扫描排除获取 SCN 后的新 ARRAY，将其交回普通事务解析。
- 独立 SQL 最终21行（seed1＋新200–219）；完整 raw → 事务 → 事件 → 适配器输出20个唯一 INSERT，完整值差异0、撤销事件0、事务序号1–20。SQL truth/native dump均未用作事件输入。
- 113项受影响回归 rc0/失败0；最后单行/LOB补偿字段门禁变更后13项相关复查 rc0/失败0。旧 raw107 正常20事件及规范化 envelope SHA保持不变。

| 制品 | SHA256 / 结果 |
|---|---|
| raw108（7784960字节） | `716ac017f4a5c6c5c034046830fc820eac089e58fbb277bc3cfecd7f70232ecb` |
| 获取基线 SCN3225957 | `52d09b935adc3e1290f60b233b9cd923c2f359bdbdeb8342d579da0937f598fd` |
| 本包候选 kernel | `5f459036888a240717d94a4d67dc1c2c9386ef719377b496e444232b94a2b280` |
| 20事件规范化 envelope | `a4eaa7992ad38585cd2b135f9751c142c3f14a27a9635375c1102fb1aa93aeed` |

冻结证据位于 `lab/out/array-rollback-20261011/`；`verify-r4/proof.json`、`projection.rc`、`normal-regression.rc` 均可复核。早期诊断及完整入口拒绝原文保留于 `verify-r1/` 和 `verify-r2/`，未覆盖。专属源与 helper 在取证后已停止/删除，本包离线开发验证未启动数据库。

复现：用Java17构建本仓候选，然后 `py -3.12 lab/array_rollback_verify.py verify-next`。该脚本只读冻结原始日志和基线，输出目录必须为新目录；依赖原正常包的适配器/Jackson验证库。禁止重新执行已完成 source DML。

## 接续与部署

ARRAY 部分行补偿、pending恢复及一般未覆盖布局继续；相同commit SCN恢复/发布、LOB历史/代际/安全回收、时间类型变体与StreamHouse正常HTTP→目标→checkpoint/ACK继续。已有commit SCN＋真实提交位置排序直接复用。

当前为内核开发候选，不要求那边重编译部署本包；完成五项、更新固定内核/适配器依赖并闭合正常产品链后，统一合并main并交付重编译部署清单。生产部署、现场验收、发布均未通过，D5不作为当前门槛。
