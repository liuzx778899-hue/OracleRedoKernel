# D2 SYS正常主线与验证优先级调整

用户2026-10-08明确：完整D1–D5能力目标保留，先功能齐，再集中加固。oracle-redo提示词、根AGENTS及产品STATUS已同步；局部解析修复仅针对性回归与冻结真实样本，低概率故障组合后移，已有通过项不重跑，不再为每个部件双SHA封存和独立长报告。

本批实现输入自有的五节点cluster维护闭合：完整BEGIN/COMMIT、undo链、索引keydata、cluster key插入与双向链一致，保留基线TAB$/COL$值行；形成原始节点收据进入字典checkpoint，只有完整闭合才从逻辑值更新中分离。仅读原80/81及SCN2899589基线，不按非值行slot直接忽略维护。

同时修正正常字节形态：六列OBJ$ ckix按前后图同值绑定，不再枚举物理引用值；cluster F前缀支持已观察双entry cleanout；DRP byte19与IRP末尾三字节不再当有效业务标志。保持完整XID/UBA、角色、列映射、前后图及行大小校验。新增三项相关回归，六个相关测试类合计23项零失败/错误/跳过；未重复全量测试或正式制品构建。

真实维护XID10.3.3527五original闭合，parent75185，0x004089a4.slot4→0x00408d86.slot3。真实pending SYS10.26.3522前缀现通过WAITING_INPUT：5original/2row/2index/1control，未提交schema拒绝，completed0/events0。

闭合80/81仍拒绝：`UNKNOWN_OR_CONFLICTING_DICTIONARY_DEPENDENCY:Position[archiveSequence=81, block=25, offset=340, vector=1]:68:68`，stdout0/events0；此节点为真实11.2插入，不能忽略或当完整SYS支持。下一直接补对象68的可信布局/关联与完整事务处置，再闭合完整事件及产品接线；不新获取或晚SCN回填，不重源动作。

执行记录在`lab/out/d2-sys-maintenance-20261008`：inventory/lifecycle/records、相关测试、最终`final-summary.json`保留rc与SHA256。开发classes验证，非正式交付jar；源/目标动作、新获取、服务部署均0，产品kernel仍未换。原失败日志保持，原214/89/91等封存不动。D2仍开发中，D3连续能力、D4/D5、独立验收部署生产现场发布保留。
