# W5 可信V3产品获取、历史HTTP与CP/ACK

已闭合获取门禁：Pa40db7b/productc47a728c/750，实际获取100747510573780992/SCN2566695/SHA323fd35a，407项封存；完整ASOF与六恢复反例/同件重启通过。下一步直接接续新历史窗口，不重做获取/测试，旧helper37261 running、auto-consume关闭，目标V3HP未建/未批准。详见DELIVERY_W5_V3_PRODUCT_ACQUISITION_20261006。已封存runtime文件不修改，新增源实验证据目录v3-product-history-20261006。

接续历史目标交付020be30/P65e12c2、adapter65e0fcba、productac98404d；216封存、26/746已通过不重复。全goal active，禁止Luna/子代理，共享产品仓只读，私有P唯一修改；PR#13 draft依赖#12，独立评审PENDING。先核STATUS和资源归属。

1. 原SCN2536302获取有冻结真实源码/输入/回执，但尚未存在产品ACQUIRED_ONLY账。优先选择**产品proper新V3获取后一次新fixture**，避免引入任意文件/客户端SHA登记通道；旧68/69作为已闭合历史证据，不用新晚SCN回填。若坚持登记原回执，须先设计可核验来源与完整获取内容的受控机制，不能手造旧账。
2. 检查OracleSnapshotAcquirer.acquireHistoryBound与FrozenHeapBaseline V3历史布局，产品获取服务/恢复metadata必须绑定全部SYS布局、行锚点、三索引identity/DBA及SCN，不只六anchors。保持V2兼容；缺布局/角色/摘要/authority/scope/文件任何一项拒绝，不产生批准binding或事件。隔离坐标/相关测试及完整构建后冻结同件，禁止把目标direct writer当HTTP。
3. 在专属owned lab、单一owner下，以新产品真实HTTP获取V3、独立ASOF完整对照通过后，才做一次新受控ADD nullable/UPDATE/DROP/UPDATE窗口。执行前冻结脚本/资源/命令，记录开始、关闭、capture与归档SHA；任何部分执行先核当前状态，禁止从头重放或drop旧证据。source NO/NO保持；配置实验不能当生产零侵入。
4. raw完整历史factory→逐事件schema/before/after→受控绑定关联实际获取账→真实HTTP→新owned target，证明两事件有序全字段及原子target receipt→控制CP→持久ACK含baseline+有序schema关联。独立SQL核目标/成员/commit/控制账，独立文件核ACK；重复0新增，同件重启恢复字节稳定。
5. authority/历史摘要/绑定错配/target receipt篡改/缺原始历史等反例逐项rc非0或明确REFUSED、event0、目标/control/CPACK零推进并精确恢复。已通过旧V2样本只做必要兼容检查，不重放旧源事务。真实高风险独立评审继续PENDING，不越过验收。
6. 封存完整失败、stdout/stderr/rc、输入/源码/制品/输出双SHA与事件数；更新两仓STATUS和反馈表，显式安全路径提交推送，不上传secret、raw或研究外仓。当前owned同实例目标不等于独立目标/现场/发布。闭合后继续活动跨窗、正式采集、独立目标与类型/环境/故障矩阵，全部Oracle目标完成才查Codex卡顿。
