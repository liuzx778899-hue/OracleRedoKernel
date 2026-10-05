# Oracle Facade 发布与恢复接线 · 候选交付（2026-10-05）

完整代码提交143f78f，clean verify715项0失败0错误0跳过。PipelineFacade增加仅Oracle采用的服务器发布端口；装配时由ObjectProvider提供，普通options不产生端口。已装配Oracle窗口不会走旧SQL发布/消费/checkpoint/target顺序，而由专用实现负责目标凭证、原子控制提交、持久ACK后返回。没有实现时保留原公开拒绝。

恢复任务在跳过已消费窗口前调用recover；ACK失败后即使pending队列为空也必须补验/补ACK，DONE重复恢复同样调用核验。恢复抛错失败停摆，不继续解码；Oracle发布失败保留已有checkpoint并停摆，不回退已提交事实。

四新增单位测试：专用路径保持Oracle checkpoint、不调用旧gate/target/markConsumed；目标/控制失败无checkpoint；控制提交后ACK失败从空pending恢复且不重复decode；证明损坏恢复拒绝。这里使用测试端口和内存Mapper，不是完整真实产品链验收。上一单三库实际控制事务证据保留、不重跑。

代码交付通过｜针对性验证通过｜集成验证待证｜部署未开始｜现场待证｜发布未开始。独立评审PENDING，依赖草稿#5，禁止合并部署。

下一步实现服务器端OraclePublicationPort：从批准登记读取并复核真实capture/基线，用允许的二进制投影执行目标应用/独立凭证核验，调用OracleControlPublication提交控制事务，再持久ACK；recover按已提交batch和完整checkpoint重新验证输入/目标/控制事实后补ACK。明确实现还未交付，接口和单位测试不能代替它。随后专属真实Facade故障/重启/两进程矩阵、独立目标实例与现场发布继续待证。

安全摘要：[SHA封存](evidence/DELIVERY-oracle-facade-publication-20261005.json)。
