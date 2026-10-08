# D2 SYS正常主线与验证优先级调整

用户2026-10-08明确：完整D1–D5能力目标保留，先功能齐，再集中加固。oracle-redo提示词、根AGENTS及产品STATUS已同步；局部解析修复仅针对性回归与冻结真实样本，低概率故障组合后移，已有通过项不重跑，不再为每个部件双SHA封存和独立长报告。

本批实现输入自有的五节点cluster维护闭合：完整BEGIN/COMMIT、undo链、索引keydata、cluster key插入与双向链一致，保留基线TAB$/COL$值行；形成原始节点收据进入字典checkpoint，只有完整闭合才从逻辑值更新中分离。仅读原80/81及SCN2899589基线，不按非值行slot直接忽略维护。

同时修正正常字节形态：六列OBJ$ ckix按前后图同值绑定，不再枚举物理引用值；cluster F前缀支持已观察双entry cleanout；DRP byte19与IRP末尾三字节不再当有效业务标志。保持完整XID/UBA、角色、列映射、前后图及行大小校验。新增三项相关回归，六个相关测试类合计23项零失败/错误/跳过；未重复全量测试或正式制品构建。

真实维护XID10.3.3527五original闭合，parent75185，0x004089a4.slot4→0x00408d86.slot3。真实pending SYS10.26.3522前缀现通过WAITING_INPUT：5original/2row/2index/1control，未提交schema拒绝，completed0/events0。

闭合80/81仍拒绝：`UNKNOWN_OR_CONFLICTING_DICTIONARY_DEPENDENCY:Position[archiveSequence=81, block=25, offset=340, vector=1]:68:68`，stdout0/events0；此节点为真实11.2插入，不能忽略或当完整SYS支持。下一直接补对象68的可信布局/关联与完整事务处置，再闭合完整事件及产品接线；不新获取或晚SCN回填，不重源动作。

执行记录在`lab/out/d2-sys-maintenance-20261008`：inventory/lifecycle/records、相关测试、最终`final-summary.json`保留rc与SHA256。开发classes验证，非正式交付jar；源/目标动作、新获取、服务部署均0，产品kernel仍未换。原失败日志保持，原214/89/91等封存不动。D2仍开发中，D3连续能力、D4/D5、独立验收部署生产现场发布保留。

## HIST_HEAD$依赖接线（2026-10-08）

新增可选、先行同SCN的HIST_HEAD$布局/两索引身份与段地址获取；SYS.ICOL$固定SCN核对(OBJ#,COL#)/(OBJ#,INTCOL#)，前后身份/布局一致。旧V3基线保持可读，不从当前诊断回填旧80/81。诊断SELECT仅确认对象68的表名/布局，不作历史授权。

解析接入已选SYS事务的全original核算：真实81:25:340统计INSERT与26:192/27:16两个索引，完整XID/UBA/代际、rowid、父对象和COL$/INTCOL#关联核等；18列51字节KDO按实际形态支持。仅此已观察INSERT形态；其它统计操作仍明确拒绝，不能宣称完整统计DDL覆盖。

针对性内核6类23项零失败/错误/跳过；获取2项直接Java测试通过，两个获取类javac rc0。适配Maven因本机SPI/内核坐标缺失rc1，原文保留，直接测试不冒充完整模块构建。首测试生成编译错误、实际KDO长度拒绝和测试列号错误均保留在statistics-*.log。必要SHA/rc见lab/out/d2-sys-maintenance-20261008/statistics-summary.json。源动作/目标动作/新获取/部署0，产品旧制品未换装。

尚未证明：旧80/81完整闭合（旧可信基线未获取统计身份），新增先行历史获取实库路径、新样本完整SYS事务、恢复及产品目标CPACK。下一在新独立实验窗口先获取含本契约的可信基线，再运行一次必要正常DDL/DML链路；不得重放80/81或修改旧基线。其后集中产品接线，不扩故障矩阵，D2其余/D3连续采集/D4-D5和最终验收范围不变。
