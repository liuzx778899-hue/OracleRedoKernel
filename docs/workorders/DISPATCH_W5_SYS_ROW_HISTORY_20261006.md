# W5 SYS clustered行值与SCN列历史正功能

前置：内核70c1a12/kernel5fb55de7/275；lab6e8ac66。冻结三SYS事务22原始节点与控制窗口/native结束一致，仅commit原始/control history范围。原产品efd068f/737尚未接新kernel；原actual V2与擦marker仍按SYS写入拒绝。源seq66/67事务闭合禁止重放，旧173/34/146文件保持。

1. 从被冻结native/raw全记录核SYS clustered KDO各形态、tabn/slot/ITL/data block/逻辑对象，区别COL$21/data2及TAB$4/data2同cluster的身份；不得凭data2或同slot将两个逻辑表混合。从同SCN V2可信角色/位置取得起点；SQL truth/dump仅作独立尺子，不喂生产事件。
2. 实现明确scoped clustered row before/after与字段/type解码；先冻结SYS完整control history与原始pair完整覆盖，再确认列名、type、physical ordinal/nullability/hidden/generation和需要的内部键。未知cluster/rowpiece/间接链拒绝；不能直接放宽普通heap tabn0锁成通用cluster。
3. 同XID COL$新增依赖必须由raw完整行/关联键证明，与已获取角色锚点相连；active/tombstone与ADD/DROP SCN历史来自raw提交重放，不能用最终字典或晚快照反推中间状态。覆盖两次列变更和间接列依赖，未覆盖部分保持拒绝。
4. 正功能必须恢复真实ADD期间EXTRA=NULL及DROP后的原schema完整事件，与各源SCN独立ASOF/完整中间像逐字段零差异。原错误三列诊断输出只能作负例。错逻辑表、错cluster tabn/slot/字段位置/type/UBA/XID、缺依赖/漏SYS节点/ABA均以合法checksum raw做结构通过/语义拒绝反例，零输出。
5. 改动提交树Java17完整build与SHA，冻结正负样本影响验证；新kernel产品依赖显式版本隔离，再737候选对应完整构建、实际HTTP DML→独立目标commit→control CP/持久ACK及重启核验。不得拿旧产品737证明新制品。随后活跃跨窗/C++/租约/独立目标/矩阵压力断电/独立评审现场发布继续。

专属oraredo-19c-lab-20261004边界、共享只读、零侵入生产路径、AGPL禁止复制、禁止Luna/子代理、失败追加/SHA/rc/events关联。原goal不缩小，automation保留；用户要求Codex卡顿在所有Oracle目标完成后查。
