# W4 跨undo block控制身份与扩展提交标记交付

源码`b99828ed28ce278bb6d39ac67c9169dfd070601c`，main，完整提交树独立重建87项测试0失败0错误0跳过。jar SHA256 `a448066f01459983ec4ed1e59ec4f05c644fda34ce7ba34ff12d460f1b54e3de`。[封存清单](evidence/DELIVERY-b99828e.json)绑定同件jar、输入、native comparator、正反例及失败日志。本轮使用已冻结seq10；无DDL/DML重放、部署或目标写入。

`UndoBlockAdvance`解释观察到的5.2 flags0x000a，32字节控制字段及可选36字节分配附加字段。后者只做布局限制，不解释其分配语义。控制sqn为0，不能当完整事务sequence；按同record/CON_ID显式新UBA绑定唯一5.1普通undo，再由活跃segment/slot/完整XID匹配确认归属。新UBA record必须1；header必须有非零previous DBA和rci0，拒绝begin/parent/wrap错配。它不生成完整前驱UBA，`UndoLink`跨块分支继续拒绝。

`TransactionLifecycle.advance`校验该控制vector属于当前record、来源归档和活跃完整身份，并拒绝同record多个控制标记归属同一新UBA；拒绝后生命周期锁定。当前只验证控制身份，不重建cursor顺序/完整前驱链，也不承诺不同record间所有重定向反例已接受安全验收。

`TransactionEnd`新增真实5.4 flags0x12的20/16/24/4字节结束形态，校验已观察24字节附加字段常数与重复时间字节，其额外控制含义未解释。仍从16字节ktucf读取原始tail。该提交tail0x02400089.00e7.04指向原始最后undo，不能当成保存点撤销后的生效末操作。

同件jar从raw seq10验证长事务1.1.604：1个begin、120个undo、15个显式跨块UBA控制、1个扩展提交身份闭合。独立native dump对17个控制/起止位置及字段零差异。该结论是身份控制闭合，不是完整跨块undo图或60次撤销语义验收。

9项真实vector内存反例覆盖：不存在的新UBA、重复候选undo、缺已验证begin、错record UID、错完整XID、无previous DBA、marker不在record、同record重定向至另一已有控制UBA、结束附加时间字节错配；9/9预期拒绝，8个生命周期拒绝后锁定。harness rc0为审计成功，stdout是拒绝报告，CDC事件0。

初轮单测只清零previous DBA一个字节仍非零，失败日志保留；修正测试为四字节全零后通过，未为此改parser。初轮真实反例把新UBA加1，恰好指向同record另一个合法候选，曾被接受：保留NEGATIVE_ACCEPTED:0原文，新增控制UBA唯一归属校验后将该反例保留为第8项，正式封存验证拒绝。不能把它简化成不存在地址后忽略实际缺口。

同件jar还回归302个KDOM2正向/逆向patch、SQL最终keep-060字节及旧seq8/9的18个精确pair，native全部零差异；21项KDOM2/未证跨块入口拒绝继续通过。`events`rc1、stdout0、DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED。

同期更正[undo快照研究](RESEARCH_W4_UNDO_SNAPSHOT_20261004.md)：0x02400088原始节点已经sequence0xe7，并非事后变化；页头XID不同仅使采用未证身份假设的研究器保守拒绝，不证明页面复用。原执行与拒绝证据保持，错误推论撤回。

六列：控制身份代码交付/针对性验证通过，完整W4集成进行中，部署/发布未开始，现场验收待证。下一步将显式UBA切换、原始节点图和60补偿联合，证明跨块前驱及缺块/缺补偿/错误tail的拒绝；随后推进SCN字典/基线与StreamHouse接入。W4未关闭，目标继续。
