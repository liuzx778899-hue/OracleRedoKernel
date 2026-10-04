# W4 三批双上下文完整物理图交付

seq22—24此前只认证A单位置和B单图，不能证明外国marker分发后A图闭合。本轮有限消费者按同一流程完整处理A/B，两上下文独立核物理match、head、清单覆盖及结束；六笔新完整图与独立native完整XID分类/有序重建零差异。仍不是通用生产事务分发或逻辑DML支持。

## 来源与制品

- lab完整树 `d907834c6b61ae416ff22d020b3f746bd636b3b8`，增加明确冻结fixture22/23/24的SHA/XID/对象/叶配置，以及A侧三项派生反例。固定fixture配置只在lab，不进入内核。
- 与192测试内核完整树 `21cd368f78709b393b8df0f79715c0031cd1c967` 的main/test/pom差异为空；复用同件jar，不重复Maven/已通过seq21整批验证。
- jar SHA256 `0642bb7ffd8691dab868b780ea73966659c9b418ff78f6b84b7e0f3858b48500`，Java17编译新lab消费者rc0。[清单](evidence/DELIVERY-paired-matrix-20261004.json)1023文件双次SHA一致，含完整lab树、既有内核树/制品/测试报告、实际支持类、输入、命令rc与输出。
- 无数据库动作；冻结seq22原始SHA b1fcd87a…、seq23 da6a0d7c…、seq24 def82f0b…。归档/dump/SQL truth仍仅本地lab/out；生产图输入仅raw归档。

## 完整图结果

| 冻结源 | A完整XID与图 | B完整XID与图 | A窗口关联 |
| --- | --- | --- | --- |
| seq22 较大键70 INSERT/PURGE | 10.3.1236：2原始/2取消/0保留 | 7.11.587：2/2/0 | 4项，2外国项 |
| seq23 两键20/30 INSERT/PURGE | 10.20.1240：2/2/0 | 9.30.584：4/4/0 | 6项，4外国项 |
| seq24 普通DELETE10/RESTORE | 10.12.1212：2/2/0 | 9.5.585：2/2/0 | 4项，2外国项 |

三批关联与六图分别native零差异，新增完整图合计14原始/14取消/0保留，不重复累加既有B单图计数。source/完整XID/原始UBA、节点/取消RBA/vector及有序head闭合独立核对；不以最终SQL相等或静态位置match代替。

消费者完整消费A窗口内14 marker归属，其中8外国项先路由到B。关闭A前把已处理的外国marker纳入A保守窗口覆盖；各图只取消自己完整身份的原始节点。各自生命周期、原始清单、物理match、active head和结束闭合分别验证。输出缓冲到整批完成，B先关闭不意味着整批成功。

## 反例和拒绝

- seq23七项B侧反例：漏节点/漏取消/重复取消/错物理slot/错marker字节/错owner/漏外国查询均rc1、stdout0。
- seq23三项A侧反例：漏A index节点/漏A index取消/重复A index取消均rc1、stdout0。错误原文显示B已关闭4/4/0，随后A在head拒绝；缓冲B输出没有泄漏。这是本批新增的整批拒绝证据。
- fixture24参数搭配seq23来源，SHA绑定拒绝ROUTED_FROZEN_SOURCE_MISMATCH、rc1/stdout0；属于有限实验来源防混，不声称生产字典保护。
- 三native图比较器派生反例（漏第二次取消/重排取消/错原始UBA）检出3/2/1差异、rc1；只认证研究验证器能检出，不计生产安全测试。
- events仍rc1、stdout0、DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED，CDC发布关闭。

本批无构建/功能验证失败，所有预期失败均保留stderr、输入摘要及rc；既有失败不删除。192测试、其它回滚图、六批长heap、捕获观察证据按原交付保留，未重复运行或合并成全流程通过。

## 下一门禁

按[内核分发执行派单](DISPATCH_W4_ARCHIVED_ROUTER_20261004.md)收敛为自主发现完整XID的内核入口，避免预知A/B、fixture常量或SQL/dump参与生产路由。重点核未知对象/控制、遗漏整笔事务/marker、窗口不完整、失败锁定与完整源覆盖；物理对象参数不当历史SCN字典证明。该派单尚未实现/验收。

四批双上下文冻结物理验证（既有seq21加本批seq22—24）已经形成证据；仍缺通用生产分发、其它控制/页维护/恢复、W5字典/完整行、持续capture/续接/ACK、StreamHouse真实集成、现场与发布，目标active。
