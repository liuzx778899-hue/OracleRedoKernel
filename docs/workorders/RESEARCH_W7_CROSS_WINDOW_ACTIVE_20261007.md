# W7 跨窗活跃事务：真实新输入与格式拒绝

状态：**采集及独立比对通过；完整事件与跨发布恢复未通过**。无生产代码修改、无产品换装、无目标或 checkpoint/ACK 动作。复用 kernel a84db1a3/adapter fbf1d30c，不重跑已通过套件。

新可信 V3 获取 SCN **2743256**、SHA256 `3619a433adcbbb336a67877fdaeba9c8e46fd69a841e3c955d28affb4627d79a`，独立 ASOF 校验六 SYS 行/230 字段/三索引/一业务行，零差异。该基线仅用于其后的新 73/74，不回填冻结 70/71。

专属容器 `oraredo-19c-lab-20261004`：新 XID **10.14.2869** 一次执行；第一次 UPDATE 后保持 SQLPlus 事务活跃、不发送 commit，关闭序列73。进程 poll=null 与 PENDING_READY 已保留。第二次 UPDATE 在同会话恢复原值并提交，再关闭74；两次源动作禁止重放。最终 NO/NO，业务行0/history-product-restored/anchor，旧表11/blocking-closed/new-note保持。无 DDL、权限或日志配置变更。

采集 terminal rc0/next75，219166 与23个观察块；归档数据块219164/21全部无缺失、无差异。原online header未归一化，`capture_complete=false`；不能当正式 C++/实时采集或发布证明。最终 `/proc` 无该类采集进程。

完整原件入口：

- 仅73：rc1，stdout零字节，`INCOMPLETE_TRANSACTION_IDENTITIES:1`。符合未闭合事务拒绝；探针在采集完成后执行，活跃会话观察来自73关闭时的独立进程记录。
- 73+74：rc1，stdout零字节，`UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE`。这是待修复的完整解析拒绝，不标业务事件或跨窗恢复通过。

只读诊断定位74:3:16，SCN2743320，VLD5/CON_UID677737149/CON_ID3，native独立确认 OP24.4 MEDIA RECOVERY MARKER。第一字段原字节 `28230000000000000000000072000c00`；第二字段 `0ddc29000000000017dc290000800000`，首端2743309/标志0000、末端2743319/标志8000，跨度10。现有profile要求两端8000且跨度≤1，因此拒绝。native未解释范围字段的完整语义，不能仅按opcode放行、擦marker或忽略范围。

封存目录 `lab/out/cross-window-active-20261007`；[182文件双SHA清单](evidence/RESEARCH-cross-window-active-20261007.json)写后再次逐项核对，凭据排除不上传。上一522文件完整核验未变。采集/获取/探针命令、rc、stdout/stderr、执行脚本、原始capture/归档/native均保全。

下一步：只读研究同冻结73/74的新marker范围与跨代际事务关联，定义可证伪profile并实现最小修复/反例，再完整两UPDATE核验。其后仍须真正跨发布窗口的持久活跃事务/字典状态及崩溃恢复；拼接两个关闭日志通过也不能替代此门禁。正式 C++、独立目标、一般矩阵、独立评审、部署/现场/发布仍待证；全goal保持active。
