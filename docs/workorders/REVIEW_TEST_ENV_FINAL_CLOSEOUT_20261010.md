# D1–D4 已实现能力测试环境最终验收 · 2026-10-10

结论：**已验收（本轮已实现能力、有限测试环境）**。中央可信恢复切点、安全回收、正常多窗口接续及集中只读评审均已收口。本轮没有再运行源DML、归档、实库实验、构建或旧测试。完整D1–D4一般能力和生产现场/发布仍有明确未验范围，不把本结论升级为GA。

## 集中处置

| 已实现能力 | 复用的真实结果 | 本轮处置 |
|---|---|---|
| D1持久恢复/发布，D3 producer接续 | 原44＋2事件；target/member/commit 46/46/2，完整像零差异，实际CPACK核等；第二窗pending0 | 有限测试环境验收通过 |
| D2历史字典/SYS跨窗、正常跨块 | 原84/85产品链及90/91的40事件产品链；target40/40/1、完整像核等 | 原证据复用，有限验收通过 |
| D2初始水位/活跃同key | 86/87三事件、target3/3/1；88/89五事件、target5/5/1；原SQL/实际DONE任务CPHEX与ACK核等 | 有限验收通过 |
| D3 C++普通成员获取/传输/背压 | 原100/101各1事件；220MiB满→交接→释放→自动续采，最终源队列0 | 有限验收通过；一次容量闭环同PID，整轮两段PID |
| D3中央切点/前缀回收/恢复 | 原100回收200MiB后两未来窗各1事件；最终保留103，回收批准101/102共419431424字节，恢复/毒化拒绝通过 | 本轮最终独立评审PASS |
| D4 RAW、DATE、无时区TIMESTAMP | 原各4事件产品链；正式HTTP获取与同SCN独立SQL核等 | 有限验收通过；新获取不批准旧raw，不拼成新基线后发布场景 |
| D4 BasicFile/SecureFile LOB | 可信HTTP基线后各1完整事件，target1/1/1、完整值零差异，实际CPACK核等，旧16事件发布0 | 有限静态schema验收通过；两个运行件分别保留 |

不同包的运行候选分别保留，不合计事件数冒充一个统一制品的全覆盖。既有包完整输入/制品SHA、rc、事件数、失败原文均复用原交付记录。本轮对已通过能力不追加部署或故障矩阵。

## 最终候选与终态独立评审

内核e3ac633，坐标 `0.1.0-readcut-53fa9a98275d`，SHA256 `53fa9a98275d22f8cadae86a7ac47d59c88450e158c46488c3eb823d2d2d01a6`。
产品代码 `7eaca10f2899dadb59fda3bf635edc5f5c63e3da`，app-r4 SHA256 `6b26d0852e15b6b6227f43b734f3c437911f971d2f38e5a507648aa648e5f32f`。
两个未来窗实际运行37ee409/app-r3；最终app-r4执行下一切点/回收/恢复/拒绝核验，没有重放两未来窗。适配器/C++沿用各原候选。

最终只读评审代理 `/root/final_evidence_review` 核对汇总与原stdout/stderr/rc，独立重算application及嵌入kernel SHA，结论有限PASS/无阻塞：

- 切点102319102395412480、SCN3160456、source `oracle-online-normal`；raw派生snapshot SHA256 `91fb4cdabdee1b03f262c3f781131701a662505fdf0d97dfde773c4f076cb163`，保留首序列103。
- 回收响应删除2个/419431424字节；原header清单对应批准101/102，当前两文件不存在，其它raw保留。中央目录36→34、7549765632→7130334208字节。
- candidate与restart是不同容器ID，先stop/remove再启动；新进程retention为103/103。四阶段SQL均rc0、COUNTS|4|4|4|4、ROWS|20。
- snapshot毒化业务409/`ORACLE_READ_CUT_RECLAIM_REFUSED`；执行脚本对零删除、原字节恢复、SHA复原、retention完全一致和ACK名称/SHA集合不变均断言，final-closeout.rc0；当前snapshot SHA独立核等。新增源DML0、事件0。
- helper stop/remove、自有库stop原日志均rc0；本窗口只读确认专属库exited、固定helper不存在，数据库与卷保留。

证据限度：毒化前后manifest/ACK集合没有另存两套清单，采用已保存执行脚本断言与整体rc0；ROWS|20为SQL定义的ID/TXT/NOTE匹配，不以它单独替代完整事件/前后像证据。启动Connection refused及SLF4J重复provider警告保留，后续health成功，无本轮阻塞。

## 独立评审及证据入口

原代码评审会话 `01a12432-779c-75b2-b67f-dccfcc5cd9f8` 发现P2“已消费不等于已封存”；7eaca10删除前逐项核验SEALED前驱链，所有受影响历史窗口必须在该链。原独立修复复核通过，相关16测试rc0，P2关闭。

该会话集中证据复核在额度中断前，已实际核对D1、D2初始/同key、D4 RAW/时间/两LOB、背压、两未来窗的原SQL、实际CPHEX/ACK与producer；其最后一轮状态为failed（额度），**不写成整轮评审成功**。复用已产出的逐项核对结果，最终终态缺项由本窗口只读代理补齐；集中裁定由本收尾单给出。

内核仓受控本地证据入口（不入Git）：

- `lab/out/read-cut-20261010/final-closeout-proof.json`、`final-cut-r1.executed.py`、`final-cut-r1-*.stdout/stderr/rc`、`candidate-r4.json`；两未来窗proof及windows-r5/r10原CPHEX/ACK。
- D1/D2/D4原证据按[已有能力盘点](DELIVERY_D1_D4_CLOSEOUT_20261010.md)、[D2/D4尾项](DELIVERY_D2_D4_TAILS_20261010.md)、[LOB产品链](DELIVERY_D4_LOB_PRODUCT_20261010.md)定位；背压按[原反馈](DELIVERY_D3_RETENTION_20261010.md)定位。
- 旧失败与两份继承未跟踪HANDOFF保留；样本、秘密配置、大制品不上载。SQL/dump/LogMiner仅作验证尺子，未作为事件生产输入。

## 状态与后续边界

| 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|
| 通过 | 通过（原针对性证据复用） | 通过（上述有限正常链） | 通过（测试环境换装）；生产待证 | 待证（生产现场） | 未开始 |

测试环境验收：通过；独立代码及本轮最终终态评审：通过。本派单收尾已完成，后续不再自动重复旧窗口/实库实验。

未批准OPEN仍占约7.13GB，不能宣称总存储有界；长期在线平台/容量治理、ARRAY/同commitSCN一般样本、其它未证类型/时区/BC/精度、一般LOB历史DDL等另列。中央ReadCut仅当前正常静态scalar schema及可证明闭合上下文，不扩为LOB回收支持。生产零侵入、Windows实部署、生产现场与GA不能由自有实验配置/换装替代，后续按明确授权安排。

D5不启动；TDE [Issue #2](https://github.com/liuzx778899-hue/OracleRedoKernel/issues/2)搁置，不作第一版前置。RAC/ASM后续复用既有解析器、只补读取/thread接入缺口，未宣称支持。不新建自动接续或长期观察任务。
