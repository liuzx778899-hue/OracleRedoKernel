# W3 同事务混合操作与 INSERT/DELETE 整事务回滚 capture

生产4f79691/kernel a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968 不改，原251测试不重跑；源driver30e42f4，独立比较与图驱动另封提交。[双次SHA清单](evidence/DELIVERY-mixed-rollback-capture-20261005.json)，目录 `lab/out/mixed-dml-capture-20261005`。这是新源轮次，不重复已接受的44—47或目标强杀。

仅专属容器新表 `ORKLAB.T_DML_MIX_20261005`，heap73256/index73257，seed0/mixed-seed/anchor，actual snapshot SCN2426700/SHA e5d52f81bf5845c363de8bc8722e990e2a5eebce3388ac8de87301edb98dcdba。每笔源事务只执行一次：XID10.27.1795同事务 INSERT11/mixed-before/mixed-note→TXT UPDATE mixed-after→DELETE该行→COMMIT；10.7.1795 INSERT12/undo-insert/undo-note→ROLLBACK；10.31.1797 DELETE seed0→ROLLBACK。每步SQL中间像与结束后truth/count已保存，源supplemental NO/NO，实验DDL/DML不当生产零侵入证明。

collector rc0，seq48/49/50分别9039/10/17块，next51，三journal与原归档数据块零差异。raw自主配对唯一index leaf、实际基线与输入SHA进入同件完整batch，rc0/三事件：INSERT→UPDATE→DELETE，全部XID10.27.1795；两笔rollback零业务事件，不因最终值等于基线而吞混合提交三次变化。batch SHA68bdcaa731964f6dfdf2bba989bcef8bc5de399416a7bdd7728db1d15248b261，schema SHAec13b3ab617c4e2fe99900cfe3866b00f0de58ccefc4127b2cdfbf85c39cf738。

自主四物理图（含seed）：2/0/2、混合5/0/5、insert rollback2/2/0、delete rollback2/2/0，共11原始/4补偿/7保留，与完整native节点/顺序/位置/UBA/对象零差异。native同record三heap forward/undo独立配对重放三完整中间像，与SQL逐步骤、capture/archive行、最终seed状态零差异。只是end record SCN及真实vector排序，不推导新commit SCN规则。

两个实际archive重封反例scan0而行/物理入口rc1/stdout0：混合事务最后DELETE inverse的mixed-after改为xixed-after（ROW_REPLAY_BEFORE_VALUE_MISMATCH）；seq50一个5.6 marker改5.7（MISSING_INDEX_LEAF_INVENTORY_MARKER）。完整输入失败无早先三事件泄漏。输入修改位置含block checksum逐字节清单保留。三独立输出比较反例缺UPDATE/错DELETE before/逆序均检出，public events仍拒绝。

比较器首轮SQL拆分起始下标4误漏ID，native三变化正确但SQL步骤1/2比较失败；失败源码/rc/stdout保全，修正下标3后只补该比较和未执行门禁，没有重放源动作。采集驱动执行前封存实际源码与kernel SHA。后续研究只读，collector终止，旧fixture11/blocking-closed/new-note保留。

本轮认证固定三列非空、单混合事务/两全回滚的关闭capture语义，未扩大到多savepoint/活跃同key跨窗/任意rowpiece/类型/历史DDL。不修改公开decoder、adapter依赖或生产部署；新INSERT/DELETE/NULL/混合/rollback还未实际到专属target commit/checkpoint。下一步复用44—50各冻结批次，建隔离目标验证完整操作顺序、中间像、回滚空batch、幂等成员凭证和消费前沿；全产品/独立目标实例/现场/发布仍未通过。
