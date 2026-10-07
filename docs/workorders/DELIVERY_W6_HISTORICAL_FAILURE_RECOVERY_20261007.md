# W6 历史窗口失败留证与恢复交付

私有产品代码 **dbab51a**（反馈文档12ce9f9），最终产品 SHA256 **2f1b46adc7a0fa7a8d6ab7c65ec8121e5eee32989256068a603b9bee549fe878**，完整 reactor **752/0/0/0**。kernel a84db1a3 / adapter fbf1d30c实际嵌入SHA不变，SPI全部class字节不变；原311内核/26adapter轮次不重跑。没有新增源业务fixture动作、获取晚SCN或日志配置。

本轮真实实验发现：原产品Oracle发布失败只保留`ORACLE_JDBC_TARGET_APPLY_FAILED`摘要，丢失底层数据库异常。最小修复将原异常交现有文件出口，登记失败文件引用与摘要；读取最新批次后仅改变诊断字段，不用旧解码对象覆盖已提交状态，不将ACK故障的APPLY_RC标成目标失败。无新schema/迁移或事件生产输入。

## 同一输入与制品验证

新自有控制`ORKSPLCTL_20261007`、目标`ORKTGT_CMT_20261005.SHPL7`、helper37263。从原控制账复制实际早V3注册`100747510573780992`，九字段/完整metadata与原记录独立相等；同一早SCN2566695快照、原70/71及最终产品只读挂载。复制旧注册用于隔离故障实验，不冒充新产品获取或新源快照。所有实验仅owned资源，共享StreamHouse只读。

| 判据 | 实际结果 |
|---|---|
| 错consumer绑定 | 实际认证HTTP：REFUSED/rc2/event0，任务FAILED，目标0/0/0、门禁0、CP空、ACK0、窗口PENDING；guard精确还原绑定 |
| 第二UPDATE约束失败 | 同原两完整事件，新候选实际`ORA-02290 SHPL7_LATE_CK`；完整异常/原SQL/文件引用/SHA保留。目标行恢复原值、APPLIED/MEMBERS/COMMITS全0，门禁0/CP空/ACK0，任务FAILED。仅第二后像值违反约束，第二完整before匹配要求第一UPDATE先发生；这是基于实际错误和完整谓词的推论，不伪称代理实测更新次数 |
| ACK存储故障 | 只在owned ACK目录位置放普通文件。目标2/2/1、门禁1、ledger2、完整checkpoint/receipt已提交；原批`101221037735239680`仍PUBLISHED，任务FAILED，持久ACK0。`FileAlreadyExistsException`全文/批次文件引用保留，APPLY_RC仍NULL |
| 故障恢复 | 保全并移走原阻挡文件，不删除失败。任务`101217591124647936`恢复DONE；目标不重复应用，补齐ACK，与checkpoint精确字节等，SHA`aa8f7d2bbdfce3be8aaa3736c9ef435fd99cda5b53a552f808ac9386e3056629` |
| 重复/同件重启 | 运行jar SHA一致；异步完成审计增量逐次核对，DONE/windows0/published0。目标/全部批次/CP/ACK精确不变，审计正常追加 |
| 源与结束 | NO/NO、旧11/blocking-closed/new-note及原fixture0/history-product-restored/anchor不变，原注册完整metadata仍相等；初始与最终helper均停止false/143，未停源 |

完整两UPDATE目标逻辑像4→3列、事件ID/payload/有序成员及schema序列摘要241d24ac、原batch832ae6e6全部与冻结投影一致；控制checkpoint保留完整来源/两capture/baseline/事件SCN/XID/位置/schema及目标receipt。这里只证明有限窗口/自有同实例跨schema目标，capture_complete仍false，不是独立目标、正式采集、现场验收或发布通过。

## 失败保全与交付状态

原ef852候选的目标回滚状态/缺原始cause日志保留，不当完整晚失败留证通过；首回归7/1/0/0复现原异常未到sink。首修复740223e/751通过，新增ACK回归8/1/0/0证明旧GATED对象覆盖PUBLISHED，该候选保留但从未运行。最终dbab51a/752通过，实库晚失败与ACK故障完整路径均通过；两原失败XML/源码/制品保留，不缩分母、不改绿。

[证据清单](evidence/DELIVERY-split-history-late-http-20261007.json)双SHA/写后核验；原356/1181/407项保持SHA/大小不变。新capsule`lab/out/split-history-late-http-20261007`封存；secret不哈希/不上传，禁止再写封存目录。产品仓已提交并推送接口级反馈，不带研究脚本、样本或解析过程。

六状态：代码交付=该有限失败留证修复通过；针对性验证=752通过；集成验证=上述绑定/晚失败/ACK恢复/重复重启通过；生产部署=未开始；现场验收=待证；发布=未开始。PR#13依赖#12、独立评审PENDING；作者不自审通过。

下一开发主线：跨窗口活跃事务/字典状态与恢复、同key并发/同commit SCN/多savepoint/跨块与初始恢复、正式C++采集/平台/高压/故障，以及一般完整DML/类型和W8—W11。进入新输出目录，不重放原源动作或回填晚SCN。部署及生产验证按用户安排协同，原完整goal active；全Oracle目标完成后才查Codex并关闭automation。
