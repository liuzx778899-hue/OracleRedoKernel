# W5 固定基线完整行重建与来源反例

状态：代码及真实固定样本顺序行重建通过；基线来源验收RETURN_FOR_FIX，完整DML/历史字典/CDC/现场/发布未通过。

## 制品

最终完整源码 `8b222c8e045f2cf832a362e39ce2c8a7e5e73a2f`，Java17封存目录 `lab/out/release-heap-baseline-r2`，213测试0失败/错误/跳过。jar SHA256 `27820d2978aef60729d002bf289bbf366a7257a5b987ee6aba0b85024db3bfb9`。第一轮b8cae3c制品及全部结果单独保全；独立比较器2857210已包含于最终构建树。

清单：[DELIVERY-heap-baseline-20261004.json](evidence/DELIVERY-heap-baseline-20261004.json)。同一清单保留两轮构建和原始成功/失败/未认证输出，不重跑数据库操作。

## 实现范围

`ArchivedHeapRows`先完成有界自主物理事务检查，再用调用者提供的来源绑定固定SCN基线重建。完整行Cell不能含ABSENT，来源包含DBID/activation/compatibility/thread/resetlogs和PDB，row key含data-object/DBA/slot。基线SCN必须在输入覆盖内，跨事务快照、并发重叠和回滚当前保守拒绝。变化列逆向必须等于当前缓存，未改列只从基线携带；实际物理extent必须一致。配对尾列证据成立后才将该尾列还原为NULL，不把其它ABSENT变NULL。

重建结果先缓冲，全部覆盖/身份/输入SHA检查通过后返回。固定schema以及logical=data-object范围仍为声明，不是历史字典；这是有限实验入口，public events不调用它授权发布。INSERT/DELETE代码分支存在但本轮没有新真实支持证据，不计验收。并发、回滚、行链、DDL/ABA、typed世代转换仍待证。

## 真实结果与失败

冻结seq25 SHA `6d593f143521236722386196a5f0908459419d305c1364d968637599d0b109fe`；基线SCN2282688，data-object73073、file12/block403/slot0。真实ASOF原字节来自已冻结setup-resume输出，本轮没有现场查询或源配置操作。

| 次序 | 完整before | 完整after |
|---|---|---|
| 1 | 10 / alpha / secret | 10 / beta / secret |
| 2 | 10 / beta / secret | 10 / beta / NULL |
| 3 | 10 / beta / NULL | 10 / gamma / NULL |

生产重建只读原始redo及基线。独立比较器从dump按完整UBA、native物理顺序和逆向值重建，另核提交后旧ASOF和最终SQL快照：3次完整行、2快照、零差异。包含commit在undo之前的真实record，未依赖vector末尾即commit的假设。

- 10基线反例：缺baseline/缺row、错误变化列before、伪初始NULL、过晚SCN、覆盖前SCN、错DBID、错PDB、错世代key、错slot均rc1/stdout0。过晚SCN实际拒绝原因NO_ROW_CHANGES_AFTER_BASELINE；没有将它冒充真实跨snapshot活动事务证据。
- 8既有重封checksum归档派生在新完整行入口均rc1/stdout0；漏变化、伪before NULL、错世代比较器反例均检出。
- 新派生顺序反例交换真实record的forward #1/#4完整vector，SHA `8bac7a287cf9f4bd666e8add1d102bf2cd902b7a8eeef38574380276d4183718`，scan通过。旧行重建b8cae3c仍rc0并输出与原始样本相同行，实际按undo而忽略重排后的forward顺序；最终jar整批`ROW_REPLAY_FORWARD_AND_UNDO_ORDER_DISAGREE`、rc1/stdout0。该派生没有另做Oracle native dump，结论限定为原始vector重排安全拒绝，不声称真实Oracle并发支持。
- **未通过的基线认证反例**：ID基线c10b→c10c而UPDATE仅改变TXT/NOTE，最终jar仍rc0输出3行。独立原生/SQL比较3处差异。这是未改列来源认证缺口，绝不能计为安全拒绝或基线验收通过。完整输出和命令rc在`unverified-untouched-key.*`/`native-rejects-untouched-key.*`保全。
- public events seq25/26仍rc1/stdout0，DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED，CDC0。

## 交接

接续[基线来源派单](DISPATCH_W5_BASELINE_PROVENANCE_20261004.md)，先把实际只读ASOF获取、冻结receipt、schema/物理列与源码入口连接，补错未改列/伪SCN/来源和跨snapshot边界反例。固定基线声明不能认证它自己；旧世代已TRUNCATE，现场需要新fixture而非重放旧轮次。完整事件、持续capture/ACK、接入和发布继续保留原目标。
