# OracleRedoKernel

Oracle Direct Redo 独立解析内核。目标：源库零配置改动，解析与采集分离，证据不足拒绝发布。

新增Linux专属lab固定member的原始块观察journal实验：真实采样与DML/切换交叠，429归档数据块字节覆盖通过。自动跟组/恢复/record游标/ACK未实现，`capture_complete=false`，未接入事件生产。[W7反馈](docs/workorders/DELIVERY_W7_OBSERVATION_20261004.md)。

新增`ArchivedCompensationInventory`自主读取归档，取得已验证目标事务record活动窗口内同PDB全部5.6/5.11候选；下游漏/重/乱序候选拒绝。候选超集不证明并发归属，lab关闭history前强制检查，完整生产事务组装仍待证。[交付与边界](docs/workorders/DELIVERY_W4_COMPENSATION_INVENTORY_20261004.md)。

活动窗口的5.2控制另经`TransactionLifecycle.observeSlotControl`检查同segment/slot的完整sequence冲突与不可读身份，拒绝后锁定；sqn0仍需advance/head-update独立绑定。[12项派生冲突前后证据](docs/workorders/DELIVERY_W4_WINDOW_SLOT_20261004.md)。

真实跨块整事务heap rollback新增有限0x14结束、短KTB Z packed XR和空head关闭路径，仍要求完整候选覆盖及来源绑定身份；index/恢复/完整生产组装待证。[交付](docs/workorders/DELIVERY_W4_FULL_ROLLBACK_20261004.md)。归档copy不等于在线capture，CURRENT只读打开也不算捕获完成；[W7执行包](docs/workorders/DISPATCH_W7_ONLINE_CAPTURE_20261004.md)。

**当前支持文件/块/record/vector结构及有限单行heap物理patch、undo header，完整DML/事务/字典尚未验收。** `events` 命令明确失败且零事件，不能用于生产同步。进度看 [STATUS](docs/workorders/STATUS.md)，方案看 [执行方案](docs/Oracle_Direct_Redo_技术方案_v0.1_20261004.md)。

使用 JDK17（Windows默认Java8需覆盖）：

```powershell
$env:JAVA_HOME='D:\work\Java\jdk-17.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -B test package
java -jar target\oracle-redo-kernel-0.1.0-SNAPSHOT.jar scan <frozen-archive>
java -jar target\oracle-redo-kernel-0.1.0-SNAPSHOT.jar records <frozen-archive>
```

`scan` 返回SHA256/源身份/SCN并检验归档长度、块号、sequence、checksum；`records` 输出 `block|offset|length|validity|scn|subScn|containerUid|opcodes`。这些是研究结构证据，不是CDC事件，可能在后续结构失败前已经输出部分行；调用方须检查rc，禁止转成可发布事件。

当前只支持已观察的 Linux x86_64 little-endian、512字节 archived redo、compatibility 19/21；其他形态显式拒绝。schema、row、commit、rollback、日志连续性与字典版本未通过前没有事件出口。

`lab/sql/` 是专属实验环境脚本，禁止直接向业务库执行。真实样本/dump/制品留 `lab/out/`，不进入Git。独立对账：

```text
python lab/compare_dump.py <oracle-dump.trc> <records.txt>
```

0条比较直接失败。AGPL参考代码未复制；当前二进制布局来自独立Oracle字节/dump观察。

`HeapRowPatch.decode(vector, dictionaryColumns)` 只解释已观察的IRP/URP/DRP单行形态：列保持VALUE/NULL/ABSENT三态，DELETE不猜before值，未改列不变成NULL。字典列数必须有来源/SCN证据，不能自行填默认值。KTB C缺XID、KTB L旧ITL不当当前事务；UBA是物理引用，rollback会恢复旧引用，不能直接把命中UBA当当前操作的事务归属。`UndoHeader.decode(vector)` 从5.1读取XID、undo地址和对象身份，提交/补偿关联仍需事务引擎。

研究驱动 `lab/HeapProbe.java` / `lab/UndoProbe.java` 只读取原始归档，独立comparator在Python中读取dump对账；源码不消费验证真值。报告见 [W3首轮反馈](docs/workorders/DELIVERY_W3_PATCH_20261004.md)。

`TransactionEnd` / `UndoCompensation`支持有界19.3的5.4结束、5.6/5.11补偿字段。segment DBA不猜USN；begin补偿wrap1不当原事务sequence；marker不直接触发事件发布或撤销。93个真实marker字段独立对账见 [W4反馈](docs/workorders/DELIVERY_W4_MARKERS_20261004.md)，事务绑定与undo链仍待证。

`TransactionStart.bindBegin(record)`按CON_ID和精确UBA绑定同record唯一5.1 begin，再核对slot/sequence读取完整XID。缺失、多义、容器或身份错配拒绝；11个目标表真实起点对账见 [起点反馈](docs/workorders/DELIVERY_W4_BEGIN_20261004.md)。仍没有完整事务事件出口。

`TransactionLifecycle`维护来源、归档连续性和目标事务身份闭合，拒绝后锁定实例。`ClosedIdentity`仅含身份及begin/end record SCN，不授权行事件或savepoint撤销。11个真实身份闭合与3个缺失/重复归档拒绝见 [生命周期反馈](docs/workorders/DELIVERY_W4_LIFECYCLE_20261004.md)。

`UndoRowPatch`支持有界5.1 heap逆向KDO；真实XID来自undo header，历史KTB不作当前身份。UPDATE旧值只限改列，缺列保留ABSENT；尚不等于完整before行或已撤销事件。[18个真实逆向片段及正向回归证据](docs/workorders/DELIVERY_W3_UNDO_ROW_20261004.md)。

`HeapUndoPair`按同record/CON_ID唯一精确UBA匹配正向与逆向片段，并校验位置、逆操作、XID和UPDATE出现列集合；拒绝XR恢复后引用。18对跨两个目标物理块的独立对账见 [配对反馈](docs/workorders/DELIVERY_W3_PAIR_20261004.md)。仍须通过活跃事务、补偿链和完整行/字典门禁。

`HeapCompensation`联合5.6 header与原undo逆向行片段匹配唯一原pair，必须另经完整活跃XID校验；低wrap不替代完整sequence。`matchBegin`为5.11从活跃segment/slot读取完整XID后交叉匹配begin undo，匹配拒绝锁定生命周期。四次补偿与保留row7的dump/SQL字节对账见 [begin补偿反馈](docs/workorders/DELIVERY_W4_BEGIN_COMPENSATION_20261004.md)。index/完整undo链仍未接受，matcher不发布或取消CDC事件。

`UndoLink` / `UndoChain`审计包含heap/index节点的同块原始前驱图，九个commit tail读取原生5.4引用，缺节点或分叉等拒绝。原始图含已补偿操作，尚不等于生效集合。11链/32节点及六项派生拒绝见 [原始链反馈](docs/workorders/DELIVERY_W4_UNDO_CHAIN_20261004.md)，跨block规则、index效果和恢复仍待证。

KDOM2目前只支持已观察的单列扩展长度更新，保留XA/XR及缺列状态。[302个长值物理patch证据](docs/workorders/DELIVERY_W3_KDOM2_20261004.md)。`UndoBlockAdvance`与扩展`TransactionEnd`验证显式新UBA和活跃完整身份，前驱链与完整回滚效果另行验收；[15次切换与扩展结束证据](docs/workorders/DELIVERY_W4_UNDO_ADVANCE_20261004.md)。

普通跨块5.11/0x0010补偿由`HeapCompensation.matchCross`绑定活跃完整XID并匹配唯一原undo逆向值；它区别于5.11/0x0c18 begin补偿。三批180补偿含23跨块匹配及多heap block反例见[跨块补偿反馈](docs/workorders/DELIVERY_W4_CROSS_COMPENSATION_20261004.md)。通用跨块原始图、index效果与恢复继续待证，`events`仍拒绝。

`ArchivedUndoInventory.read`从完整归档访问路径收集指定容器/完整XID的5.1位置、UBA与元数据引用；`requireCompleteSelection`拒绝下游漏项、重复或重排。三批360节点native零差异和三个真实漏项拒绝见[选择覆盖反馈](docs/workorders/DELIVERY_W4_UNDO_INVENTORY_20261004.md)。这仅认证给定输入中的选择覆盖，不授权事务图、跨块边或CDC发布。

`UndoHeadUpdate` / `TransactionLifecycle.headUpdate`支持已观察的5.2/0x0002已分配块新UBA控制，并绑定唯一同record原始undo和完整活跃身份。[真实控制、10项拒绝与回归](docs/workorders/DELIVERY_W4_HEAD_UPDATE_20261004.md)。不推断完整旧前驱，不打开事件发布。

`UndoHistory`按完整目标5.1 inventory接收原始节点，将最后分配UBA与补偿后的生效head分开；已绑定的物理补偿只能弹出当前head，追加写入引用恢复后的head。[四批490节点/250 live与9项真实拒绝](docs/workorders/DELIVERY_W4_ORDERED_HISTORY_20261004.md)。调用者还须证明相关补偿输入完整，返回值不是可发布事务凭证。
