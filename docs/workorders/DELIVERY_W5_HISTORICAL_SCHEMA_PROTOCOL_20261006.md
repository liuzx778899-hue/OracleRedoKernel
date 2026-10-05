# W5 历史schema证据协议与产品兼容候选

内核/adapter代码e5477a6，产品代码8cc33b1（文档135a1d9）。[封存清单](evidence/DELIVERY-historical-schema-protocol-20261006.json)241项双SHA、封存后全部匹配，旧75和189逐项不变；候选目录`lab/out/historical-schema-protocol-20261006`。

|制品|SHA256|针对性测试|
|---|---|---|
|kernel|240c2250f00256209bf02ccb9b17918e90797c920e113ac552db2881c678c25c|299/0/0/0|
|SPI|53b84d186ba2cfa7f14143ae04cd553354a783e98a71642d271825d65059d829|8/0/0/0|
|adapter|353407790ac810cefda2d47688d48cc9ed4ae1d46fd705b67d799ddfd65960f9|24/0/0/0|
|产品兼容候选|18f49be40e91897094e581170da77738515536cffeb78f0ce73a58202d93a453|743/0/0/0完整构建|

## 本轮交付与证据

可信历史factory新增baselineSchema只读API。`OracleBatchEvidence`增加逐事件eventSchemas和schemaSequenceSha256；顶层columns/schemaSha256继续是原可信获取基线，旧11参数构造与缺字段JSON仍为固定schema（新列表空/摘要null）。历史投影的公开DTO携带实际row key、完整XID、SCN、位置、基线3列及事件4→3列；关联摘要仍c23904a9，与前件协议向量一致。

真实V3/seq68—69两事件完整DTO/字段独立断言及JSON往返相符，重复JSON相同，擦四marker事件与schema有序关联相同。错误摘要、交换关联、裁剪新增列三个整入口rc1/stdout0：前两由SPI关联检查拒绝，裁列由完整DTO与raw factory结果比较拒绝；**关联摘要本身不认证任意调用方填写的列定义**。基线metadata与事件schema保留，不能将最终三列当整批schema。

产品受控注册读取新增关联校验，SPI3测试+注册3测试覆盖缺摘要/错摘要/错误顺序/旧存储兼容；完整产品构建743/0/0/0。旧V2静态三IUD所有旧DTO字段、完整envelope及envelopes SHA相同，新固定schema字段严格空/null。封存完整构建日志与各模块报告；三条初始shell构建的command记录明确标为执行后从工具调用重建，不冒充执行前封存。

## 产品制品核验与未验收项

产品兼容包内真实kernel仍672f01bc、adapter仍9516d4f9，SPI为同协议新类（独立SPI与产品SPI class字节相同，jar打包SHA不同）；没有把独立kernel240/adapter353当已安装产品。历史decoder、target receipt、control checkpoint/ACK尚未接上新关联，V3实际产品获取账与该窗口授权未过，本轮无历史HTTP发布、0源业务/配置变更、0目标动作、未换装服务。[PR#13](https://github.com/liuzx778899-hue/StreamHouse/pull/13)保持draft，依赖#12，独立评审PENDING。共享checkout未修改。

下一步按[历史产品执行单](DISPATCH_W5_HISTORICAL_PRODUCT_20261006.md)补齐可信历史获取回执、decoder及统一target commit/control CP/ACK关联。原actual V3 SCN2536302先于68/69，**不能以当前晚SCN的新获取登记回填旧窗口**；若使用原冻结获取件，须有受控、可核验的原始获取回执登记路径，不能客户端任填baseline SHA或手造获取账。旧68/69源动作已结束禁止重放。

原全目标active：活动跨窗、正式C++采集/租约、独立目标、类型/环境/高压断电、独立评审现场发布仍待证。全部Oracle目标完成后才查Codex卡顿。
