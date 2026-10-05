# W6 产品缺失LSN拒绝候选交付

接续6078df6空侧修复。当前产品PublishGateService在null begin/record/commit LSN上先抛NullPointerException；既有LSN_MALFORMED检查未获得执行机会。最小修复为允许null的数组遍历，原SQL Server格式拒绝规则保留。未把Oracle source/位置拼成SQL字段，未启用Oracle公开decoder。

## 提交与证据

产品隔离分支codex/oracle-gate-null-lsn：代码784a5699c7327c0f1ee47207c0cb65c150899a2b，文档tip19f8806；两提交之间仅交付报告与STATUS变化。该代码完整提交树后端reactor test rc0，701项，错误/失败/跳过均0。新增测试逐项缺begin/record/commit，核验REFUSED、拒绝台账、零事件账本写入；测试使用既有fake Mapper，不能称实际控制库台账验收。

三真实冻结非空/NULL/混合batch先使用同源码编译class验证，再使用reactor实际测试过的PublishGateService与SPI/common classes重新核验；都rc1/stdout0，明确EVENT_ID_INVALID、SOURCE_INCOMPLETE、LSN_MALFORMED，不再NullPointerException。后一轮是核验实际候选class，未重建/重跑已通过reactor。没有源/目标写入、产品部署或public放行。

37文件双SHA与候选source/test/实际class指纹、83份测试报告指纹见[证据](evidence/DELIVERY-product-null-lsn-20261005.json)。大日志/制品保留lab/out/product-null-lsn-20261005，隔离工作树lab/out/streamhouse-oracle-gate-worktree；产品仓只交付普通gate代码/测试及契约说明，不复制内核研究材料。原共享HEAD67bfb775…、原两dirty文档与agent/build-curl原样保留。

已推送[草稿PR #2](https://github.com/liuzx778899-hue/StreamHouse/pull/2)，独立评审PENDING，未合并/未部署。产品流程要求发布闸门独立评审；当前禁止子代理，作者自检不能替代独立审阅。部署、现场验收、GA全部未完成。

## 下一执行项

[Oracle来源与位置契约](CONTRACT_W6_ORACLE_PRODUCT_BINDING_20261005.md)明确CapDecoder结果需可选typed batch evidence，并通过受控registry绑定来源八字段、capture闭合链、baseline acquisition/schema generation和唯一消费身份；普通options不能授权。事件提交顺序、paired undo定位和transport issueSeq分开核验，SQL Server分支保持原语义。随后真实产品decode→gate→target→checkpoint/ACK及晚失败/串源/错位置/错schema/无登记空batch反例。历史字典、活跃跨窗、C++实际采集、独立目标实例和现场发布仍属于原目标，不能用本单的明确拒绝代替这些门禁。
