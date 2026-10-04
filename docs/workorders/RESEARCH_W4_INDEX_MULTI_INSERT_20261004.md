# W4 多次并发 INSERT 位置研究

同叶位置重放不能只由一次较小键插入证明。本轮在已授权专属19c源中新建有限fixture，A删除中间键50并保持未提交；B依次插入20、70、30并提交，A随后回滚。冻结raw及native显示B的较大键70落在目标后方，只有20和30推动目标位置；**同件已交付内核jar成功把A原slot1关联到RESTORE slot3**。没有扩大到其他中间操作、完整混合图或逻辑DML。

源动作脚本提交d3fd77b，研究驱动完整树 `c469cd326ed01d194c49f4339933a36db239c898`；main/test/pom与已验收内核aa44b0d差异为空。使用[上一轮交付](DELIVERY_W4_INDEX_POSITION_20261004.md)封存的167测试jar，SHA256 `4db98241abfea8579e2502563eca666375e8fb32724a0af403a8a19341e178a3`，没有重新构建或重跑已通过回归。[研究清单](evidence/RESEARCH-index-multi-position-20261004.json)关联源脚本/完整树、raw、dump、SQL真值、jar、编译器/运行命令与输出，双次重算SHA一致。

## 源与时间顺序

专属容器 `oraredo-19c-lab-20261004` 的owner/image/running三项先核验。SQL在DDL之前检查DBID1276726821、ARCHIVELOG、MIN/ALL均NO；没有开启supplemental或更改模式。新增表 `ORKLAB.T_INDEX_MULTI_20261004`，heap73063，PK73064、leaf DBA0x0300014b。baseline键10/50/90先提交，A完整XID10.29.1230删除50，B完整XID9.16.584插入三键后提交。A ready后才启动B，B返回时A进程仍活着；A最终rollback后本地事务ID为NONE。所有源动作rc0，只执行一次。

源SCN：A删除后2265369，B提交后2265373，A回滚前2265387。最终SQL键10/20/30/50/70/90及文本ten/twenty/thirty/fifty/seventy/ninety全部存在；该真值只验证源动作，不当作取消图或事件证明。

seq20完整归档14,153,216 bytes，SHA256 `620c6361a3986fee40a18ccafb68e9b6a6b2cb9f59fc9db10fb70ce510aea400`。native dump仅有界SCN2265333..2265401；不存在把选定dump当成整归档完整语义认证的问题。raw大文件留本地 `lab/out/oracle19-index-multi-insert-20261004`。

## 独立对账

| 操作 | raw/native位置 | slot | 键字节 |
|---|---|---|---|
| A DELETE 50 | 20:27625:148:3 | 1 | 原始undo携带50，当前位置比较未单独认证DELETE键字节 |
| B INSERT 20 | 20:27626:328:3 | 1 | 02c115 |
| B INSERT 70 | 20:27626:328:5 | 3 | 02c147 |
| B INSERT 30 | 20:27626:328:7 | 2 | 02c11f |
| A RESTORE | 20:27632:16:1 | 3 | 补偿不携带键，关联原始undo |

三次B插入位于同一record，顺序必须用vector位置，不能按SCN强制递增或按键大小排序。native独立提取所有五个leaf操作的RBA/vector、opcode、record SCN、ITL、slot、row size，与raw字段零差异；三次INSERT键字节也零差异。原始A index undo完整UBA从native同record5.1/10.22提取为37759368:245:21，新matcher输出精确为：

```
MATCH|20:27632:16:1|10.29.1230|37759368:245:21|RESTORE|1|3
```

旧静态matcher同输入rc1/stdout0，`MISSING_INDEX_COMPENSATION_ORIGINAL`；新owned positions matcher rc0，唯一匹配输出如上。`compare_multi_index_position.py`期望来自独立native与源动作，不从matcher输出计算，最终mismatches0。

## 状态与下一步

本轮无内核修改，新增一次较小/较大/较小键连续插入的真实证据。**A/B完整heap/index图尚未在此新输入上核验**，新API在本批的支持只限一个index补偿；不能借上一批2/2/0和2/0/2填本批图计数。配置不变及源动作成功也不证明完整零侵入CDC。

下一步用这份冻结输入补A/B完整分配/取消图与漏中间vector反例，之后用专属新fixture研究前方PURGE是否移除slot目录。普通DELETE、PURGE、RESTORE及未知维护作为中间操作仍fail closed；W5字典/基线、W7持续capture/续接/ACK、部署/现场/发布继续待证。
