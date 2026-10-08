# D2 真实跨成员 SYS：新缺口与最小 marker 修复

本包生成并冻结了真实跨成员 SYS 事务，修复其前置 marker 识别缺口；**尚未实现完整 pending SYS 恢复或历史 DML 支持**。真实前缀/闭合均仍 rc1、stdout0，不能把安全拒绝当正功能。下一步基于冻结输入修 COL$ 集群链维护，不再获取 baseline 或重放源动作。

## 候选代码与验证

代码 **93801dee3e0471e5fd45a70224264ab48eb956ae**，分支 codex/d2-normal-index-restore。真实 sequence80 CREATE TRIGGER 的24.1 marker具有46字段、12字节主对象字段，但次影响对象字段为空。此前一律要求该字段4字节，实际返回 UNSUPPORTED_HISTORICAL_SCHEMA_MARKER。

现在仅识别该已观察的59/2头标签、主对象结构和对应20字节辅助字段，保留主对象及完整 owner/name 的范围闸门。SQL文本不参与判断，未知形态仍拒绝；目标数字身份或精确名称碰撞仍拒绝。新增两项测试覆盖这些范围与损坏反例，针对性15/0/0/0，完整 **339/0/0/0**。首反例测试只清除对象号低字节，未真正变成零对象，导致预期错误；原15/1报告保留，修正反例输入，没有放宽生产守卫。

完整提交树 git archive source SHA256 **fa3c2334baa3746a1ba21818a91bdd3c224234bcc2822fad5f97c549a9155a95**；正式 kernel SHA256 **6d633505824569b76fc87aef28e881d6f85f6d8ba944fe57e0da903791f884e8**。打包跳过重复测试，测试证据来自此前同提交内容的完整回归。产品仍原kernel2690e584，未换装或新增发布。

## 新实际源与独立尺子

仅读先行可信 acquisition101584476177633280/SCN2899589/snapshot7c435791；旧75–78不重放、不晚SCN回填。专属实验容器一次ADD/DROP及两个UPDATE，临时限定本 fixture/client identifier 的延迟 trigger仅用于实验，已经删除。完整原行与三列结构恢复，NO/NO、旧行保持；未要求生产触发器或任何源配置改变。

等待期间真实会话 XID **10.26.3522**。独立 native dump明确：begin **80:51459:16 / SCN2900129**，commit **81:28:308 / SCN2900180**，20原始 undo节点横跨两成员。这证明实际跨成员未提交状态的存在，不证明节点已完整处置或DML支持。native只作尺子，事件生产仍原始字节路径。

冻结原始80 **26349568bytes / c220216ab1cf13311c6b74b6e3dd4f29541ced107d3331a6ef1e0ab73023673d**，81 **26624bytes / 9b14542f7181eecf3fa9e49086eb823394fc82582408dc75656201f3f2b23e0b**，各与源归档独立SHA核等。正式候选前缀及两成员闭合均 **UNSUPPORTED_DICTIONARY_DISCOVERY_ROW / rc1 / stdout0**，未生成checkpoint、目标操作0。

剩余具体位置：80:51452:324与80:51453:100、SCN2900125，逻辑COL$21/data2、32字节KDO10。native对应SKL cluster link维护、slot4；既有基线COL$完整值位于同块slots13–15，TAB$slot3。不得仅因不是这些值行的slot就跳过链维护，必须绑定同记录前后向量、完整XID/UBA、物理链及基线影响，再核全部原始节点处置/恢复。源动作不重放，只读冻结80/81继续。

## 封存与下一项

**d2-sys-cross-sealed-20261008，214项双SHA封存**；manifest SHA256 **98cfab6f9f21f81132619f93cc64f87038ed80e2b33dde76a1c2b769cad67fa3**。公开证据已知秘密扫描0；原始文件和二进制以双SHA引用保全，未宣称原始数据脱敏，不入Git。不得追加封存，后续修复输出另建目录。首次marker/发现行失败、native尺子误用CLS36（实际commit35）、首封存误用UTF16解码均保留，不当产品反例通过。

方法依据：[Oracle19c client event trigger事务说明](https://docs.oracle.com/en/database/oracle/oracle-database/19/lnpls/triggers-publishing-events.html)、[DBMS_SESSION.SLEEP](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_SESSION.html)。格式结论来自本轮真实字节/native，不复制参考实现。

下一先修真实SKL链缺口并核完整pending SYS→闭合→字典/事件及跨进程恢复，再集成产品目标CPACK；不堆已通过项。D2其它场景、D3连续采集及轮转压力断电/监管/Windows网络、D4类型LOB、D5 RAC/ASM/TDE、独立目标与评审/部署/用户生产/现场发布完整保留。PR1/13仍draft，不能自验收；root STATUS保留移交快照。
