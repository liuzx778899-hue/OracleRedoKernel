# W7 online redo捕获：与ldfcap对应的独立门禁

用户追问捕获环节及redo文件独占问题。本包明确：现有归档copy只是冻结解析输入，不是在线捕获实现，W7未完成。解析工作继续，但完整零侵入路线必须交付原始字节捕获、持久化和连续性证据。

## 当前事实

2026-10-04专属Linux 19.3实例运行中，LGWR进程存在；CURRENT日志组1、sequence16，`/opt/oracle/oradata/ORK19/redo01.log`。另一个容器进程以既有身份只读打开并读取前1024字节，rc0；读取前后CURRENT均为seq16。没有写文件、强制切换或配置变更。[本地探测清单](evidence/READ-online-open-20261004.json)。这只证明此环境的该次读访问，不证明LGWR恰在dd期间写入、持续流完整性、Windows共享权限或ASM访问。

Oracle官方说明LGWR写在线redo，日志组循环切换和复用；在ARCHIVELOG模式下归档完成是复用前置条件之一。[Managing the Redo Log](https://docs.oracle.com/en/database/oracle/oracle-database/21/admin/managing-the-redo-log.html)。Oracle RAC redo可位于共享文件系统或ASM，普通路径访问实验不能替代ASM验证。[About Online Redo Log Files](https://docs.oracle.com/en/database/oracle/oracle-database/19/rilin/about-online-redo-log-files.html)。不从这些资料推断所有平台的文件共享flags。

## 最小落地路径及可证伪条件

1. 普通文件只读collector先行。先验证已有运行环境的CURRENT读取权限、块格式、正在写入时的短读/半写与跨块record边界，不预设必须有驱动。数据库模式、补充日志和业务表保持既有配置；不以重启源端换取读取能力。
2. 形成独立持久化capture envelope：绑定源身份/thread/resetlogs/sequence和真实块位置，保留原始字节及摘要、版本观察和失败原文。它与解析内核解耦；未观察到online可验证完成边界之前，不把普通copy或手工改NAB伪装成已关闭归档。
3. 专属lab受控DML/自然或显式实验切换，独立核对collector保存字节与随后冻结归档及native记录。实验切换不算生产需要切换或零侵入证明；预先存在归档可做验证真值，NOARCHIVELOG支持另用专属fixture验证。
4. 设置有界反例：读取过程中日志代际变化、旧日志复用前未落盘、跨块record残缺、重复/乱序块、源身份混流、collector崩溃及消费ACK前后崩溃。捕获端持久位置、解析消费位置和目标ACK分别核对。未完成record不得进入事件出口；遇无法补齐缺口明确拒绝。
5. 普通文件路线被实测访问限制阻挡时，再选择平台专用只读访问/捕获方案。Windows Oracle共享读取必须另测；ASM需要独立访问适配与权限证据。任何需要源端重启/数据库配置变更的路线均不能冒充本项目严格零侵入支持。

## 验收交付

交付collector源码、capture格式与source-binding说明、完整运行命令/rc、原始输入及journal/制品/输出SHA、运行期间持续DML与切换证据、独立对账、缺口拒绝及故障恢复原文。先完成“读取权限”“捕获连续性”“持久化恢复”三个门禁，再做解析接入和StreamHouse ACK/目标对账。读取成功、文件SHA一致、进程存活或解析已通过均不能替代捕获验收。

专属lab可受控动作；共享Oracle仅只读取证。原始日志及大型journal留lab/out，Git仅安全源码、脚本与摘要。此派单没有宣称collector已实现，也没有授权修改其他项目或生产环境。
