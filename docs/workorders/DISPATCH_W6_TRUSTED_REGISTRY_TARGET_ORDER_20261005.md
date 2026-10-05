# W6 可信Oracle登记与产品target事务顺序派单

前置交付typed evidence（产品ddff6ae、704测试；adapter dfcfe38b…/16）仅完成数据透传与未绑定拒绝。草稿PR #2/#3待独立审阅，不合并部署。负责执行当前主agent，禁止子代理/Luna。产品独立工作树保留，shared只读，原源DML不重放。

## P0-A 受控登记与引擎守卫

- 先核当前app BaselineCredentialRow/Mapper/Service/注册API与三库迁移。现有baseline守卫以SQL cap target/database字符串匹配，不能共用给Oracle。登记必须来自受控集中端流程，禁止上传JSON或DecodeContext.options自动创建信任；无需业务DDL/补充日志/源重启。
- 最小Oracle可信记录按sourceId绑定来源八字段、baseline获取摘要与SCN、schema generation与对象/列范围、实际capture闭合链/摘要与固定范围、唯一消费身份。未知历史schema/跨窗活动状态继续拒绝，不能把只在实验中固定的baseline升为生产通用字典。
- source登记、消费前guard与发布前验证均读同一受控记录；实际物理输入与binary kernel结果逐字段核对。typed evidence必须与本次events/batch精确绑定，所有事件缓冲后才返回，晚失败零输出。若有三库SQL/迁移改动按产品流程跑隔离三库真实Mapper/新装升级，不能只mock通过。
- 反例：无登记、错source/activation/resetlogs/container/thread、错baseline/schema/对象/列/leaf、改capture范围/摘要、错XID/record SCN/paired位置/row key/事件摘要、普通options或自声明hash企图授权。空batch也逐项校验，0事件不豁免。

## P0-B Oracle target/账本/水位/ACK次序

- 当前Facade在target前记账并推进checkpoint；先复现其目标失败处置与恢复状态，不扩大SQL Server无关改动。Oracle目标原子业务写入/事件审计/有序成员/commit凭证必须使用同一验证batch/source/schema/baseline绑定。
- 实际target commit与可复核凭证完成前，不得发布消费水位/ACK或标窗口CONSUMED；commit后控制库写入失败必须从凭证恢复、业务重复0；丢凭证、成员/计数/摘要/source/schema篡改拒绝，不能由消费者补造旧凭证。明确跨库边界，不能用无事务的多次成功调用当原子闭环。
- 不改变事件ID，不用SCN或位置合成SQL LSN。Oracle以已验证的提交顺序/事务内sequence排序，paired undo只定位；相同提交record SCN无证全序保持拒绝。transport issueSeq连续性另核。
- 反例与实际环境：目标中途失败整批回滚且水位未动、commit前/后与水位落盘边界恢复、并发/双消费者、错source/批次重叠、迟到失败、独立空batch缺凭证；先用新的专属控制库/target验证、同件运行指纹留证。不能用测试Mapper证明真实持久化。

## 出口与遗留

交付完整产品提交树、内核/SPI/adapter坐标与SHA、同件真实decode→gate→target→账本→checkpoint/ACK及独立raw/native/SQL对账、失败原文与反例、三库适用检查、六列状态和安全推送。高风险独立评审PENDING期间保持Oracle发布拒绝、不部署；授权禁止子代理不等于豁免独立审阅。历史字典/跨窗/C++采集/独立目标实例与现场GA保持原门禁，不将本派单范围替代全目标。
