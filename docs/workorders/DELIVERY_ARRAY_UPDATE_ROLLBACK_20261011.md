# ARRAY UPDATE 回滚及恢复续接

冻结 raw116 的整笔批量 UPDATE 回滚、savepoint 批量 UPDATE 撤销已从原始字节完成解析。只发布保留事务 `10.20.5217` 的3条完整 UPDATE；与独立获取的 `after.snapshot` 三行完整值差异0，撤销事件0。源脚本只执行过一次，本次没有数据库、获取、归档或部署动作。

修复范围：11.19 XR 归属匹配、三行18字节聚合头及实际8字节 KTB-Z/16字节 KTB-C；分别要求锁0/恢复原锁，所有原始行槽、旧值及 undo 身份必须一致。较短列表继续拒绝，不能取消整个节点。

- 重新编译后26项数组/物理事务回归及8项单行补偿回归通过，失败/错误/跳过0；`tests-r3.rc`、`tests-compensation-r3.rc` 均0。
- `projection-r5.rc=0`，只输出3事件。两个独立 JVM `enroll-r1`/`resume-r1` 均rc0，事件、批次及投影证据与直接读取完全一致。
- raw116 SHA256 `cab026f8824bbcb288b261d6ceff601224b2ce7995c8859f876442564ef90e3b`，6910464字节；基线 `376bc8480fda62bb5a7651c7a2d16d0065588b07d492f2f87e156dfdd2ee3a11`。
- checkpoint `774da18887345fc073b7c5251ace0e465cd4118d0a013b193f64cec656d0ee90`；规范化事件 `ba550f817add8bffcc9611f720412a0ede1d616f791b7e350381d350cd717026`。
- package rc0，内核 `f02b15ca2e49450d196d8e82999b38a3cd03eba5986fdc40f8f04d080c33adea`；分支 `codex/d2-normal-index-restore`。未更新产品固定坐标或部署本候选。

证据：`lab/out/array-update-rollback-20261011/`，独立对比 `source/verify-r1.json`。复现 `lab/array_update_rollback_verify.py` 只读取冻结文件，输出存在时拒绝覆盖。原三次解析失败及旧编译证据保留。

仅关闭完整归属节点的批量 UPDATE 补偿与恢复。本轮五项未全部完成：任意节点部分行补偿、LOB 历史/代际/恢复/安全回收仍待开发；同 SCN 合成证据不冒充真实双窗，已通过时间及产品正常链证据复用。后续统一更新产品、合并 main、清理自己的分支并给出重新编译部署清单，然后进入模拟生产；D5搁置、TDE不启动。
