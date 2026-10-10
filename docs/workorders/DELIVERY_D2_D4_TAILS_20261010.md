# D2/D4 已实现能力尾项交付

按用户授权先收尾产品接线、不过度验证。本包未改解析/获取/目标写入代码，复用D3最终完整提交候选：内核01feae8、产品99d6b1b，application SHA256 `d028ca5450bfe95bab4bcac09eafc39054b023e814f2dd32e83eeffb38ee4350`；kernel `fff0550a36d7b19c75de2072d1660b326d4bec28834ebc3aaad1fa29577d95c1`、adapter `4c619ca01918157e4172c767bdaa83b74b43168ac16527943425b358a635d0f2`。没有重复构建/全套测试/旧源DML。

| 尾项 | 本次实际证据 | 处置 |
|---|---|---|
| D2 初始水位86/87 | 原HTTP获取101660919708471296/SCN2917903经实际认证GET重新核验；3完整UPDATE、target/applied/member/commit=3/3/1，ledger3/gate1；完整前后像和最终1行零差异，producer来源2/sequence87/pending0/published3 | 有限正常产品链通过 |
| D2 活跃同key88/89 | 原HTTP获取101667102842380288/SCN2921710经实际认证GET重新核验；5完整UPDATE、target5/5/1、ledger5/gate1；完整前后像及最终2行零差异，producer来源2/sequence89/pending0/published5 | 有限正常产品链通过 |
| D4 RAW正式HTTP获取 | POST historical→持久ACQUIRED_ONLY→认证GET；新获取102223483555266560/SCN3101827、快照SHA `2020dc98d985942663d96d2dcb54dccbca6509a6df347cfe500129e588e860e5`；RAW字节与同SCN独立SQL零差异，原task1/ledger4未变 | 现有通用获取接口已支持，无新增生产接线代码 |
| D4 DATE/无时区TIMESTAMP正式HTTP获取 | 新获取102223967829606400/SCN3103835、快照SHA `9d90f704dedf7ba279a249218a3b99dc8384468b507ebb3499f0373fbb2911bd`；两个类型完整值与同SCN独立SQL零差异，原task1/ledger4未变 | 同上，正式获取通过 |

D2实际CPACK逐字节等于控制checkpoint，producer文件逐字节等于冻结解析预期；初始组ACK SHA `68d140e88d769873b55570623f82827f3ffe9f10b45e7f15d7d4b56df34e6b1b`，同key组 `8b07f7bf0e014f49d896832dd6857f9de497b812753ac52dba56ab128c0c1ab6`。原目标RPROG1仍46/46/2。两组为一次有限有界产品提交，不新增两窗恢复/故障矩阵；此前内核前缀/续读证据复用。

D4本次两次HTTP获取不产生事件，也不以晚基线批准旧95/96；原早期获取与各4事件真实JDBC/目标CPACK证据继续复用。没有新增“正式HTTP基线后的新变化→发布”专门类型场景，不把本次获取与旧批次拼成同一新端到端场景。LOB正常完整发布证据不重跑。其它类型/时区/BC/精度、ARRAY/同commitSCN真实样本仍未交付。

冻结原证据在OracleRedoKernel `lab/out/d2-initial-product-20261010`、`d2-overlap-product-20261010` 的 `normal-product-proof.json`；D4对应 `d4-raw-http-20261010`、`d4-temporal-http-20261010/http-proof.json`。所有命令stdout/stderr/rc、输入及制品留原目录，不上传大文件或秘密。初始组首次比较脚本把目标VARCHAR2的无填充URL Base64当普通字符串，实际提交已成功；原driver/actual/失败保留，修正比较器后离线核对通过，未再次发布。接续同key脚本rc0。四次helper移除/源停止rc0，最终实查专属库exited/helper不存在；源新DML0、旧DML0、源配置0。

六列：脚本/文档代码交付通过；必要冻结/结果核对通过；上述有限集成通过；约定生产部署待证；现场待证；发布未开始。独立评审/目标验收仍待证，D1–D4整体未完成。

下一包为[D3保留水位与背压](DISPATCH_D3_RETENTION_BACKPRESSURE_20261010.md)。现有恢复仍要求原raw前缀，不能按传输完成/已消费直接删文件。本单未实现释放代码或删除任何旧证据。D5未启；TDE Issue#2继续搁置。
