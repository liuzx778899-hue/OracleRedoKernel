# W6 空侧契约与实际产品 gate 验证

范围：冻结seq44—50，源DML不重放；共享StreamHouse checkout只读。当前公开gate失败与私有目标闭环分别记账，完整产品/部署/现场/发布仍未验收。

## 变更及判据

OracleBatchProjection将INSERT不存在的before、DELETE不存在的after改为null；存在侧仍包含所有可信列，SQL NULL保留为map中真实null。目标writer仅在生成SQL和原有审计编码时兼容null侧，未改变事件身份、payload摘要、batch摘要或位置。针对性adapter测试16项通过；内核复用96095420…，已通过254项测试不重跑。新adapter c98b1a37…。

`lab/verify_oracle_null_sides.py`将接受过的目标driver和独立raw比较器仅重命名至新专属SNON/SNUL/SMIX/SFAIL范围，实际执行文本及来源SHA落盘。三个真实输入完整投影输出与旧投影逐字节相同；空侧探针检查INSERT/DELETE及UPDATE两侧，NULL批次两个事件各保留两个SQL NULL列。

四新专属目标20表：非空/NULL新增各1+1，混合3+0+0，所有前缀重复0且checkpoint原字节稳定。SFAIL混合UPDATE受自有CHECK约束拒绝，整个事务0审计/0成员/0凭证且无checkpoint/ACK；移除自有约束恢复3，再重复0。独立raw→SQL完整像、成员、凭证、checkpoint全scope/位置/ACK核验及三个比较反例由既有比较器执行；旧目标未消费，源快照前后相同。

## 产品门禁

实际PublishGateService源码从共享HEAD67bfb775…在隔离目录编译；来源SHA62e96b4b…、编译class SHA16b52a3d…，依赖boot制品SHA另记。真实非空/NULL/混合三组batch均先记录EVENT_ID_INVALID、SOURCE_INCOMPLETE，再因null legacy LSN的List.of抛NullPointerException，rc1/stdout0。新adapter也必须保持实际gate拒绝；不能以空侧修复代替Oracle身份、可信源登记和类型化redo位置验证。无产品部署、无public decoder开放。

证据入口：[双SHA及命令清单](evidence/DELIVERY-oracle-null-sides-20261005.json)；大型输入、实际SQL/输出/失败原文/执行脚本在忽略目录lab/out/oracle-null-sides-target-20261005；gate隔离目录oracle-product-gate-20261005；新制品目录release-oracle-null-sides-20261005。独立比较指与raw/native/SQL来源分离的算法，并不代替独立人员审阅。

下一步按[产品契约执行包](DISPATCH_W6_PRODUCT_GATE_CONTRACT_20261005.md)第2、4、5项推进：可信Oracle source binding和类型化事务/redo位置、生产注册及完整产品pipeline；C++真实采集、历史字典/跨窗口活动事务与现场发布继续待证。不得伪造v2 ID、SQL LSN或source字符串换取gate通过。
