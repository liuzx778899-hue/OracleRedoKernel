# Oracle第一版 · 开发冻结与模拟验证入口

用户决定：本轮完成固定场景覆盖和main合并后，Oracle开发暂止；下一阶段是模拟验证。开发不追求全场景证明，不再追加实样本门、故障矩阵、D5/RAC/ASM或TDE。

版本固定为 `0.1.0-v1-rc1`，内核 `com.streamhouse.oracle:oracle-redo-kernel`，适配器 `com.streamhouse:streamhouse-engine-oracle`。正式候选从完整提交树构建；源码提交、制品SHA256、产品提交和远端main记录在本轮候选清单。构建/合并记录待实际完成后补齐，不提前记通过。

## 本轮固定场景与取舍

| 场景 | 结果与证据类型 |
| --- | --- |
| 同节点部分撤销 | 受控原生布局：同一个ARRAY UPDATE undo节点撤销中间行、保留另外两行，物理checkpoint保留逐行撤销信息，恢复后只有两行进入行回放，中间行保留原值。`PartialArrayRecoveryTest`通过；不是特殊实库partial-node样本证明 |
| 同SCN | 受控字节/事务场景：不同XID同commit SCN按提交位置排序，恢复和分窗发布保留第二事务；既有 `ArchivedPhysicalTransactionsTest`、`HistoricalProducerStateTest`、`HistoricalPublicationBoundaryTest`覆盖复用，不宣称raw124存在实库同SCN |
| ARRAY真实部分失败 | raw123整节点回退/重试只发布18成功行，独立进程恢复一致；这是既有业务场景补充证据，不替代同节点部分撤销 |
| LOB正常链 | 冻结raw120迁移、121/122未来窗口，完整BLOB/CLOB、实际target/producer/checkpoint/ACK、批准副本两次回收和独立重启通过；真实样本，源业务DML不重放 |

本次相关检查：原候选56项通过；部分撤销改动后30项对应检查通过。已有普通DML、ARRAY正常/回退、时间字段、BasicFile/SecureFile、ADD/DROP、TRUNCATE及迁移证据复用，不重复全套部署或故障验证。

支持边界不扩张：partial-node当前覆盖已绑定的packed ARRAY UPDATE、非事务根且不跨undo块的逐行补偿；未知INSERT/索引部分bitmap及其它未证明物理形式仍拒绝，不能称通用任意redo格式支持。已闭合LOB恢复链通过，不称pending LOB事务或无限context压缩已支持。生产路径仍仅原始redo/capture，SQL/dump仅作为比较尺子。

## 开始第一版模拟测试

1. 使用交付目录的产品应用jar和固定内核/适配器坐标；Java17。运行前核对清单中的SHA256，不混入开发覆盖类。
2. 模拟环境准备独立控制库、目标库、源只读身份及原始redo/capture目录；沿用现有产品获取/登记流程，不开启业务补充日志或修改共享业务配置。
3. 在该环境的配置中设置控制库连接、源身份/范围、目标身份、输入目录和producer/ACK目录；凭据仅放本地私有配置，交付不含口令或原始样本。
4. 启动产品：`java -jar streamhouse-oracle-v1-rc1.jar --spring.config.additional-location=file:./application-simulation.properties`。先执行基线→正常DML→目标→checkpoint/ACK→重启，再覆盖上述两个固定场景及LOB历史变化/恢复回收；记录实际业务差异。
5. 回退时先停止消费，保留已提交checkpoint/ACK/目标账本和输入；使用上一版已记录的制品和原配置。新格式的状态不直接交给旧版本读取，须保留对应旧状态及前驱证据，不能回退版本后擅自重置水位。

模拟验证待项：真实业务负载/长期稳定性、容量与性能、现场平台差异、更广物理格式及复杂故障组合。模拟发现的实际问题按明确工单处理，不自动重新开启全面开发研究。本版不是正式生产验收通过声明。
