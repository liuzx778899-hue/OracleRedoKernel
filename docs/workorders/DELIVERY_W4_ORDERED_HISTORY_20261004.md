# W4 原始分配历史与生效head分离

rollback后追加写入的原始节点会引用恢复后的head，不能继续要求全部原始undo形成无分叉单链。新增`UndoHistory`按archive-owned inventory顺序接收每个原始节点，记录分配尾和生效head；外部先经物理补偿匹配/完整活跃身份验证，再弹出生效head。追加节点引用当前生效head，5.4尾引用则核对最后原始分配节点。

核心功能提交3002c5b，完整封存树8a18e3d（包含真实派生拒绝driver）；[证据清单](evidence/DELIVERY-8a18e3d.json)。完整Git archive树、Java17独立重建，jar SHA256 `aefeea587e24c48993f3b513a292195fdf607473b1e1479b2e3c67ea0e415345`。109项测试，0失败/错误/跳过。本轮未执行Oracle DDL/DML，仅只读冻结seq10..13；未部署或开放事件。

## 有界证据

| 项目 | 实际结果 | 处置 |
|---|---|---|
| 四批原始节点位置/UBA/metadata引用 | 490节点，native零差异 | PASS指定输入5.1覆盖 |
| ledger导出的live heap集合 | 240个已匹配补偿、250个保留patch，native零差异 | PASS有界heap生效集合 |
| seq13回滚后继续写入 | 130原始/60撤销/70保留；最终两行SQL字节相同 | PASS有界追加场景 |
| seq10完整跨块前驱佐证 | 新history的15条边与独立事后页sequence/CNT零差异 | PASS此样本观察，不代替归档时点一致性 |
| 控制身份 | cross entry经唯一5.2/0x000a或0x0002绑定，同record原undo/full active XID必须一致 | PASS既有有界规则回归 |
| 真实派生顺序与补偿拒绝 | 漏原始节点、重复/重排节点、漏首补偿、全sequence+65536、重复补偿、提前close、错allocation tail、跳过全部补偿后resume | 9项精确原因拒绝、9次锁定 |
| 旧marker/物理pair回归 | head-update及10项拒绝、旧18pair/4补偿/SQL row7、长值patch等同件jar通过 | PASS |
| events | 四批rc1、stdout空 | 继续拒绝 |

5个新单元测试包括“rollback→resume→第二次rollback”的合成历史，检查allocation tail仍为最后生成节点、active head回到保留节点。第二次rollback的真实Oracle样本仍待证，不把合成测试写成真实支持矩阵。

## 实现约束

`append`只接受inventory中的下一个typed entry；漏项、重排或重复位置拒绝，原始UBA重用也保守拒绝。begin必须为首节点。同块前驱按已证DBA/sequence/rci完整引用校验；跨块previous DBA/rci与当前生效head核对，完整旧UBA来自有序状态而非历史行KTB。`compensate`必须等于当前head且原UndoHeader完整身份一致，不能跳过中间节点或用低wrap碰撞替代XID。失败后锁定，成功close后不接受追加。

研究驱动逐个读取完整目标5.1输入，cross entry还必须经过精确控制身份绑定；从`Result.live`集合挑选保留物理patch，不再仅按历史下标和cancelled集合输出。新增边值输出供独立native直接字段对账；SQL/dump不参与Java状态生成。

本实现返回的是指定5.1历史及**调用者提供的已匹配补偿**下的结果，不是完整事务凭证。尤其在rollback后没有追加写入时，遗漏所有补偿未必被5.1节点及allocation tail发现；API本身无法证明补偿输入完整。当前lab驱动按冻结目标heap对象读取全部已观察marker，并以native完整marker集合对账，但这不证明通用index/未知相关opcode和恢复覆盖。

下一阶段优先形成事务相关控制/补偿完整输入门禁，再补第二次savepoint、完整rollback、index和恢复。W5字典/基线与StreamHouse接入仍待证。不能因为有序history或生效集合通过就打开CDC，也不将事后undo页引入生产路线。
