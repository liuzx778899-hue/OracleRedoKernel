# W5 历史完整事件逐schema投影候选

代码 `fd0a254`，adapter SHA256 `446e5a9b42c9324f5242df72e255cda0acf36f8179628af80dce7f41415e7778`，24测试/0失败错误跳过；引用冻结历史kernel `3ccca289e717f7fd8f188af30c8625baea2577640fbac7b2ff1c3ae384346e55`，本轮不重构该内核。[封存清单](evidence/DELIVERY-historical-projection-20261006.json)75项双SHA并复核全部匹配，旧189项未变；候选 `lab/out/historical-product-candidate-20261006`。

## 实际功能证据

实际V3/seq68—69经可信factory产生的2条历史UPDATE进入 `OracleHistoricalBatchProjection`。每个投影Entry保留自己的完整schema及原payload/原始undo位置/完整XID/提交SCN；完整before/after分别四列、三列，隐藏NOTE anchor保留，第一条EXTRA_V3显式NULL。两条changedColumns均只含TXT；legacy SQL Server LSN字段为NULL，无伪造Oracle LSN。

有序schema关联摘要 `c23904a9efaed17aaf82a68d8d88478dcf84d89804c14376b6c29aa9f077a2e4`：DataOutputStream UTF tag `ORK_ORACLE_SCHEMA_SEQUENCE_V1`、int事件数，然后逐事件UTF envelope eventId、UTF schema SHA、long endRecordSCN；事件顺序与schema关联都参与。不是单一最终schema SHA，也不是发布许可证。

原始完整事件与投影ID/payload/位置/XID/SCN/schema/完整字段逐项一致；重复投影stdout逐字节相同，擦四marker仍有相同事件及有序schema摘要，envelope captureInput随真正batch digest变化。丢新增列、wrong-schema、wrong-before三个比较反例检出；wrong-business-before/wrong-generation完整投影rc1/stdout0。旧静态V2三IUD投影的JSON所有字段、envelopes有序数组及evidence与冻结输出相同；JSON对象键顺序不当语义差异。

## 保全及边界

首次PowerShell Maven未引用点号参数导致kernel路径被截为kernel、安装拒绝；改用逐参数执行记录后成功，原失败保留。静态投影比较首轮缺Jackson依赖NoClassDefFoundError保留；补同原制品的三个Jackson jar，只执行该未通过影响项，先前已经通过的真实历史投影/重复/擦marker/反例读取已有终态记录，没有重放。

本轮0源业务/配置变更、0目标动作、0私有StreamHouse代码变更；共享checkout保持只读。新Batch私有构造，仅从可信历史factory投影；没有将它强转旧固定schema Batch 或旧SPI证据。既有目标writer、ConsumptionWindow、checkpoint/ACK仍仅接受固定schema类型，**历史投影尚未进入真实HTTP或目标协议**。StreamHouse产品仍efd068f/737，PR#13待评审，产品V3获取账尚未通过。

## 接续主线

按 [历史产品执行单](DISPATCH_W5_HISTORICAL_PRODUCT_20261006.md)继续：在私有worktree升级SPI/可信注册/获取账/发布门禁的有序逐事件schema证据，再统一窗口、目标commit凭证、控制checkpoint与ACK恢复核验；完整隐藏/新增字段不得裁剪。具体目标字段映射/历史列生命周期需显式策略，不能仅以同一批最终三列初始化目标就报告通过。新候选必要构建及真实HTTP→目标commit→控制CP→ACK/重复/重启/反例必须独立留证。活动跨窗/正式C++捕获租约/独立目标/类型环境高压断电/独立评审现场发布仍未完成。原完整goal active，所有Oracle目标任务完成后才查Codex卡顿。
