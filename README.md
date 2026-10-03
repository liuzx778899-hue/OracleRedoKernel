# OracleRedoKernel

Oracle Direct Redo 独立解析内核。目标：源库零配置改动，解析与采集分离，证据不足拒绝发布。

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
