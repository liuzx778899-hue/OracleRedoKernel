# W5 完整SYS变化史和历史DML正功能

前置：6b68786/kernel97dd395f/290、adaptere095822b/22；V3实际新SCN2533047六完整SYS行、230字段与独立尺子零差异，但没有产品获取账/历史事件权限。旧seq66/67已关闭源事务禁止重放，旧V2仍拒绝，219/204等冻结证据不修改。源IND$权限仅专属实验配置，不当生产零侵入证明。

1. 补冻结seq66/67相关提交SCN的独立完整SYS像，特别新COL$ slot51插入/临时名称/重命名/逻辑列号变化/删除全过程；核全部尾字段，不把IRP存32/总payload36的四ABSENT凭空补NULL。完整SYS布局37/45/37中OBJ# cluster key与payload偏移、物理/internal错位及字段类型明确保留。旧SCN结果仅独立尺子，不回填权限。
2. 实现完整闭合SYS original/control驱动的行像重放，使用已有非行分配begin/首行根、完整XID/current UBA、普通OBJ$ heap pair、TAB$/COL$ cluster pair及I_COL1/2/3十index pair；全部original必须处置。以可信新版本baseline中完整行和派生列索引初态为起点，按事务提交SCN推进，rollback/补偿不发布；非当前L ITL不当当前身份，slot复用/ABA需明确生命周期。
3. 构建新COL$位置与父OBJ#及NAME/COL#/INTCOL#三索引一致的完整变化史；TAB$/OBJ$状态和真实列历史相互一致才授权schema。缺索引/间接依赖、错parent/generation/layout/rowid/UBA/fullXID/commitSCN、unknown字段或NULL压缩profile均语义拒绝、零事件。合法checksum raw反例须结构通过，不能仅测试手工DTO。
4. 先新专属fixture和V3固定SCN获取，之后ADD/DROP与稀疏DML各一次，源动作闭合后不重放；按真实commit SCN核完整EXTRA=NULL、DROP后完整行及native/ASOF独立尺子。实际V3元数据绑定源/SCN/receipt，旧V2不得以晚基线获得历史权限。
5. 正功能后升级隔离adapter/产品依赖、产品获取账格式与获取API，完整P提交树build；新真实HTTP→目标原子commit→control checkpoint/持久ACK、重复/重启核验。不借用旧737或这轮290/22宣称产品通过。

继续原跨窗口活跃事务、正式C++采集/租约、独立目标实例、扩展类型/环境/压力断电、独立评审与现场发布。共享checkout只读，禁止Luna/子代理、AGPL复制、秘密/大型证据入Git。原goal/automation保持，Codex所有Oracle目标完成后才查。
