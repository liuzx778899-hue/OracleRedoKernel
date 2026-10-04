# W4 主键更新/DELETE 回滚与 index RESTORE 研究

在专属 `oraredo-19c-lab-20261004` 执行一次新的隔离表fixture，冻结seq18，捕获实际10.5 RESTORE布局。fixture脚本首次提交 `418cd0c`；研究源码完整封存 `d957a34`。生产内核仍是 `9e80b0f`、jar SHA256 `ef7092a43e3ff2681822f617092dd257e1c21fb33a6153580a76a898d461aaf8`，本轮不重建或重复计入138项测试。

## 动作及绑定

- 执行前核对容器owner/image/running，SQL核对DBID1276726821 / 已有ARCHIVELOG / MIN=NO / ALL=NO；没有修改模式、补充日志或重启。
- 新表 `ORKLAB.T_INDEX_RB_20261004` / heap73059、PK73060。基线ID1/2/3。
- savepoint事务 `10.17.1215`：先保留TXT1=`keep`，再更新主键1→101及TXT、DELETE2、INSERT4，rollback to savepoint后commit。回滚前后完整XID相同。
- full rollback事务 `10.14.1214`：更新主键3→103，DELETE1，然后rollback。结束SQL transaction id为NONE。
- 两阶段SQL行均为 `1=keep、2=two、3=three`，保留NUMBER/VARCHAR2 DUMP和物理heap行位置。SQL真值只独立验证，不输入事件生产。

输入seq18：5,215,744 bytes，SHA256 `5d5203bc42d6d3d68bf95b70f39631214bc78096e78ee6d5975e2036f3948abd`。实验SCN窗口[2207226,2207283]；raw归档包含窗口外内容，native诊断dump明确按该SCN窗口限制。新对象所有相关选定vector均在窗口内，未把有界dump当作整个seq18结构全量对账。

## 新字节证据

| 输入 | 数量 | 本轮验证 |
|---|---:|---|
| 5.1 / index10.22 undo | 10 | 完整XID/UBA、metadata、leaf字段、key及restore keydata |
| 10.2 insert leaf | 6 | leaf字段、key/keydata；包含主键101/103的较长NUMBER字节 |
| 10.4 delete leaf | 4 | leaf字段与字段数 |
| 10.3 purge leaf | 3 | leaf字段与UNDO模式 |
| 10.5 restore leaf | **4** | 6字节leaf、UNDO模式、ITL/slot/rowSize与字段数 |
| index5.6 compensation | 7 | metadata与extra直接字段 |
| 10.31 create/load index | 1 | 清单位置/opcode/DBA/SCN，语义未认证 |
| 合计 | **35** | raw/native位置及所列字段零差异 |

四条10.5位于seq18 RBA(block:offset/vector)：10170:68/1、10171:120/1、10178:432/1、10179:484/1。本批restore为KTB C16或L32 + leaf6，leaf模式1、ITL2、slot分别1/0/0/2、rowSize13。L引用的是历史基线事务10.31.1214，不是当前回滚事务；C引用也不是补偿原始undo的充分身份。未知/不解释字节继续原样保留。

## 当前内核拒绝与研究反例

实际record中的4条10.5与同record唯一目标5.6传入现有matcher，均返回 `UNSUPPORTED_INDEX_PURGE_LAYOUT`。研究harness捕获预期异常，命令rc0；这4项不是事件命令rc1，也不是完整活动身份/历史已绑定的正验证（harness传空history）。只定位目前不支持RESTORE的入口，不能计为RESTORE支持或完整安全验收。

最终封存研究源码重新编译驱动，raw/native35零差异；旧seq8/9的35项直接字段及6项研究反例回归；新增一项真实10.5 leaf模式篡改被研究对账器检出，合计7项比较器反例。`scan`对seq18 rc0仅证明既有结构scanner通过，`events`仍rc1/stdout0。Java生产代码无改动，旧138测试本轮未执行。

执行SQL导出绑定Git archive脚本，分别保存执行与封存SHA；只对源码CRLF→LF核对内容，raw redo/dump不归一。所有源端命令、rc、stdout/stderr、输入及制品SHA见 [研究清单](evidence/RESEARCH-index-restore-20261004.json)。本地 `lab/out/oracle19-index-restore-20261004/` 与 `lab/out/release-index-restore-research/`；DML不重放。

## 接续

下一步从上述真实字段实现普通5.6 + 10.5 RESTORE叶操作匹配，绑定原始10.4 DELETE及对应RESTORE undo；补当前活动完整XID、物理slot/size、唯一历史候选、重复取消和拒绝锁定反例。与本批3条PURGE一同验证7个索引取消，再核对含主键两列UPDATE的heap+index混合有序图。

本批生产事件、完整DML、index RESTORE支持、并发/slot迁移/恢复、SCN字典/基线、持续capture/ACK、StreamHouse接入、现场验收及发布仍未完成。专属实验DDL/DML/归档不算生产源端零侵入证明。
