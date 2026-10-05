# W5 可信字典锚点代码候选

## 结论
未关闭历史字典 P0，未接产品候选或实际 HTTP。当前交付增加同 SCN SYS 锚点获取接口、严格 V2 导入和 raw 字典位置屏障；下一步仍按 DISPATCH_W5_TRUSTED_DICTIONARY_ANCHORS_20261005 执行。

## 已验证
- 内核 verify：265 tests，0 failure/error/skipped；针对性 15 项通过。
- adapter verify：21 tests，0 failure/error/skipped。新增获取结果缺失 SYS 行和历史列冲突不得签发 authority。
- V2 导入拒绝缺失/重复/错目标/错角色/历史列冲突；TAB$/COL$ 共用 data object 仍保留各自逻辑角色。
- 无 schema marker 的合成目标锚点字典写入拒绝；同 data object 的其他逻辑角色、其他物理行不冒充目标 DDL。已知角色未知 row profile 和 data generation 冲突拒绝。
- 实际专属 Oracle 既有实验只读账号调用 acquireDictionaryBound，ORA-00942，连接关闭、未产生 acquisition。未自动 grant；本轮无源 DDL/DML、目标或配置动作。

## 范围与失败
原始 SYS byte/native 观察见前一轮 COVERAGE_GAP；本轮位置屏障测试为合成单位证据，不替代新真实捕获。仅已观测 UPDATE/DELETE/INSERT KDO 前缀；已知字典角色未知 profile 保守拒绝，间接字典依赖和跨窗覆盖未证。旧 V1 兼容不授予一般历史权限。

adapter 首轮编译失败：wanted 变量重名，失败源码/原 stdout/stderr/rc 保留，修复后 r2 全通过。内核源码 SHA 于内核验证后封存，权限探针源码/class/jar 于执行前封存，不能混称全轮执行前 pin。

## 制品和接续
安全清单：[DELIVERY-dictionary-anchor-candidate-20261005.json](evidence/DELIVERY-dictionary-anchor-candidate-20261005.json)。实际输入/输出/源码及测试报告在 ignored lab/out/dictionary-acquisition-20261005；secret 和 Maven 缓存不在本清单哈希范围。

需要补实验 SYS SELECT/FLASHBACK 权限时只在专属容器按明确账号执行；之后 fresh 获取实际同 SCN anchors，创建新唯一 fixture/raw DDL，验证 marker 擦除也拒绝，接持久获取账及新产品候选 HTTP。旧 seq52—57 源事务已关闭，严禁重放。原目标、独立目标/采集/现场/发布范围保持，goal active，独立评审 PENDING。
