# W5 产品真实V3获取与恢复候选

root代码 `0f2838b`，私有产品代码 `a40db7b`；[407项封存清单](evidence/DELIVERY-v3-product-acquisition-20261006.json)双SHA及封存后核验一致。旧目标216、协议241、投影75、完整行189、真实raw374全部逐项未变。共享产品checkout未修改，禁止Luna/子代理，原完整goal active。

产品新增认证 `POST /api/v1/oracle/acquisitions/historical`，只接受server已配置的sourceId，实际调用adapter `acquireHistoryBound`；原V2入口保持。V3完整dictionaryBaseline（SYS布局、全部行值与三索引身份/DBA）和六anchors一起持久化，恢复必须逐项匹配快照。历史binding需要V3，只有V2 anchors不能替代。未引入任意文件/SHA/authority回填入口。四项新增测试加原测试扩展，完整reactor750/0/0/0；未重跑kernel299或adapter26。

产品SHA256 `c47a728ceea5bbf0e989ef03c6858e4033e27d4fb7eee99a22cc39b1519bec49`，实际嵌入kernel240c2250和adapter65e0fcba，jar逐项匹配。专属helper `ork-oracle-v3-product-20261006`，端口37261，owned控制schema `ORKV3CTL_20261006`；source为既有专属实验容器，不换装其它服务，不碰旧37215 helper。

## 实际获取与独立验证

真实认证HTTP获取 `100747510573780992`，SCN2566695，snapshotSHA `323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2`，状态ACQUIRED_ONLY。格式V3；独立同SCN DUMP/布局/三索引与业务基线零差异：6 SYS行、230值、3索引、业务行0/history-v3-restored/anchor。产品metadata全布局/行键/原始值/索引与独立frames完全一致。

未认证HTTP401；客户端自填authority返回非成功code400。实际GET对截断文件及五项账metadata反例（缺历史、缺布局列、错SYS值、错索引DBA、错索引identity）均拒绝，每项精确恢复后GET全部持久字段相同，快照字节相同。同件停止143/重启后再次GET全字段与SHA相同。控制审计binding0/event0、六拒绝记录、源NO/NO及旧保留行不变。本轮仅创建owned控制schema、获取和受控控制账反例，0源fixture DDL/DML、0日志配置变更、0业务目标动作。

失败保留：比较器首轮把JSON字符串SCN当数字、第二轮把小写hex与大写frames比较；只修表示层类型/大小写，复用已保存只读结果，不重新获取或源查询。恢复比较器首轮将POST纳秒时间戳与Oracle落库微秒精度比较；改以首次持久GET为恢复基准，全部字段严格相等。首次长CLOB注入SQL*Plus SP2-0341，命令rc0但SP2明确判失败；GET核metadata未变，分行修复后五项反例真实通过。源码、SQL、原错误与恢复记录均封存，未删除失败。

| 状态 | 结论 |
|---|---|
| 代码交付 | 通过：V3获取入口、完整metadata恢复、历史绑定门禁 |
| 针对性验证 | 通过：完整750及真实获取/六恢复拒绝 |
| 集成验证 | 通过有限获取/同件重启；历史发布HTTP/CPACK待证 |
| 部署 | 不适用：owned helper；生产服务未换装 |
| 现场验收 | 待证；独立评审PENDING |
| 发布 | 未开始 |

当前helper经inspect确认running，auto-consume关闭，目标prefix `V3HP` 尚未建表、未批准窗口、无ACK。快照与已封存文件不得覆盖；后续新日志/脚本放独立 `lab/out/v3-product-history-20261006`，产品需要访问的capture作为新文件加入runtime目录，不修改407项旧文件。

下一步该实际获取之后一次新ADD nullable/UPDATE/DROP/UPDATE，冻结原始capture→完整历史factory/逐事件schema→关联实际获取账→真实产品HTTP→新owned目标→CP/ACK、重复/重启/拒绝全字段独立核验。旧seq44—69不重放、旧68/69不接晚SCN新账；按[执行单](DISPATCH_W5_V3_PRODUCT_RUNTIME_20261006.md)继续。PR#13 draft依赖#12、独立评审PENDING；活动跨窗、正式C++采集/租约、独立目标、类型环境/压力断电/现场发布仍待证，全部Oracle目标完成才查Codex卡顿。
