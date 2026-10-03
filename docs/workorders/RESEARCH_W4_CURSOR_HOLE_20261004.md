# W4 跨块cursor局部约束的缺节点反例

研究源码b64a090；[独立清单](evidence/RESEARCH-cursor-hole-20261004.json)。生产源码仍0445b43，同件jar SHA256 `931840b788e41def334f91a4f2e222edf28ffe63162d2f883bdbcaaaba8d40c9`。本轮没有数据库动作、归档修改或生产parser修改，未重跑93项生产测试。

## 可证伪假设与方法

假设：“相邻可见undo cursor + previous DBA + 唯一5.2切块控制 + 最终5.4尾引用”足以证明原始事务图完整。

`lab/UndoCursorProbe.java --omit-cross-predecessor`在内存选择阶段刻意跳过第一个跨块边界之前的最后一个原始undo节点。归档完整字节及SHA不变。研究模式把原有fixture专用计数断言从120改为119，以隔离局部链约束；默认模式仍严格要求120。不能将研究模式返回0描述为正式parser安全漏洞，也不能声称旧的120节点覆盖断言失效。

源码从完整提交的Git blob封存；Java17研究driver只读raw归档，独立Python比较器读取此前冻结的Oracle native dump。运行器`lab/research_cursor_hole.py`拒绝覆盖已有commands清单，不发Oracle命令。

## 实际结果

| 冻结归档 | 完整XID | 被省略UBA（十进制DBA:seq:rec） | 可见节点 | 块切换约束 | 局部cursor rc | native全量rc |
|---|---|---|---|---|---|---|
| seq10 | 1.1.604 | 37757297:229:33 | 119 | 15次通过 | 0 | 1 |
| seq11 | 10.23.1156 | 37760236:242:20 | 119 | 15次通过 | 0 | 1 |
| seq12 | 3.26.669 | 37756974:399:37 | 119 | 15次通过 | 0 | 1 |

三个默认正样本仍各120节点、native字段零差异。三个缺节点样本的最终提交尾引用仍与native一致；独立native全量比较均发现两项错误：被省略位置`DIRECT_FIELD_DIFF`和`COVERAGE_OR_END_TAIL_DIFF`。归档扫描与原有事务身份生命周期本身不是图完整性判据。研究输出不是CDC，事件发布仍关闭。

首轮预扫描对无关事务直接调用严格UndoHeader decoder，遇到`UNSUPPORTED_UNDO_HEADER_FLAGS`，rc1；修正为先按目标完整XID筛选再解码。pilot原失败和第二次结果均保留并纳入SHA清单。

## 对落地方案的约束

局部cursor假设被反例否定，不能单靠正样本120节点或最终SQL一致来升级完整跨块图。下一阶段必须先完成以下证明：

1. 从绑定来源的完整archive/record/vector流形成事务输入，任何选择、解码或未知相关opcode造成的遗漏都显式拒绝；调用者任意提供子集不能获得“完整”状态。
2. 跨块前驱的完整sequence/record需要独立依据，或一套证明输入完整性并能拒绝本反例的流状态规则。当前previous DBA和控制新UBA不直接编码完整旧UBA。
3. 将此真实派生遗漏、同块遗漏、重复/重排、错控制身份、缺archive和槽位复用作为独立拒绝门禁；计数不能硬编码成fixture长度后冒充通用完整性。
4. 完整原始图与补偿后的存活效果分别验收，包含index undo、恢复边界和W5字典/基线。通过前继续保持`events` rc1、stdout空。

本研究不改变已验收的有界heap补偿匹配、93项测试及23个跨块5.11的结论；完整W4、W5和StreamHouse接入仍未验收。
