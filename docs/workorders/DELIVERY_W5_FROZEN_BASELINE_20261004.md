# W5 真实只读基线获取与回执校验导入

状态：专属源真实只读获取、固定获取回执下完整导入与篡改拒绝通过。生产回执可信交接、typed世代重建、历史字典及CDC/现场/发布未验收。

## 制品及实际获取

- 运行时/获取脚本完整源码`b1ff3af`，独立比较器`014297a`；217测试0失败/错误/跳过。jar SHA256 `5967c7e44aeeda4f59c886d4c7983c5336551a66ddd786798314ad309bde05fc`，目录 `lab/out/release-frozen-baseline`。
- 获取脚本 `lab/capture_heap_baseline19.py --execute lab/out/oracle19-baseline-acquisition-20261004` 实际执行成功。仅专属容器检查、复制本次只读SQL脚本、源身份/字典/ASOF SELECT及本连接session切换，没有DML、DDL、归档切换或数据库配置变化。
- 实际SCN2341293，CDB DBID1276726821、activation1276717861、resetlogs1245693159/SCN1920977、thread1、PDB UID677737149/ID3；AL32UTF8。
- 完整快照原件SHA256 `36e3c954dd0844367bbb2bb9bec8c9b1f63f4069b1c1b01aa6239f62792f220d`，查询SHA256 `916f8ed0de320ba1614e331efac48debe858bb6fefb956a6805bccb1dbecd720`，完整命令/rc/stdout/stderr和receipt独立保存，原件不入Git。
- 实际heap logical73073/data73076、index logical73074/data73075；物理列SEGMENT/INTERNAL依次1/1、2/2、3/3。NOTE HIDDEN_COLUMN=YES仍是物理第三列，不能用visible COLUMN_ID重排。
- 一行data-object73076/file12/block403/slot0，完整字节对应10/new-epoch/new-note。ASOF与ROW_COUNT同一固定SCN，查询前后对象/物理列及CDB身份核一致；这不证明整个历史区间无DDL/ABA。

## 导入及可证伪结果

`FrozenHeapBaseline.read`必须拿到获取环节外部交付的原SHA，先按完整原件字节核对，再严格解析有序版本/来源/PDB/字符集/SCN、heap/index角色、物理列、完整行与行数、前后schema/source及END。拒绝部分输出、重复位置、错字段数、错世代、非完整列、虚拟列和当前未支持格式；导入结果不授权CDC。

| 检查 | 结果 |
|---|---|
| 实际查询导入 | 1行、3列、完整source/SCN/角色/字节与实际SQL输出独立零差异 |
| 实际源身份交叉验证 | 5个身份字段与冻结seq26原始header及原生dump零差异；这里只证明同源身份，不证明当前snapshot SCN被旧seq26覆盖 |
| 原回执下9份篡改 | 改未改键/NOTE/SCN/PDB/DBID/世代/物理map、漏row、截断均rc1/stdout0，即使各副本自报了新SHA，仍不能代替原获取回执 |
| 同时伪造回执authority | 伪ID副本配上其新SHA，仍rc0；独立比较器检出2处差异（原件digest及完整行值）。明确证明哈希不认证回执签发者，生产控制器必须信任实际获取进程，不从被读文件/旁边可编辑manifest自取“可信SHA” |
| 世代到旧接口转换 | actual snapshot正常导入，但equal-object旧scope转换整批UNSUPPORTED_SNAPSHOT_LOGICAL_DATA_OBJECT_MAPPING、rc1/stdout0，不能把两个世代ID折叠取得通过 |
| 比较器反例 | 漏row、错heap data-object、错internal物理列均rc1检出；不是内核拒绝的替代 |
| public events | seq26仍DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0、CDC0 |

首次独立比较器直接将SCN高半字0x8000作为数值，造成resetlogsScn140737490276305错误，rc1与完整原文保全；修正屏蔽标志位，并另核原生dump的1920977。没有重跑获取或已通过导入，只补比较器和未执行项。

上一轮直接构造`ArchivedHeapRows.Baseline`仍是实验API，伪未改键反例不因新增导入器就自动消失。新导入路径在可信获取回执边界下关闭文件篡改；public事件尚未连接生产可信控制器，整体基线来源验收仍进行中。

## 资料及接续

实际SQL字段依据Oracle19官方 [V$DATABASE](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-DATABASE.html)、[V$DATABASE_INCARNATION](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-DATABASE_INCARNATION.html)、[V$CONTAINERS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-CONTAINERS.html)。格式行为由本次源输出与原始字节验证，不复制外部参考解析源码。

接续typed heap/index logical→data角色scope及该snapshot到新DML的完整行链路。当前SCN晚于旧seq26覆盖，不能拿旧输入冒充后续重建正例；需要一次性新受控fixture/新归档。补真实跨snapshot初始事务与可信获取交接，不降低历史字典/持续capture/接入/发布门禁。

证据清单：[DELIVERY-frozen-baseline-20261004.json](evidence/DELIVERY-frozen-baseline-20261004.json)。
