# W5 同产品候选的V2历史schema拒绝

前置：完整产品efd068f/737，application2db7106f、kernel672f01bc、adapter9516d4f9；新V2静态DML→HTTP→target/controlCP/ACK/重复重启已通过。本单不重跑已通过轮次，不修改或重放seq62—64源事务。

1. 登记新唯一historical fixture、控制schema、目标前缀/目录/port，核候选及源container归属。固定同候选application，实际产品HTTP先获取V2，再独立ASOF所有锚点/列/基线行核验。获取SCN早于新源动作，不使用事后补填快照作为authority。
2. 在专属源一次性ADD nullable列→稀疏UPDATE→DROP恢复→UPDATE并冻结原始capture/archive/native；每事务记录完整XID/结束，采集timeout只检查同句柄、不重复源动作。保全原raw和正确完整schema真值。
3. 原raw与重封缺schema-marker DERIVED绑定同获取账分别经过同实际认证HTTP候选。须目标前REFUSED/event0，目标行不变/applied/member/commit/ledger0、controlCP/ACK无推进；恢复/重启重复仍拒绝。结构scan通过不能当安全或DML通过。
4. 核对native完整SYS目标锚点写入及同XID COL$新行链，缺信息继续拒绝；生成authority/SCN/role/anchor身份反例。准确拒绝原因/控制实库记录独立核验，比较器反例检出，输入/制品/输出SHA双次封存。
5. 间接COL$依赖/活跃跨窗历史/原完整控制与C++采集、独立目标及矩阵/压力断电/评审/现场发布继续作为原目标，不以完成本单替代全部目标。

仅oraredo-19c-lab-20261004允许新fixture/config实验；共享只读、生产缺权限拒绝不自动grant。严禁Luna/子代理。秘密与大型制品留ignored，失败追加；完成本单继续下一未验收主线，真正全目标完成才停automation、之后才查Codex卡顿。
