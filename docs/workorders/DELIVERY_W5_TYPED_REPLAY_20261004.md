# W5 typed世代与真实获取基线完整行链路

状态：19c专属固定schema、来源回执和明确世代的有界链路通过；初始活动事务跨snapshot、通用历史字典、rollback/并发完整行、持续capture、CDC/现场/发布未验收。

## 制品与源端动作

最终运行时完整树`4afea7fd8f70159a86679a55cb79f803ff09bc01`，实验/验证树`1c7106f`。223测试0失败/错误/跳过；jar SHA256 `0e57e330971d6d41b07896e520958ce2a0d0ec1ee6339d21abacc77502524140`，位于 `lab/out/release-typed-scope-r4/tree/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar`。

一次性fixture脚本60d43bd仅作用于专属已有项目表：先验证source/两角色当前generation、总行数1和真实旧ASOF，按预期值CAS更新TXT，再CAS更新ID，每步sql%rowcount必须1，同一事务提交一次并归档/dump。没有业务源配置变化、没有启用supplemental；受控fixture DML不是生产侵入要求。失败没有重放DML。

新XID2.9.595；seq27完整归档137674240字节，SHA256 `061b8b3d915145eedbff3cf3ec22416495d60ea6f5f60106c7d504e69ea15817`。基线仍为实际获取snapshot2341293，SHA256 `36e3c954dd0844367bbb2bb9bec8c9b1f63f4069b1c1b01aa6239f62792f220d`；提交后再次旧ASOF仍一致。

## 实现与真实结果

`HeapIndexScope`分别声明heap logical73073→data73076、index logical73074→data73075，禁止跨角色ID碰撞。自主发现逐项校验原undo与相关补偿的完整(objectId,dataObjectId)对；legacy Set只表示各ID等值映射，不再用四ID集合掩盖交叉映射。physical dispatcher、index leaf inventory/position都传递logical/data pair，完整行缓存按data-object/DBA/slot定位。

`ArchivedHeapRows.readVerified`直接导入冻结snapshot及实际获取回执，不由驱动手填行值，再完成整件有界物理门禁、SCN/source检查和before逆向值核对。两次实际完整行：

| 顺序 | before | after |
|---|---|---|
| TXT UPDATE | 10 / new-epoch / new-note | 10 / typed-epoch / new-note |
| PK UPDATE | 10 / typed-epoch / new-note | 11 / typed-epoch / new-note |

- 2次完整before/after及提交后旧ASOF、最终SQL两快照与native独立零差异。
- 自主发现1笔事务，4原始/0取消/4保留（2heap、2index），全量有序物理图与native零差异；native比较器先验证角色pair，再比较图，不以四ID集合替代角色检查。
- 真实主键UPDATE使用XA KDOM2短编码：KDO布局控制字0、map0、payload`02 c1 0c`及inverse`02 c1 0b`。解析短正长度tag并核整字段长度，保留其它列ABSENT；packed sizeDelta继续显式未知，NULL tag/short XR仍拒绝。没有将布局控制字推断为长度字段。
- 同record的commit先于undo vectors仍正确处理；本例不证明跨snapshot初始活动事务。

## 失败与反例

第一轮220测试后，完整seq27遇全局发现中的范围外未知补偿flags，rc1/stdout0。原始读取定位27:192928:56:1，已知2field/28字节头，object/data=1/4294967295，双方均在目标角色之外。只对可读公共头、明确范围外对象做排除，未放宽flags；新增测试证明同一未知flags进入活动窗口后仍拒绝。第二轮221测试后，主键真实短KDOM2仍UNSUPPORTED_KDOM2_UPDATE_LAYOUT、rc1/stdout0。全部日志保全。

首个短编码候选40a3e14误把KDO控制字当1字节前缀标记，虽223合成测试通过，没有实际支持证据；真实字段读取证明控制字为0，4afea7f修正，最终同原输入通过。r1/r2/r3构建及字段研究均保留，不把候选编译/合成测试充当真实支持。

| 反例/回归 | 结果及边界 |
|---|---|
| 3语义映射反例 | 在独立重算digest的测试快照下，交换heap/index data角色、旧generation、错logical均OBJECT_TRANSACTION_MAPPING_MISMATCH，rc1/stdout0；这是语义scope测试，不能计为原回执下篡改认证 |
| 原回执改未改键 | SNAPSHOT_ACQUISITION_DIGEST_MISMATCH、rc1/stdout0，完整行驱动不再直接填伪键 |
| 变化途中before失配 | 独立重算digest的语义测试基线ID改为11，首个TXT变更能局部匹配，但第二个PK变更的inverse ID10与缓存不符；ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0，不能发布首个已处理片段 |
| 3真实原始字节派生 | 短packed长度、未证short XR、原undo交叉role；各重封checksum并scan通过，完整行入口全部rc1/stdout0 |
| 独立比较器反例 | 行漏变化/伪未改NOTE NULL/旧data-object及图漏节点/错role均检出；不代替内核拒绝 |
| 同件jar受影响旧回归 | seq15长值120原始/120取消/0保留与native零差异；seq24自主三物理图10/4/6与native零差异 |
| public events | seq27 rc1/stdout0、DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED，CDC0 |

## 接续

基线文件、获取回执和有界DML已连接；生产控制器的可信获取交接、历史schema/DDL/ABA认证仍未完成，旧直填Baseline API仍是声明型实验入口。本轮固定schema证据不能解锁public events。

下一步按[真实边界派单](DISPATCH_W5_SNAPSHOT_BOUNDARY_20261004.md)优先补初始事务跨snapshot与缺begin/end反例，再推进INSERT/DELETE/rollback/并发完整行覆盖。Record SCN名义边界不自动等于SQL事务开始时刻，须用分离源会话与独立native证据验证；专属新输出目录、一次性DML，失败只补读取/解析。之后继续持续capture/ACK、StreamHouse和独立现场/发布验收。

清单：[DELIVERY-typed-replay-20261004.json](evidence/DELIVERY-typed-replay-20261004.json)。
