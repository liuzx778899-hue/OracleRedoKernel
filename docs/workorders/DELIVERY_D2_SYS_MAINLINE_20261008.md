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

## 新先行统计基线与82/83正常事务（2026-10-08）

产品依赖87f0261完整提交树必要打包rc0，未重跑全量测试；application f5be235df0951d92f9035b4ac6705bae17e0c527e0db78a7e98d09cd4b624673，嵌入kernel2cb14d11/adaptera54946ae逐字节核等。新自有ORKD2STATCTL_20261008/helper37294worker50真实Flyway25项成功、消费关闭。首健康探测未就绪connect111保留；原进程正常启动后核SHA/HTTP通过。首获取拒绝且无基线，确认新fixture及ICOL$缺读取权限，仅专属实验补SELECT/FLASHBACK后成功，不作生产零权限配置证明。

真实HTTP acquisition101622639306678272/SCN2910534/snapshot9f0347eb3093ebc19416690199fcc0cebd7e5ac40c9bc24aa7541ff2bfa2d895；独立SYS6行230cells/3index/业务1row，加统计22列/2索引零差异。新fixture T_D2_STATS_20261008一次ADD跨82/83、DROP/两UPDATE，延迟trigger已删、源行三列恢复/NO NO/旧行不变。原始归档SHA核等；禁止重跑d2_statistics_source.py --execute、新获取或晚SCN回填本基线。

开发修复：148字节0x44a BEGIN绑定同记录声明长度；历史fbi/padding、cluster索引SDC及IRP填充不作身份/key/指针授权；COMMIT fbi/padding不作扩展语义；24.4支持VLD1且仍核已观察下界和闭合上界。相关5类47项零失败/错误/跳过。开发classes真实82前缀rc0，pending SYS10.24.3554/5original/2row/2index/1control、schema拒绝/completed0/events0。CP SHA及必要制品/输入SHA见lab/out/d2-statistics-runtime-20261008/integration-summary.json；控制修复尚未换装产品，不冒充正式jar恢复通过。首索引段误用50357378保留；冻结同记录配对确认实际leaf50357379，未按猜测加1。

闭合仍rc1/stdout0：83:38:16未知669/669。全部两个SYS事务原始清单：ADD10original、DROP8original；新增缺件仅COL_USAGE$669/I_COL_USAGE$670。诊断布局10列NUMBER/DATE、索引OBJ#/INTCOL#，不是历史元数据授权。本批ADD没有HIST_HEAD$操作，不替代旧真实统计INSERT完整运行验证。下一先补这两个依赖的完整处置及先行获取，再新独立窗口集中一次闭合/恢复/产品CPACK；不逐表反复源实验、不忽略统计或回填本基线。全部失败与原始输入保留，自有helper已停143，源Oracle运行。D2其余/D3连续/D4-D5及独立验收发布保留，不扩故障矩阵。

## COL_USAGE$正常主线补齐（2026-10-08）

基于冻结83:38/39实际DELETE/索引DELETE补COL_USAGE$十列、I_COL_USAGE$分离rowid绑定；完整undo前像、XID/UBA/代际/父OBJ#/INTCOL#及删除前COL$历史关联进入原SYS全original覆盖，不以统计表名跳过。可选先行DICT_USAGE布局/身份/索引段地址与前后核等接V3，获取沿用固定SCN OBJ$/COL$/TAB$/IND$/ICOL$与当前布局核等。旧V3不带此契约仍可读，但不得事后回填82/83。

相关内核5类21项及获取/HTTP类8项零失败/错误/跳过。实际十列undo IRP为50字节KDO（两字节null bitmap）已支持；首长度拒绝与新角色碰撞校验误碰已有cluster共享dataobj失败全文保留在usage-targeted-tests*.log，修后通过。当前只是开发候选，正常新先行获取/完整闭合/恢复/产品CPACK继续待证。只做本次必要正常接线，不扩故障矩阵。

正式提交树正常链路补证：root0b9f77e内核cbbe75b013c173e3c858dac7fb37464c6e2b67ffae91619912c196877921e35c、adapter008d2a194be3c76335aa3aa5be4a4bd9acdeecde8740763128294aaad0d8ec6b；P b1e5d9b applicationdc90e006e6826bdbaf3c0bcd23a0e09eb6c9e22b59e9368acc35df09676eb6b4必要打包rc0/未重跑全量测试，嵌入逐字节核等。新真实HTTP acquisition101631881417797632/SCN2913968/snapshotd5184aa9edfbce2667b873376b93db8428fee5a0067a7e8f2ef929751dbac47d；独立SYS6行230cells/3index/业务1row及HIST_HEAD$22列2索引、COL_USAGE$10列1索引零差异。

新一次专属T_D2_USAGE_20261008实验冻结84/85，ADD跨成员SYS3.16.749真实pending，prefix rc0/5original/2row/2index/1control/completed0/events0；完整闭合rc0/2 UPDATE，四列→三列历史schema及完整前后像核等。独立JVM恢复rc0/2事件、完整输出同字节SHA8b72c0866cd4203d8f7134126982523efb4e4db8509df6a8d1fb85eadf654ab2，CP SHAe2491466818b04e03c204ac1f7e7704c28881693d3fe4ad5726aca9992caa5c4。原始归档源/本地SHA核等，源行三列恢复/延迟trigger已删/NO NO。禁止重跑本批源动作或获取，也不回填旧75–83。必要SHA/rc入口lab/out/d2-usage-runtime-20261008/normal-summary.json，原始失败保留，无新增封存仪式。

产品目标接线尚未执行：2026-10-08本轮获取helper停止核归属时，Docker返回“Docker Desktop is manually paused”。helper最后已知健康运行，stop未成功，不宣称已停；新产品helper37296/目标SHPUS1/任务/CPACK驱动仅准备，目标写入0。lab/d2_usage_product.py为后续入口，先核Docker恢复与原helper权威状态，以新标签完成停止再projection→target→start→正常prefix WAITING→闭合CPACK，不重放源动作。此为新实际环境暂停，不是旧Docker阻断；goal仍active。D2其余/D3连续/D4-D5及独立目标评审部署生产现场发布保持待证，不扩极端验证。

## 正常产品目标闭环完成（2026-10-08）

Docker恢复后只接续冻结84/85与原先行获取，新源动作/新获取0。正式dc90e006同件、原SYS历史pending进入产品task101658150276644864 WAITING_INPUT，目标/事件账/门/CP均零；闭合后两完整UPDATE四列→三列历史进入batch101658702570012672 PUBLISHED/rc0、原任务DONE，target2/2/1、ledger2/gate1、两窗CONSUMED。独立SQL读目标两事件顺序/ID/payloadSHA/完整前后像均与实际raw投影核等，完整控制checkpoint与ACK同字节SHA75af73f1683cd37248c83dd09c642ea3bdc8f4dcac6aaa7543ff55370a8cbc8f。获取全字段/metadata/登记时间按TIMESTAMP(6)核等，源三列/旧行/NO NO保持、trigger0，目标最终行含D2STAT_COL=NULL。

首窗口登记脚本沿用旧.oracap文件名导致ORACLE_CAPTURE_HASH_OR_SCOPE_MISMATCH，零目标/零推进失败保留；仅修正本窗口FILE_SHA256文件名清单并续原task，没有重新提交批次/重放源。首resume空body误发GET和校验器base64补位误判均保留，按既有POST/无padding契约修正。产品代码与制品未改。两个自有helpers均已停143，源Oracle运行。必要证据入口lab/out/d2-usage-runtime-20261008/product-summary.json和product/closure-proof.json，不新增封存或重跑已过项。

本批证明实际pending SYS→完整历史事件/跨JVM恢复→产品目标原子提交→CPACK正常链路；同实例独立SQL是尺子，不是独立目标验收，且新ADD未包含HIST_HEAD$操作。D2初始水位/初始恢复/一般事务、D3连续采集、D4/D5及独立目标评审部署生产现场发布仍保留，下一主攻这些功能，不补低概率故障矩阵。


## 初始水位与完整活跃事务恢复（2026-10-08）

D2初始水位开发态通过：新一次专属T_D2_INITIAL_20261008事务10.11.3606/startSCN2917894未提交时获取可信acquisition101660919708471296/SCN2917903/snapshot1ab759112bc293bd647877d8aa72fc640faa530bf2eb5227260a9cee5e0e1834，独立固定SCN基线SYS6行230cells/3index/业务1row及统计/usage布局零差异。冻结86/87完整BEGIN/undo/commit，保留首前像核对和schema一致性，将完整跨水位事务纳入重放；实际24.4标记下界取原始已观察SCN，不以基线之后限制。prefix rc0/1pending/0events，closed与独立JVM restored均rc0/3完整UPDATE，seed→initial-open→initial-final→seed顺序及前后像核等，输出同字节SHA233746264a2c007959df99906a7422e9eea70e97c6be58a7c2fb52c0ed0bce79。相关37项零失败错误跳过；首正式jar标记拒绝、后续跨水位拒绝及测试误写1而实际2事务失败全文保留。helper37297已停143，源行恢复/NO NO；禁止重放本批源动作或获取。入口lab/out/d2-initial-runtime-20261008/normal-summary.json及d2-initial-source-20261008/archives.json。此为开发classes正常恢复证据，正式产品仍dc90e006/旧内核，未新打包/部署/目标CPACK，不当D2整体验收。下一补一般事务同commitSCN/同key/跨块正常能力，合并候选后集中产品接线；D3连续、D4/D5及独立验收发布保持完整范围。
