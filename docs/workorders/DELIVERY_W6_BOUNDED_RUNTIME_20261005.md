# Oracle 批准有界输入运行时 · 候选交付（2026-10-05）

完整代码树1c6d75ec9485d0187872e8b4d1438bde3b09f76b，clean verify723项0失败0错误0跳过。显式服务器开关sh.oracle.bounded.enabled默认关闭；批准登记、consumer和固定目标配置都齐备时才装配OracleApprovedInput、OracleJdbcTarget及OracleBoundedPublication。普通options不授权。内核/适配器只用固定二进制坐标，不复制解析源码。

运行链：批准capture/基线实际SHA与完整投影核对→控制来源锁及任务/窗口/连续性/既有eventId预检→独立连接原子目标apply→新连接完整commit核验→同控制事务内账本/PASS/批次/CONSUMED/checkpoint提交→ACK文件force及原子move/目录force。控制失败保留目标commit但无CP/ACK；ACK冲突保留控制事实并停摆，恢复重新核验后补ACK，DONE重复同样核验。文件系统不支持所需force则明确失败。

实际产品Java Facade及实现、真实Spring/MyBatis事务、专属Oracle新控制schema从空库23迁移到V730、四目标组通过（事件2/2/3/0）：实际checkpoint SQL后抛错全部控制回滚；目标提交保留；ACK冲突发生在控制提交后，pending已空；重建Java实例恢复与DONE重复，checkpoint及ACK稳定。独立SQL导出比较目标身份/完整中间像/成员/凭证/最终行、账本、完整CP/ACK与既有raw/native/typed证据四组零差异；三比較反例检出。源业务DML0、NO/NO配置及旧业务表不变。

失败追加：首次编译少传既有commit参数；后续Mockito嵌套stubbing测试错误；首轮实库复用旧控制schema已入账eventId，正确ORACLE_CONTROL_EVENT_LEDGER_CONFLICT，未到达预设故障。旧控制库保留，改用新专属schema；增加eventId写前拒绝。SNON目标2条首次提交发生在旧控制冲突轮，本接受轮核验/复用该目标；其余三目标首次提交在本接受轮。不得宣称接受轮四组都首次应用。

证据入口：OracleRedoKernel docs/workorders/evidence/DELIVERY-oracle-bounded-runtime-20261005.json，316文件双SHA，运行前类/资源/依赖及driver清单绑定代码SHA；秘密、大制品和原始事件数据不提交。许可二进制：streamhouse-engine-oracle:0.1.0-readonly-8de94aae（8de94aae…）；oracle-redo-kernel:0.1.0-bounded-foreign-begin-r2（96095420…），内核254/适配器16已通过轮次不重跑。

代码交付通过｜针对性验证通过（有界）｜集成验证进行中（实际Java Facade有限通过）｜部署未开始｜现场待证｜发布未开始。独立评审PENDING；依赖草稿#6，禁止合并部署。

边界：这是专属Oracle控制方言、同Oracle实例跨schema、各固定schema/目标单独装配的有界实验。恢复为重建Java实例，不是跨进程重启/SIGKILL；Spring服务HTTP入口、其他控制方言完整Facade、独立目标实例、生产字典/基线获取、消费者租约、活跃跨窗、实时C++采集、Windows/ASM/断电/现场/发布仍待证。Oracle失败WindowRun的published目前按端口是否完整返回记录，ACK失败后不能据该字段否认数据库已提交事实；下一轮核对API/审计事实口径。

[安全SHA摘要](evidence/DELIVERY-oracle-bounded-runtime-20261005.json)。
