# W5 历史 schema 反例及 raw marker 屏障候选

结论：真实 ADD nullable 列→稀疏 UPDATE→DROP 恢复→UPDATE 证明旧基线可输出伪完整事件。候选内核在完整 batch materialize 前拒绝；同候选产品的实际认证 HTTP 首次/恢复均拒绝，目标、控制 checkpoint 和 ACK 零推进。**仅观测到的19.3 raw 24.1屏障，历史字典整体未验收；作者自检，独立评审PENDING。**

| 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|
| 候选已交付 | 有限反例/259测试通过 | 候选产品735测试、实际HTTP零推进通过 | 未换装；专属helper已停止 | 待证 | 未开始 |

## 真实失败和独立尺子

新专属 `ORKLAB.T_SCHEMA_BARRIER_20261005`，heap74695/index74696，基线由现有只读实际source acquirer取得，SCN2501884、3物理列/1行/1隐藏列，SHA256 `6498d7223c2e5383facc9e3f848f22dd8cb3272460cf7c50b20a29d34d5379d5`。本轮不是新的中央 acquisitionId 接入验收。

源动作一次性结束，不得重放：ADD EXTRA nullable→UPDATE旧TXT→COMMIT（XID7.33.618）；DROP EXTRA→UPDATE旧TXT→COMMIT（XID10.26.2162）。真实metadata为3→4→3列，首尾每列定义、heap/index object与data identity一致；真实ABA成立。原始seq56/57关闭capture共134/22块，与归档数据块零差异，online头保留未归档头归一。native独立三图4原始/0补偿/4live零差异，包含基线之前seed事务。

旧内核 `960954207b626be4d19db547ce2a9e3c5245075e84edb7196935ba3c0489c55e` rc0输出2个三列UPDATE；ADD后第一事件缺EXTRA=NULL，尽管补丁及事务图合法。错误事件原文、基线、两次metadata/SQL truth、capture/dump均保留；最终行相同不能验收历史事件。

## 最小修复及执行边界

`HistoricalSchemaBarrier`仅扫描原始record/vector，不读取dump/SQL truth，不解释marker内SQL文本。独立字节实验观察到同PDB 24.1的46字段形态、field3/11数值角色及field8/9名称角色；基线后任何匹配heap/index的marker拒绝，未知marker形态不能证明无关亦拒绝。已覆盖基线前marker、不同container及明确无关marker；没有将所有系统事务统称DDL。接在可信基线物理入口，所有事件生成前完成。

修复内核SHA256 `9275ab9062413ae7fa88ac3a147337fbe1cc1d9b1c7add834f374c401a9570c1`，同raw rc1/stdout0，原因 `HISTORICAL_SCHEMA_BARRIER:scn=2501918:object=74695`。259测试0失败/错误/跳过；受影响混合DML与独立空batch冻结输入字节结果不变，无源动作重放。源码执行SHA和Git源码分别记录，不用后续提交时间冒充执行时间。

内核代码交付 `f24a881`；内核在执行工作树构建后提交，源码SHA与提交后的工作树字节核对，不能写成“先提交再构建”。产品代码e4d3aa9/交付文档f5f73fe，[草稿PR#12](https://github.com/liuzx778899-hue/StreamHouse/pull/12)，依赖#11；应用SHA256 `879d6698b27c0fb58935d59f905850d0b8e4ea2a34c149e40102a4b58a5265f6`。均未合并/换装。

产品代码树 `e4d3aa9`仅换私有内核坐标 `0.1.0-historical-marker-9275ab90`，完整clean verify735/0/0/0，包内唯一内核SHA已核对；adapter50feffa8复用，未重跑未变模块的19项旧构建轮次。认证HTTP task100605123436298240，首次和恢复两个REFUSED批次rc2/event0，准确原因字节独立SQL核验。新控制schema ORKSCH_20261005/新SCBAR目标：行仍0/schema-seed/anchor，applied/member/commit/ledger0，CP=NULL，窗口PENDING，ACK目录无文件。目标/setup DDL属于专属实验，不是生产变更；目标仍同实例不同schema，不是独立目标实例。

负输入使用旧错误投影作为故意登记的受控反例，不当作正确envelope或新的获取账权限；实际产品重新从raw解析先拒绝。独立比较器的目标写入、CP推进、错拒绝原因三个派生反例均检出。loopback37220/helper `ork-schema-barrier-http-20261005` 已停止exit143；源容器未重启，NO/NO及旧11/blocking-closed/new-note保持。

## 证据和遗留

唯一原文目录 `lab/out/schema-barrier-20261005`：源码/制品/执行pin、259及735报告、命令rc、原始失败、真实反例、原始字节字段、SQL和HTTP完整输出；非secret文件两遍SHA封存，摘要见 `evidence/DELIVERY-historical-schema-barrier-20261005.json`。首次新增测试Snapshot构造参数顺序错误导致编译失败，原文/rc保留，修复后259通过。首次SQLPlus原因显示换行保留，另用HEX精确核验字节，未将显示rc0当业务通过。

最终443个非secret文件双SHA，真实秘密值扫描0命中。HTTP启动driver执行前SHA对应原始字节已按相同SHA恢复保留；后续仅加两条只读原因查询分支，末次源码封存和执行前pin分开。首次封存摘要目录错误rc1原控制台文本保留，修正为现存docs/workorders/evidence；写入中的封存日志不当最终hash，最终静止文件重新封存并逐项复核，不重复已通过解析/DML/产品轮次。

当前未证明marker缺失、跨窗/跨SCN活跃DDL、未知字典操作依赖、间接依赖、21c/其他版本的完整覆盖；不得把识别到两条marker称历史字典完备。公开生产decoder仍关闭。接续 [下一单](DISPATCH_W5_SCHEMA_COVERAGE_20261005.md)，三库V731 NEW/UPGRADE、活跃跨窗/租约、C++/独立目标、Windows/ASM/高压/断电、独立评审/现场/发布仍保留。
