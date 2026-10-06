# 维护begin与实际生命周期候选

结论：新增严格19.3已观察044a pre-split begin profile，并在原冻结70/71窗口验证XID9.9.660完整生命周期及原始前驱链。完整历史工厂仍拒绝、0事件；不是索引维护完整支持或历史产品链验收。

源码仅修改TransactionStart的已观察profile和两个测试；研究探针增加调用既有typed UndoBlockAdvance。044a profile要求首record1、保留字段0、size136、opaque控制字0x3f98及零父事务；不解释控制字语义或放宽未知profile。当前XID/UBA/slot/sequence/container绑定不变。

- 针对性TransactionStartTest实际10项通过，新增控制字/size/flags/父字段及错XID/UBA/重复begin/container反例。
- 内核完整clean package实际301/0失败/0错误/0跳过；kernel SHA256 `4e7eeafc9cde59f6bc1168334509f6d75cec7c012638b2ba61c96df8be55152c`。
- 同原snapshot/seq70：begin1098、两advance10、五original/live及endSCN2567427/tail02402f51.e6.3闭合；原始节点身份及native前驱逐项0差异。旧完整lifecycle拒绝原文位于上一205项，不覆盖。
- 新候选完整capture历史工厂rc1/stdout0，原原因DICTIONARY_DISCOVERY_INDEX_COVERAGE保持。实际branch separator/child、邻页链接和重建事务typed全覆盖尚未建立，不能跳过维护图或从SQL补key。
- 本轮0源动作/配置/服务重启/目标写，不重新获取基线，不回填旧日志；0适配器/产品构建或换装，旧产品750仍只是原制品证据。

[130项双SHA封存清单](evidence/DELIVERY-index-maintenance-begin-20261006.json)，前205研究和407获取逐项SHA/大小不变，lab/out与secret不入Git。独立评审、现场与发布未通过；完整goal active，Codex卡顿最后查。

## 下一执行包

接续RESEARCH_W5_V3_PRODUCT_INDEX_MAINTENANCE_20261006.md第2—5步。使用本kernel及原205窗口，在新输出目录推进，不覆盖封存记录。

P0：建立完整typed I_COL3维护闭包，必须绑定锁/前像/new leaf/old leaf/neighbor prev/root branch child与separator/commit及主事务10.5；以445→220+226全行守恒及新增删除标记行的raw槽证据证明restore的key/rowid。五original和独立结束重建记录必须全覆盖，typed维护不制造逻辑新增事件。缺节点、错child/link/槽/rowid/世代/UBA/commit tail做合法checksum完整入口反例，失败0事件。

闭合后同实际V3获取基线的70/71完整入口应产生两个完整业务UPDATE/schema4→3，并与native/阶段SQL独立比较；再新适配器/私有产品真实嵌入、HTTP→target→CPACK及重复/重启/拒绝。不得以本生命周期通过替代这些剩余门禁。禁止Luna/子代理，共享StreamHouse只读。

六状态：代码交付=begin候选；针对性验证=301及原生命周期通过；集成验证=完整历史拒绝；部署=未换装；现场验收=未通过；发布=未开始。
