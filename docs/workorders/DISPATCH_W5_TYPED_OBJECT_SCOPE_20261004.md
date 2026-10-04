# W5 typed世代scope与获取基线到原始redo链路

状态：typed scope及snapshot→新DML有限链路已交付，见[交付](DELIVERY_W5_TYPED_REPLAY_20261004.md)；初始跨snapshot等尚待证，接续[真实边界派单](DISPATCH_W5_SNAPSHOT_BOUNDARY_20261004.md)。不能以固定fixture通过标整个W5完成。

1. 增加明确heap/index角色的logical/data-object映射，先在自主对象发现逐项验证原undo及补偿的完整(objectId,dataObjectId)对。禁止把四ID塞进Set后放宽原拒绝；错heap/index交叉role、旧epoch、未知scope一律整批拒绝。保留等值旧scope及其已通过证据。
2. physical dispatcher、index leaf position及完整行row key一并使用该typed映射；不能只改发现器导致后续层仍把logical当data。映射同时绑定获取source/PDB/SCN/物理列，角色来自真实获取结果，历史区间认证仍独立待证。
3. 接通FrozenHeapBaseline导入与重建，不由lab驱动手填行值。可信digest来自实际获取进程交接；若需生产控制器，不能读取可编辑artifact旁边manifest自认可信。显式列出哪个控制进程/凭据/连接身份是信任根，不宣称SHA有签名认证能力。
4. snapshot2341293不在旧seq26覆盖内，不能旧输入重建。实现后在专属已有项目表新输出目录做一次性受控UPDATE/commit/归档，先核source/当前generation与fixture当前值，CAS式匹配预期值并断言只影响一行。保留原始归档和native/SQL独立truth；失败不重放已提交DML。必要的新read-only基线不是重跑旧DML。
5. 同件完整树jar对账完整before/after与当前value，并拒绝旧73073缓存套新73076、错map/角色/PDB、receipt下改键、晚SCN/边界。导入成功或结构scan不能当DML支持；初始事务跨snapshot真实证据与rollback/并发仍须补齐。
6. 历史DDL/ABA自动来源、列版本认证、持续capture/ACK、StreamHouse及现场/发布保留完整目标。public events在这些门禁前继续拒绝。

交付中文报告、STATUS、冻结输入/jar/输出/receipt SHA、完整命令rc及失败原文；只提交安全脚本/源码/测试/摘要。
