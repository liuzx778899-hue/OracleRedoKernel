# I_COL3前像／两叶重建typed字节候选

新增DictionaryIndexLeafRows，严格解码观察到的8032字节I_COL3前像和两种10.8重建格式：NEW携带KTB/header/prev/next，OLD仅携带next。完整目录、末尾边界、逐行2字节偏移修正、两组件key、big-endian rowid、flags/ITL及严格顺序均校验；保留每个物理slot和完整raw行。行字节解码不证明source/lifecycle/完整split，不授权事件。

最终r2内核完整clean package **309/0失败/0错误/0跳过**，SHA256 `b4913b23c18d8c950fccd65873dfcc51c917e03a14cb498ca01a714228450e30`。四新增测试覆盖目录/截断/overlap/end、未知flags/ITL、零rowid、乱序、两种header及NEW目录origin。旧117个production class字节完全不变，仅新增LeafRows与Row两class。

原产品早V3与冻结seq70，typed输出BEFORE445/NEW220/OLD226，全部891行的raw字节、Oracle NUMBER、rowid、flags/ITL与独立native完整内存区域解析0差异。前后无丢行，只新增一条0101删除标记行：OLD slot7，raw `0101004089a4003304c308345602c105`，rowid004089a4/51，parent75185/internal4。错raw、NUMBER、rowid、flags四独立比较反例检出；不等于合法checksum完整历史安全矩阵通过。

**OLD previousLink未知，用null表示。** 首候选曾用0作为缺席占位，接口审查发现其可能被误解为已证明的零；首133项/SHA与源码保留，改为null并重新完整构建/真实字节核验。最终r2的129项为当前候选，首133项不作为prev字段authority。没有源动作重放或产品换装。

封存：[最终129项清单](evidence/DELIVERY-dictionary-leaf-rows-r2-20261006.json)，[首133项保留](evidence/DELIVERY-dictionary-leaf-rows-20261006.json)。双SHA及写后复核，前139+130+205+407逐项不变。0新源/配置/目标/产品构建或换装。完整历史工厂尚未接入此decoder，本轮不重跑旧完整入口；维护事务闭包、restore绑定、两业务事件/产品HTTP/CPACK仍待证。原全goal active，独立评审、现场与发布未通过，Codex最后查。

## 下一执行包：自主完整维护闭包，不再拆成局部通过

P0使用当前kernel和原70/71/产品snapshot，在新输出目录接续。禁止Luna/子代理、晚基线回填、源fixture重放与封存覆盖；共享StreamHouse只读。

1. 从实际输入发现I_COL3/10.21维护XID，使用既有ArchivedUndoInventory、UndoHistory和TransactionLifecycle完成自主source/position/五original/两advance/end/tail覆盖；不得使用手工XID/固定RBA清单当生产选择authority。
2. 按原始节点顺序绑定LOCK、10.9前像、NEW重建、PREVIOUS_LINK、BRANCH_INSERT及end同记录OLD重建。R型forward当前ITL的XID/UBA必须与undo匹配，foreign undo历史ITL不能替代。可信segment、角色/世代、source/container、SCN及位置关联要完整。
3. 联合校验：old/new/neighbor互指，branch child=new，separator与NEW首key/range一致，全原始行守恒且唯一新增0101删除标记；OLD prev没有当前重建字段，不能凭零值或猜测补齐。完整处置所有原始节点及独立结束重建，不跳过任何维护记录或未知opcode。
4. 形成不可伪造的raw位置/slot凭证，将主字典事务10.5与同记录10.22 key及OLD slot7的rowid/key/size/ITL绑定，逻辑恢复为索引INSERT；维护本身不新增逻辑COL$事件。随后接入自主历史发现/全original分类/三索引最终状态。
5. 同原实际V3完整70/71入口放行两完整UPDATE/schema4→3，独立native/阶段SQL核完整像/hidden NOTE/XID/SCN/位置；缺节点/end重建、错link/child/separator/行/slot/rowid/UBA/世代做合法checksum全入口拒绝，rc非0/stdout0。再新adapter/私有product真实嵌入、HTTP→target→CPACK/重复/重启与错receipt零推进，独立目标/现场/发布另验。

六状态：代码交付=typed叶字节候选；针对性验证=309与891行独立通过；集成验证=完整维护闭包未通过；部署=未换装；现场验收=未通过；发布=未开始。
