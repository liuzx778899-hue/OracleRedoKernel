# 下一阶段执行包：合法空批次与产品接入

当前入口：STATUS与DELIVERY_W6_W7_NEW_DML_TARGET_20261005。已闭合项不重跑；main交付与真实实验证据分开，长期目标未完成。禁止Luna/子代理，保留dirty与失败原文。

## P1 合法独立空批次

已证明49—50不能直接沿用48中的旧基线，BASELINE_SCN_OUTSIDE_INPUT_COVERAGE必须保留。先只读审计已有seq36完整rollback-only原始归档及可信获取回执，或现有真实capture内覆盖合法基线的空范围。seq36旧事件/图接受证据在DELIVERY_W5_ROLLBACK_ONLY_20261005，不能用SQL/native生成生产事件。

若只能从原始归档包装lab capture，须明确标archive-derived，并逐块验证不变；此路径可验证空batch投影/目标凭证/checkpoint/ACK，但不能当真实collector新空窗口证明。若需要新可信基线及新专属空capture，只执行新范围，旧44—50 DML不重放，生产零侵入门禁不变。不得为过关伪造基线SCN/获取回执，或跳过回滚控制。

交付判据：独立合法batch entries0，专属新目标业务/审计/成员均0，实际提交凭证eventsCount0，target commit之后checkpoint前沿及ACK发布，重复0且文件字节稳定；原始取消覆盖、SQL基线、输入/制品/输出双SHA独立关联。缺marker/错baseline/ACK无commit保持整批拒绝。未执行项写待证。

## P1 产品公开入口边界

共享StreamHouse只读核public SPI、Oracle decoder/scanner、C++ sh_agent的实际路径与未闭合gate，先形成最小接入设计及可证伪验证包。已有独立adapter私有窗口不可改成授权字符串绕过public拒绝，不能伪造SQL Server LSN/GUID或commitAt。生产可信schema/基线认证与范围完整性未闭合时继续拒绝。

目标交付：可审阅的接口/范围/失败语义方案与窄实现；针对新变化验证，公共拒绝保持；之后才完整真实产品pipeline与独立目标实例执行。不得将本轮同实例专属目标或15模块测试当产品部署/现场/GA。每次实质交付立即显式安全stage/提交/推送反馈，失败追加，不上传secret/raw/dump/外仓源码。
