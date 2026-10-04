# OracleRedoKernel 当前状态

更新：2026-10-04（Asia/Shanghai）。用户授权自主论证、实现、专属实验验证，主攻解析；禁止 Luna。目标仍进行中，不是全流程验收完成。

## 六列状态

| 工作包 | 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|---|
| W0 环境/样本/真值 | 通过：首批fa61476，矩阵继续 | 通过：环境/冻结归档；矩阵待证 | 待证 | 不适用：实验脚本 | 待证 | 未开始 |
| W1 文件/块 scanner | 通过：首批fa61476 | 通过：19c/21c有界样本/5个真实派生反例 | 待证：真实capture | 未开始 | 待证 | 未开始 |
| W2 record/vector | 通过：首批fa61476 | 通过：19c seq8/9完整dump、21c选定范围 | 待证：事务/字典 | 未开始 | 待证 | 未开始 |
| W3 基础行/类型 | 进行中：scalar + pair + KDOM2单列扩展值 | 通过：18pair/302长值patch；完整行待证 | 进行中：物理配对；字典/基线待证 | 未开始 | 待证 | 未开始 |
| W4 事务/回滚 | 进行中：有序history/覆盖/heap+index补偿、同源完整XID归属及有源位置重放 | 通过：203测试；seq21—24自主发现基线/A/B共12物理图；自主对象头关联；seq19—24六位置、seq21—24有限双上下文完整图与拒绝证据；九笔旧图及六批长heap证据保留 | 进行中：有界自主物理入口已交付；完整控制/页维护/跨块关联/恢复待证；不当通用生产能力 | 未开始 | 待证 | 未开始 |
| W5 SCN字典/基线 | 进行中：extent/完整行、实际ASOF获取/回执/typed世代，有限空heap cleanout | 通过：225测试；seq27真实TXT/PK完整行与两SQL快照；seq28/29真实初始跨snapshot整批拒绝、2/0/2物理图和SCN native零差异，4 raw拒绝；生产可信交接/历史字典待证 | 进行中：有限获取→新DML及真实初始跨snapshot拒绝通过；通用初始恢复/历史schema/INSERT/DELETE/rollback/并发待证 | 未开始 | 待证 | 未开始 |
| W6 StreamHouse接入 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
| W7 online采集 | 进行中：固定member有界观察journal实验 | 通过：429归档数据块字节覆盖、6派生拒绝；完整持续捕获/续接/Windows/ASM待证 | 未开始：journal到解析/ACK | 未开始 | 待证 | 未开始 |
| W8-W11 扩展类型/LOB/RAC/ASM/TDE | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |

## 当前证据

最新：[真实初始事务跨快照拒绝](DELIVERY_W5_SNAPSHOT_BOUNDARY_20261004.md)，运行时b9320ff、lab比较/派生5a2344b，[清单](evidence/DELIVERY-snapshot-boundary-20261004.json)。225测试通过；自有未提交会话/独立获取snapshot2345429/原会话继续提交，XID6.2.594冻结连续seq28/29。完整2/0/2物理图和begin2345424<=snapshot2345429<end2345432与native零差异后，完整行BASELINE_CROSSES_TRANSACTION_BOUNDARY、rc1/stdout0。真实窗口空heap OP4.1首轮拒绝保全，只支持已观察空条目/SCN/spare严格布局；4重封raw均scan通过、物理及行入口拒绝。缺begin/end、晚SCN语义、原回执SCN篡改及比较器反例检出，旧seq27两完整行同件jar回归零差异，events两件分别拒绝。实验DML没有重放，最终专属项目行11/boundary-closed/new-note；接续[INSERT/DELETE完整行](DISPATCH_W5_INSERT_DELETE_ROWS_20261004.md)。本例安全拒绝不代表通用初始恢复/历史字典/CDC/capture/接入/现场/发布通过。

最新：[W5 typed世代与获取基线完整行链路](DELIVERY_W5_TYPED_REPLAY_20261004.md)，运行时4afea7f、实验1c7106f，[清单](evidence/DELIVERY-typed-replay-20261004.json)。223测试通过；专属既有项目表一次性CAS TXT再PK UPDATE，同XID2.9.595冻结seq27 SHA061b8b3d…；实际获取snapshot2341293经回执/typed scope直接重建两次完整before/after，native及旧ASOF/最终SQL两快照零差异。完整1事务4/0/4物理图零差异。错角色/旧epoch/错logical/原回执改键及3重封checksum原始派生均rc1/stdout0；比较器5反例检出，同件jar seq15长值120/120/0、seq24三图10/4/6回归通过，events拒绝。范围外未知flags、真实short KDOM2首轮拒绝和错误控制字候选均保全，只修读取/解析不重放DML。控制字0短正长度XA已支持，NULL/short XR未证；接续真实初始跨snapshot边界，再攻INSERT/DELETE/rollback/并发完整行，生产可信交接/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[W5真实只读基线获取/回执导入](DELIVERY_W5_FROZEN_BASELINE_20261004.md)，运行时/获取脚本b1ff3af、比较器014297a，[清单](evidence/DELIVERY-frozen-baseline-20261004.json)。217测试通过；专属现有表只读获取snapshot2341293、完整1行/3列，与实际SQL输出及raw/native5身份字段零差异。原获取SHA下9份未改键/值/SCN/source/map/世代/漏行/截断篡改均rc1/stdout0，即使副本自报新SHA仍拒绝。伪造回执authority也可授权伪键，独立比较器2差异，明确可信控制进程边界，不能宣称SHA认证签发者。实际heap73073→73076/index73074→73075导入保留角色，旧equal-object重建scope拒绝，events拒绝。首次比较器SCN标志位错误rc1保全，只补比较器与未执行项；没有DML/DDL/归档或数据库配置改变。接续[typed角色与新DML链路派单](DISPATCH_W5_TYPED_OBJECT_SCOPE_20261004.md)，生产可信获取交接/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[W5固定基线完整行与来源反例](DELIVERY_W5_ROW_IMAGES_20261004.md)，最终完整树8b222c8，[清单](evidence/DELIVERY-heap-baseline-20261004.json)。213测试通过；真实seq25三次完整before/after、旧ASOF与最终SQL两快照独立零差异。10基线反例、8原始归档派生及新增forward/undo顺序冲突均整批拒绝，events继续拒绝。顺序派生在旧b8cae3c仍rc0，新jar严格拒绝；两轮原件保全。**未改主键伪基线仍rc0、独立比较3处差异，基线来源验收RETURN_FOR_FIX**；真实固定SCN样本通过不认证调用者声明。无源端动作，接续[基线来源/快照边界派单](DISPATCH_W5_BASELINE_PROVENANCE_20261004.md)。INSERT/DELETE分支无本轮新真实证据，rollback/并发/DDL/世代/完整CDC/capture/接入/现场/发布继续待证。

最新：[W5物理尾列缩短交付](DELIVERY_W5_ROW_EXTENT_20261004.md)，运行时/实验完整源码9b3f738，[清单](evidence/DELIVERY-tail-extent-20261004.json)。209测试通过；seq25两物理图5/0/5、8typed patch、4完整UBA pair含1尾列3→2与native零差异。8重封checksum真实派生均scan通过、完整物理入口及typed pair rc1/stdout0；比较器漏变化/伪NULL/错extent均检出。同件jar seq24三图10/4/6与seq15长值120/120/0回归通过，events seq25/26拒绝。第二轮测试复制丢失extent失败保全，仅修测试适配；没有数据库动作。保留ABSENT、packed delta未知，有限尾列标记不是SQL NULL；接续固定SCN完整行/逆向值核对、来源/世代门禁，历史字典/完整CDC/capture/接入/现场/发布仍待证。

最新：[W5基线/物理列/世代研究](RESEARCH_W5_BASELINE_20261004.md)，首源ad9f9e0、只读恢复cfa7187、完整研究9003ec5，[清单](evidence/RESEARCH-baseline-20261004.json)182文件双次SHA一致。专属新表两次受控实验冻结seq25/26，10heap布局raw/native零差异。SCN2282688缓存10/alpha/secret在UPDATE提交后ASOF仍一致；invisible NOTE逻辑列号NULL而segment列3。真实NOTE→NULL是ncol3→2/nnew0无显式字段，当前内核UNSUPPORTED_UPDATE_ROWPIECE拒绝，不计行支持。TRUNCATE后heap data-object73073→73076、同file12/block403/slot0复用，旧ASOF实际ORA-01466；旧logical/新data范围均内核拒绝。首次建表后SCN读取ORA-01466保全，只补只读基线和未执行UPDATE，未重放已提交setup。三比较器反例5/1/1，events两件均拒绝；生产内核203测试树6dd1772不变/不重跑。接续[缩列与世代派单](DISPATCH_W5_ROW_EXTENT_EPOCH_20261004.md)，W5/完整CDC/持续capture/接入/现场/发布仍待证。

最新：[自主物理事务分发交付](DELIVERY_W4_PHYSICAL_TRANSACTIONS_20261004.md)，运行时6dd1772、最终lab树eafdb16，[清单](evidence/DELIVERY-physical-transactions-20261004.json)1316文件双次SHA一致。203测试通过；新内核不预填XID，seq21—24各自主发现基线/A/B，共12笔图42原始/18取消/24保留与native零差异。9真实字节派生rc1/stdout0、四比较器反例5/2/1/1差异；正确A heap slot在基线/B已关闭后的实际遍历拒绝，无部分输出。首轮两测试预期/native参数错误及派生器错改非slot导致实际rc0的原文保全，只补失败/未执行项。没有数据库动作，events继续拒绝；物理范围不是SCN字典，非begin控制/跨块关联/页维护/恢复仍限制。接续W5字典/零侵入基线与完整行可恢复性，持续capture/ACK/接入/现场/发布独立待证。

最新：[自主对象事务发现/头路由交付](DELIVERY_W4_OBJECT_DISCOVERY_20261004.md)，运行时e7f2314、native比较器0d216bc，[清单](evidence/DELIVERY-object-discovery-20261004.json)1118文件双次SHA一致。198测试通过；不预填A/B XID，seq22—24各自主发现基线/A/B三笔完整事务，共32原始undo/22窗口头关联与native零差异。三比较器反例检出，缺heap范围rc1/stdout0拒绝。首轮范围外未知flags拒绝与封存原因断言错误保全，只补未执行检查。events继续rc1/stdout0；这是header入口，不是完整物理分发/DML/字典认证。无数据库动作，接续完整自主物理流/有序head/取消/无部分输出门禁，再攻W5；持续capture/ACK/接入/现场/发布待证。

- 私有空仓库已拉取，Java17构建；生产运行时无数据库/Flink/Spring依赖。
- 本地两套21.3 XE只读盘点：ARCHIVELOG且MIN/ALL已开启 vs NOARCHIVELOG且MIN/ALL关闭；DBID相同，需来源绑定防混流。
- `lab/out/environment-20261004/archive3064.dbf`：10,352,128 bytes，SHA256 `259286dbce264815ac582152a75670a3c7572cbe71fb2af8dbeba62164a8e34f`，20,219 blocks，20,105 structural records。DBID3062128985、thread1、seq3064、resetlogs1181008281、SCN[41434081,41443847)。
- Oracle dump选择范围：490条record、920个vector位置/LEN/VLD/SCN/container/opcode零差异。首轮comparator漏掉一条不在行首的CHANGE，失败原文 `dump-parity.json` 保留；修复后 `dump-parity-r2.json`。这是验证器修复，不是parser修复。
- fa61476完整提交树独立重建：14项测试、0失败、0跳过。研发jar SHA256 `3c7e87e590afded83d468cb677e4403d5a978f9dfa555577fefd2f682f835421`。合成测试不冒充真实Oracle支持。
- 同件jar对19c真实归档副本5项反例（截断1字节/末块缺失/中间块缺失/checksum篡改/重封sequence错）均rc1、stdout空，结构拒绝通过；T009事务级缺archive仍待证。
- 已推送main首批源码fa61476；[交付清单](evidence/DELIVERY-fa61476.json)绑定jar、输入、对账、反例与日志SHA，私有真实raw样本留本地。
- `events` 返回rc1、CDC事件0，原因 `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。未开放事件发布。
- 专属19.3容器 `oraredo-19c-lab-20261004` 已建好。第一次初始化口令长度超限失败，原容器改名带bootstrap-failed、原日志保留；成功实例不暴露宿主端口。
- 19c无supplemental fixture已冻结seq7/8/9：SHA分别d53b6ee8…、e9ec1daf…、99ea74e7…。seq8对账53,078 records/104,545 vectors，seq9对账14/28，均零差异。尚未判DML/事务通过。
- 首轮PDB切日志ORA-65040、CDB VLD9/13/marker结构缺口、SQLPlus中文显示与LogMiner status3均保留原文；两会话交叉事务/跨switch动作已执行。细节见[首批反馈](DELIVERY_W0_W2_20261004.md)。

## 接续与限制

最新：[三批双上下文完整图](DELIVERY_W4_PAIRED_MATRIX_20261004.md)，lab完整树d907834，main/test/pom与192测试内核21cd368差异为空，[清单](evidence/DELIVERY-paired-matrix-20261004.json)1023文件双次SHA一致。同件jar完整路由seq22—24三批A/B，共六图14原始/14取消/0保留；关联14项/8外国项及各图native零差异。seq23七B+三A派生rc1/stdout0；A失败即使B已关闭也不泄漏输出。错fixture来源拒绝，三比较器反例检出3/2/1差异，events拒绝；无数据库动作，不重复seq21/192测试已通过轮次。接续[内核自主发现/分发派单](DISPATCH_W4_ARCHIVED_ROUTER_20261004.md)，尚未实现/验收；通用控制/完整源覆盖/字典/持续capture/集成/现场/发布待证。

最新：[有源归属位置重放/双事务完整图](DELIVERY_W4_OWNED_POSITIONS_20261004.md)，内核/消费者完整树21cd368，native比较器修正树ce23211且main/test/pom差异为空，[清单](evidence/DELIVERY-owned-positions-20261004.json)1328文件主封存/追加失败原文均双次SHA核对。192测试通过；自主绑定完整同叶流及唯一原始头，验证PURGE/RESTORE物理位置后重放INSERT/PURGE槽位、DELETE/RESTORE保留槽位，未知/缺原始/重复取消等拒绝。seq19—24六A位置native零差异；seq23 B图4/4/0、seq24 B2/2/0、seq21 full A/B各2/2/0独立native有序图零差异，seq18旧两图回归通过。7分发+7位置查询+3重封raw拒绝；3比较器反例检出。旧native误把B补偿归A及补充汇总空stdout失败保全，仅补失败/未执行检查。无数据库动作，events拒绝；seq22—24双事务完整分发图、生产分发/其它控制/页维护/字典/持续capture/接入/现场/发布待证。

最新：[真实短Z RESTORE及B图交付](DELIVERY_W4_Z_RESTORE_20261004.md)，运行时b7bb75f、完整封存树bbd2fd6，[清单](evidence/DELIVERY-z-restore-20261004.json)1063文件双次SHA一致。187测试通过；OP10.5增加已观察030db87100000000精确布局，未知bytes2/3不解释/其他变体拒绝。seq24 A/B同叶清单4/2与native零差异，B图2/2/0闭合；13真实vector派生+3图派生rc1/stdout0。旧seq21/22 B图各2/2/0、seq18两笔8/7/1和5/5/0同件jar回归零差异。A位置现在到达中间DELETE拒绝，A单上下文图仍缺B原始节点，events拒绝。首轮旧Java编译失败保全，新目录绑定Java17重建通过；没有数据库动作。接续有源归属DELETE/RESTORE及PURGE位置重放，用seq24 A/seq23 B冻结失败正样本验证；字典/持续capture/集成/现场/发布待证。

最新研究：[普通DELETE/RESTORE及真实短Z缺口](RESEARCH_W4_DELETE_RESTORE_20261004.md)，源脚本4cb3693、驱动树6f49a87、研究比较器修正树6bd7a87，[清单](evidence/RESEARCH-delete-restore-20261004.json)698文件双次SHA一致。专属一次新实验冻结seq24（def82f0b…），A删除50、B删除10后rollback、A再rollback；四叶slot1/0/0/1与raw/native零差异，A/B补偿头关联4/2含A的2外国marker零差异。真实B index RESTORE短Z为030db87100000000；owned清单A/B、A位置及A/B图五入口均rc1/stdout0拒绝，未到DELETE位置模型验收。首轮比较器错误沿用PURGE全零Z及native反例选择器断言保全，补充仅失败/未执行检查，三比较器反例检出1/1/2差异；events拒绝。内核仍184测试树582fec6无变化。接续直接补真实短Z RESTORE布局/反例、清单及B图，再实现有证据的DELETE/RESTORE与PURGE位置模型；字典/持续capture/接入/发布待证。

最新研究：[两次PURGE顺序/完整图缺口](RESEARCH_W4_DOUBLE_PURGE_20261004.md)，源脚本a5846cd、完整研究树4249b73，[清单](evidence/RESEARCH-double-purge-20261004.json)411文件双次SHA一致。专属一次新实验冻结seq23（da6a0d7c…），A删除50、B插入20/30后整事务回滚、A再回滚；六叶slot1/1/2/2/1/1及C/Z PURGE raw/native零差异，A/B owned清单6/4及头关联6/4（A含4外国marker）零差异。B自身第二次取消也因中间PURGE位置拒绝，rc1/stdout0，完整A/B图均不计通过。三比较器反例检出1/1/2差异，events拒绝。main/test/pom与184测试内核582fec6差异为空；不重复已通过测试。接续普通DELETE/RESTORE及有源归属PURGE模型，用本冻结B图作修复前失败正样本；字典/持续capture/接入/发布待证。

最新研究：[较大键INSERT/PURGE位置](RESEARCH_W4_HIGHER_PURGE_20261004.md)，源脚本f332264、完整研究树3d05413，[清单](evidence/RESEARCH-higher-purge-20261004.json)390文件双次SHA一致。专属一次实验冻结seq22（b1fcd87a…），A删除50、B插入70后回滚、A再回滚；四叶slot1/2/2/1与raw/native零差异，A/B完整清单4/2、补偿头关联4/2含A的2外国marker、B图2/2/0均零差异。三项B图派生反例rc1/stdout0；A位置仍拒绝PURGE，A单上下文图仍缺B原始节点，旧静态单match不当A完整图通过。main/test/pom与184测试内核582fec6差异为空，不重复已通过测试；events拒绝。两笔单键净零PURGE实例不能推导通用规则，接续多PURGE/普通DELETE实际矩阵及有源归属位置模型，W5/持续capture/接入/发布仍待证。

最新：[有界并发分发/B物理回滚图交付](DELIVERY_W4_CONCURRENT_ROUTING_20261004.md)，完整研究树40a0fda，main/test/pom与582fec6差异为空，[清单](evidence/DELIVERY-concurrent-routing-20261004.json)。同件184测试jar，双上下文消费者完整消费A窗口四项关联，把B送到B生命周期/物理match/head，B图2/2/0与native零差异；七项分发派生反例rc1/stdout0。full路径先真关闭B，再以A中间PURGE拒绝，越过此前缺B原始节点门禁，仍不认证A图。foreign-only明确只验证B图及A关联；events继续拒绝。无数据库动作，下一步扩大PURGE较小/较大/多次及普通DELETE实际矩阵，生产分发/字典/持续capture/发布待证。

最新：[同源完整XID补偿头关联交付](DELIVERY_W4_COMPENSATION_ASSOCIATIONS_20261004.md)，实现12910d5、完整封存树582fec6，[清单](evidence/DELIVERY-compensation-associations-20261004.json)543文件双次SHA一致。184测试通过；内核自主遍历完整归档，绑定唯一先前原始头与同源完整事务窗口，seq19—21共10项/其中2外国marker与native全字段零差异；8真实查询拒绝和3比较器反例。仅认证header关联，不认证物理行/当前head或取消，cross-block和同record原始关联仍未支持。A旧图、PURGE位置及events继续拒绝，没有补填通过计数。无数据库动作，旧解析类未变且不重复已通过回归；接续消费者须完整分类并独立核B物理/head/取消图，再补PURGE矩阵。

最新：[短Z PURGE/XR DELETE交付](DELIVERY_W4_Z_ROLLBACK_20261004.md)，运行时a6818ea、完整封存树919e6ee，[清单](evidence/DELIVERY-z-rollback-20261004.json)974文件双次SHA一致。175测试通过；真实index Z固定前缀PURGE与heap短Z XR DRP物理输出native零差异，不补造XID/UBA/列值；11项真实vector派生拒绝。seq21 A/B完整同叶清单4/2项native零差异，B图2/2/0通过；A现在分别到达中间PURGE位置拒绝与B marker缺原始节点拒绝，仍未验收。同件jar九笔旧图34/23/11、六批heap756/430/326回归零差异；events继续拒绝。本轮无数据库动作，接续先攻并发marker完整归属，再补PURGE位置及更多实际布局。

最新研究：[中间PURGE/其他XID回滚](RESEARCH_W4_INTERVENING_PURGE_20261004.md)，源脚本0017daa、完整研究树cea54f9，[清单](evidence/RESEARCH-intervening-purge-20261004.json)。专属源一次新实验冻结seq21（f01a212c…），A删除50、B插入20后rollback、A再rollback；native/raw四叶操作slot1/1/1/1零差异，前三record SCN相同，须按RBA/vector排序。真实B index PURGE为短KTB Z，owned清单/位置与A/B图均rc1/stdout0拒绝；B heap短Z XR DELETE直接拒绝。旧静态A单补偿rc0不当完整图通过。无内核变化，五项是不支持入口证据而非安全派生反例。接续先补两个真实短Z布局与反例，再验证清单、PURGE位置及并发marker完整归属；events继续拒绝。

最新：[多次INSERT并发图交付](DELIVERY_W4_INDEX_MULTI_GRAPH_20261004.md)，完整研究树e3552af，main/test/pom与aa44b0d差异为空，[清单](evidence/DELIVERY-index-multi-graph-20261004.json)。冻结seq20同件jar两笔图8原始/2取消/6保留与native零差异；七项调用者选择/模型派生反例rc1/stdout0，三项比较器反例检出。首次wrong-live派生未改变的封存失败保留，只补跑正确反例及未执行events，events继续拒绝。无数据库动作，本批有限位置和选定图验证完成；中间PURGE/普通DELETE、完整控制/字典/持续capture/发布仍待证。

最新研究：[多次并发INSERT位置](RESEARCH_W4_INDEX_MULTI_INSERT_20261004.md)，源脚本d3fd77b、完整研究树c469cd3，[清单](evidence/RESEARCH-index-multi-position-20261004.json)。专属源一次受控实验冻结seq20（620c6361…），A删除50，B按20/70/30插入后提交，A回滚；native slot1/1/3/2/3及三键raw字节零差异。已交付同件jar精确关联A原slot1→RESTORE slot3，旧静态matcher继续拒绝；无内核变化。新批完整A/B heap+index图、反例覆盖、其他中间操作仍待证，不补填旧批图计数或开放CDC。接续优先补本冻结批图和漏vector反例，再研究前方PURGE。

最新：[同叶INSERT位置重放与短Z回滚交付](DELIVERY_W4_INDEX_POSITION_20261004.md)，完整内核树aa44b0d，补充lab驱动b934d99且main/test/pom差异为空，[清单](evidence/DELIVERY-index-position-20261004.json)封存1030文件双次SHA核对。167测试通过；真实A原slot1经B插入变slot2成功关联，native零差异；A/B heap+index图4原始/2取消/2 live、4新heap pair零差异。同件jar旧五笔混合图22/19/3与六批heap756/430/326回归通过；7位置+4图+7短Z真实vector派生反例rc1/stdout0。中间DELETE/PURGE/RESTORE仍拒绝；不将有限图当完整DML/字典认证。首次合成marker错误及真实短Z拒绝原文已保全，无新数据库动作，events继续拒绝，目标保持active。

最新：[同叶完整操作清单交付](DELIVERY_W4_INDEX_LEAF_INVENTORY_20261004.md)，最终完整树13a87c0，[清单](evidence/DELIVERY-leaf-inventory-20261004.json)。154测试通过；内核绑定完整归档，在verified record窗口收录同叶其他XID操作，真实A窗口3项含B插入、B窗口1项与native窗口/字段零差异。4选择反例拒绝；3重封raw反例结构scan rc0而清单拒绝未知10.31/4.1/缺B undo；3比较器反例检出。同件jar五笔混合图22/19/3回归通过、slot迁移matcher和events继续拒绝，1000文件SHA重算。没有数据库动作；位置重放尚未实现，block/object字典映射/完整并发图/页维护/持续capture/发布未验收。

最新：[并发索引slot迁移反例](RESEARCH_W4_INDEX_SLOT_SHIFT_20261004.md)，研究完整树b19e5a2，[清单](evidence/RESEARCH-index-slot-shift-20261004.json)。专属无supplemental新表一次性A DELETE键3、B INSERT键2提交、A rollback；seq19 SHA497d09a5…冻结，同一叶原DELETE slot1而RESTORE slot2，11index vector/native零差异、3比较器篡改反例检出。既有5002f18内核matcher rc1/stdout0、MISSING_INDEX_COMPENSATION_ORIGINAL；是真实功能缺口，不算并发DML支持。569文件SHA重算，首轮PLSQL保留字及封存Java8误选失败保留；生产内核不变、147测试不重跑，events拒绝。下一步按W4-SLOT完整同叶操作流跟踪位置并保持错误slot/缺操作拒绝，不能删slot条件。

最新：[index RESTORE与主键回滚图交付](DELIVERY_W4_INDEX_RESTORE_20261004.md)，完整封存树5002f18，[清单](evidence/DELIVERY-index-restore-20261004.json)。147项测试通过；seq18两笔新混合图13/12/1与native零差异，索引取消4 RESTORE+3 PURGE；9新heap pair逐列字节零差异，8新派生rc1/stdout0。旧三笔混合图/index及六批756/430/326同件jar回归通过，events拒绝。首次full图遇heap0x12 C-cleanout拒绝及研发jar/diff保全，补真实70字节XA DELETE入口后重新封存。无源端动作；slot迁移/页维护/并发/恢复/其他控制/W5及持续capture仍待证。

最新：[真实主键更新及index RESTORE研究](RESEARCH_W4_INDEX_RESTORE_20261004.md)，fixture418cd0c、研究完整树d957a34，[清单](evidence/RESEARCH-index-restore-20261004.json)。专属新表一次性主键UPDATE/DELETE/savepoint及full rollback，seq18 SHA5d5203bc…冻结；35index相关vector/native直接字段零差异，出现4条10.5 RESTORE，现有matcher均拒绝UNSUPPORTED_INDEX_PURGE_LAYOUT。SQL两阶段最终3行通过，研究比较器7项反例检出。生产内核9e80b0f不变，138测试不重跑；下一步RESTORE匹配与7索引取消/混合图，不能以SQL最终相等或结构scan代替DML支持。

最新：[索引补偿与混合undo图交付](DELIVERY_W4_MIXED_UNDO_20261004.md)，最终完整封存树9e80b0f，[清单](evidence/DELIVERY-mixed-undo-20261004.json)。138项测试通过；三笔完整XID的heap+index原始9/取消7/保留2与native有序重建零差异（index取消3、heap取消4）；6项漏index节点/错误purge slot派生输入rc1/stdout0。同件jar14index pair/12拒绝及既有heap补偿回归，events拒绝。无数据库动作；下一步index RESTORE及主键更新回滚专属fixture，其他控制/并发/恢复/W5/持续capture仍待证。

最新：[索引undo配对交付](DELIVERY_W4_INDEX_PAIR_20261004.md)，完整封存树b0771f7，[清单](evidence/DELIVERY-index-pair-20261004.json)。133项测试通过；seq8/9真实14index pair按同record唯一完整UBA与native零差异，12记录派生拒绝；同件jar既有18heap pair及4补偿/1保留回归，events rc1/stdout0。首次lambda编译失败保全；最终XID反例直接篡改forward当前身份。无数据库动作；下一步3条索引补偿归属及heap+index取消图，SCN字典/逻辑DML/发布未通过。

最新：[索引字段与完整输入研究](RESEARCH_W4_INDEX_FIELDS_20261004.md)，源码0880e03，[清单](evidence/RESEARCH-index-fields-20261004.json)。既有seq8/9主键索引35个相关vector与native位置/直接字段零差异（14原始undo、12insert、2delete、3purge、3补偿、1维护）；6项研究对账反例拒绝。首轮漏3补偿与验证器8处差异保全。无数据库动作，生产内核076865c不变；下一步完整XID/UBA索引配对及heap+index取消图，尚不证明完整DML或发布。

最新：[W7原始块观察交付](DELIVERY_W7_OBSERVATION_20261004.md)，源码7669d7c，[清单](evidence/DELIVERY-observation-20261004.json)。实际固定member采样journal与新fixture DML/seq17→18切换时间交叠，432帧验证、429归档数据块字节零差异；6真实journal派生反例拒绝。第一轮结束后才DML的编排错误保留，seq16相对journal missing284、rc1。Python3.6时间API和CRLF/LF源码封存失败保留。`capture_complete=false`；未自动跟组/续接/完成record/ACK，W7实验不算捕获验收，CDC拒绝；Java内核076865c不变。

最新：[整事务rollback交付](DELIVERY_W4_FULL_ROLLBACK_20261004.md)，封存源码076865c，[清单](evidence/DELIVERY-076865c.json)。124项测试通过；新seq15/XID2.13.581的120原始/120补偿/0 live与native零差异，20行恢复基线，有限0x14结束与两条短KTB Z完成；10真实结束反例及锁定、8真实Z反例拒绝。同件jar六批长事务756/430/326回归通过，首轮字段索引错误及实际KTB拒绝保留。并发/index/恢复/W5继续待证，CDC拒绝。

捕获问题：[W7执行包](DISPATCH_W7_ONLINE_CAPTURE_20261004.md)，[CURRENT只读探测](evidence/READ-online-open-20261004.json)。专属Linux Oracle运行中、LGWR存在、seq16 CURRENT只读1024字节rc0且读取前后未切换。只证明该次访问，不证明并发写一致性/持续流/Windows共享或ASM；当前归档copy不是ldfcap，W7捕获持久化未实现。

最新：[真实第二次savepoint回滚研究](RESEARCH_W4_SECOND_ROLLBACK_20261004.md)，研究脚本/驱动cfdcdc0，[清单](evidence/RESEARCH-second-rollback-20261004.json)。专属无supplemental新表一次性执行并冻结seq14/XID3.11.671，146原始/70补偿含9跨块/76保留与native零差异，最终两行SQL通过；真实记录阶段120节点→60补偿→20节点→10补偿→6节点。五批合计636/310/326同件既有jar回归通过，生产内核9a2344b不变。首次研究编译依赖漏项和native未来候选误纳失败均保全，验证器按native RBA修正。整个rollback/index/恢复/W5待证，CDC继续拒绝。

最新：[活动窗口slot冲突拒绝反馈](DELIVERY_W4_WINDOW_SLOT_20261004.md)，完整封存树9a2344b，[清单](evidence/DELIVERY-9a2344b.json)。117项测试通过；四批各3份原始归档副本改写完整sequence并重封checksum，12个结构scan均rc0，旧覆盖读取器均rc0，新读取器均`TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE`、rc1/stdout0。活动窗口现在审计同segment/slot的完整sequence冲突，合成测试验证失败锁定；不是实际slot复用支持。四批原始240候选/490节点/240补偿/250 live及SQL同件jar回归通过。未做数据库动作；并发归属/其他控制/index/恢复/W5仍待证，CDC拒绝。

最新：[补偿候选覆盖门禁反馈](DELIVERY_W4_COMPENSATION_INVENTORY_20261004.md)，完整封存树952e65d，[清单](evidence/DELIVERY-952e65d.json)。113项测试全通过；内核自主遍历同PDB目标活动record窗口，四批240个5.6/5.11候选与native位置/字段零差异，20项真实清单派生选择反例rc1/stdout0；同件jar490节点/240补偿/250 live及最终SQL回归通过。lab关闭history前强制核对候选覆盖。候选为保守超集，不认证并发归属；其他控制/index/恢复/W5继续待证，CDC拒绝。首轮单测反例偏移错误及失败日志已保全。

最新：[有序分配历史与生效head反馈](DELIVERY_W4_ORDERED_HISTORY_20261004.md)，封存源码8a18e3d，[清单](evidence/DELIVERY-8a18e3d.json)。109项测试全通过；四批490原始节点、240补偿/250 live heap片段与native零差异，seq13最终SQL两行通过；seq10的15条完整前驱继续得到独立页CNT佐证。9项真实派生拒绝与锁定。history只覆盖5.1及调用者已匹配的补偿，不能证明补偿全部收到；下一步主攻完整相关控制/补偿输入门禁，index/恢复/W5仍待证，CDC继续拒绝。

最新：[已分配块head-update反馈](DELIVERY_W4_HEAD_UPDATE_20261004.md)，源码6d30626，[清单](evidence/DELIVERY-6d30626.json)。104项测试全通过；5.2/0x0002新UBA控制与完整活跃身份native零差异，10项真实派生拒绝及锁定。四批490节点、240补偿/250保留patch含31跨块，同件jar回归零差异；seq13最终两行SQL通过。完整生效图/index/恢复/W5仍待证，CDC继续拒绝。上轮网络推送超时，本地交付不代替远端同步证明。

最新研究：[rollback后继续写入](RESEARCH_W4_RESUMED_WRITE_20261004.md)，fixture e322007、研究driver ea0e0d8。[清单](evidence/RESEARCH-resume-fixture-20261004.json)封存seq13/XID10.2.1163。130原始节点、60补偿含8跨块、70保留patch与native零差异，最终两行SQL字节通过；旧三批补偿回归零差异。首次resume使用5.2/0x0002及原分配块record3，prev DBA指向保留head，旧cursor保守拒绝。生产内核未改，完整图/index/新控制/恢复继续待证，CDC零发布。

最新：[原始undo选择覆盖反馈](DELIVERY_W4_UNDO_INVENTORY_20261004.md)，源码9ffc19b，[封存清单](evidence/DELIVERY-9ffc19b.json)。98项测试全通过；内核自己遍历归档生成360个目标5.1节点清单，与native零差异；三个真实漏项选择均rc1、stdout空，三个完整选择通过。同件jar180补偿及旧18pair/4补偿回归通过。覆盖仅限给定输入中的目标5.1，不证明事务图/所有相关opcode/块generation或恢复；CDC继续拒绝。

最新研究：[独立前驱页CNT佐证](RESEARCH_W4_CURSOR_SNAPSHOT_20261004.md)，源码140711e。既有seq10的15个跨块前驱页sequence/CNT与完整cursor零差异，缺节点cursor的首条边被独立页值拒绝。第16页不被跨块previous DBA引用且身份不同，明确保留而不认证。无新数据库动作；事后页面时间一致性/通用raw块覆盖仍待证，生产制品与CDC拒绝门禁不变。

最新研究：[跨块cursor缺节点反例](RESEARCH_W4_CURSOR_HOLE_20261004.md)，研究源码b64a090，生产制品仍0445b43。三个冻结正样本各120节点零差异；各省略一个跨块前驱节点后，119节点仍通过15次局部切块及提交尾检查，但native全量比较均rc1。研究模式刻意调整fixture计数，未修改生产拒绝门禁；证明局部cursor约束不足，完整输入与跨块前驱仍需独立证据。无新数据库动作、CDC零发布。

最新：[普通跨块5.11与多heap block反馈](DELIVERY_W4_CROSS_COMPENSATION_20261004.md)，源码0445b43，[封存清单](evidence/DELIVERY-0445b43.json)。93项测试全通过；三批360pair/180补偿/180保留物理patch与native零差异，23个普通跨块5.11匹配；9项真实派生拒绝与锁定。946物理patch字节及新两批最终SQL值通过。不同heap block的118个历史KTB引用反驳通用前驱替代规则。实验cursor的完整跨块前驱仍未独立认证，index/恢复/W5仍待证，CDC零发布。

最新：[跨块控制身份反馈](DELIVERY_W4_UNDO_ADVANCE_20261004.md)，源码b99828e，[封存清单](evidence/DELIVERY-b99828e.json)。87项测试全通过；长事务120 undo、15显式UBA切换、1扩展提交身份闭合，17个控制/起止字段与native零差异；9项真实派生拒绝、8生命周期锁定。302patch和旧18pair同件jar回归通过。完整跨块前驱/60撤销保留集合仍待证，CDC零发布。

最新：[KDOM2与跨块样本反馈](DELIVERY_W3_KDOM2_20261004.md)，源码6985d9c，[封存清单](evidence/DELIVERY-6985d9c.json)。82项测试0失败0跳过；302个长值正向/逆向物理patch与native dump零差异，末XR与SQL keep-060值一致；6项真实派生反例+15真实跨块入口拒绝。长事务1.1.604跨16块，跨块链接/完整保留集合尚未验收。原seq8/9的18pair同件jar回归通过。此前11原始同块链/32节点与四heap补偿证据继续有效。W5及集成仍待证，CDC零发布。

补充：[事后undo快照拒绝研究及更正](RESEARCH_W4_UNDO_SNAPSHOT_20261004.md)。只读16块dump中，0x02400089页头XID与归档长事务不符，研究器按未证页头身份假设保守拒绝，rc1、stdout空，不能据此证明页面复用。0x02400088的raw原始节点已为sequence0xe7，上一版称序号变化错误已更正。正式制品不变，继续从raw归档论证跨块链。

最新：[5.11 begin补偿反馈](DELIVERY_W4_BEGIN_COMPENSATION_20261004.md)，源码d3f07b3，[封存清单](evidence/DELIVERY-d3f07b3.json)。72项测试0失败0跳过；四次5.6/5.11补偿原操作与dump零差异，保留row7四列与SQL字节一致，缺归档反例stdout空。index/完整undo链、恢复及W5仍待证，CDC零发布。

最新：[5.6补偿关联反馈](DELIVERY_W4_COMPENSATION_20261004.md)，源码0c5ff4c，[封存清单](evidence/DELIVERY-0c5ff4c.json)。66项测试0失败0跳过；T007撤销原undo36 INSERT8/35 UPDATE，保留undo33 INSERT7，与dump及SQL row7四列字节零差异。两个缺archive反例均rc1且stdout空。5.11、index/完整undo链、恢复及W5仍待证，CDC零发布。

最新：[精确行/undo反馈](DELIVERY_W3_PAIR_20261004.md)，源码3e1b5bc，[封存清单](evidence/DELIVERY-3e1b5bc.json)。57项测试0失败0跳过；18对、48正向/10逆向出现列与dump零差异；6项真实record内存派生反例全部拒绝。完整行、活跃事务集成、savepoint撤销与W5仍待证。

最新：[逆向行片段反馈](DELIVERY_W3_UNDO_ROW_20261004.md)，源码3418fb9，[封存清单](evidence/DELIVERY-3418fb9.json)。51项测试0失败0跳过；18个5.1逆向patch/10出现列与dump零差异；同件jar回归21个旧物理patch/45出现列零差异。完整before行、XA/undo配对、补偿链与W5仍待证。

最新：[有界身份生命周期反馈](DELIVERY_W4_LIFECYCLE_20261004.md)，源码8859c7d，[封存清单](evidence/DELIVERY-8859c7d.json)。46项测试0失败0跳过；11个身份（9commit/2rollback/1跨archive）与dump零差异；缺开始、缺结束、重复archive均rc1且stdout空。行变化、savepoint撤销、undo链与W5仍未验收，CDC零发布。

最新：[起点身份绑定反馈](DELIVERY_W4_BEGIN_20261004.md)，源码0b36846，[封存清单](evidence/DELIVERY-0b36846.json)。36项测试0失败0跳过；11个目标表5.2与唯一同record5.1 begin精确UBA绑定，dump零差异。结束/补偿链、跨归档连续性及W5仍待证。

最新W4：[结束/补偿字段反馈](DELIVERY_W4_MARKERS_20261004.md)，源码b188709，[封存清单](evidence/DELIVERY-b188709.json)。30项测试0失败0跳过；同件jar对86条5.4、5条5.6、2条5.11与dump零差异。5.11 wrap1不等于事务sequence；commit vector可能位于undo vector之前。没有事务关联或CDC验收。

最新进展：[W3首轮反馈](DELIVERY_W3_PATCH_20261004.md)，已推送ea709ae；[同提交树清单](evidence/DELIVERY-ea709ae.json)。24项测试0失败0跳过；同19c seq8/9的21个物理patch/45个出现列、18个undo header与dump零差异。KTB L为旧ITL、XR补偿可恢复旧UBA，不能据此猜当前事务。完整CDC仍零发布，W4/W5继续。

1. 先读19c fixture stdout/stderr/commands/manifest，失败不得重跑已提交DML；修复只读export或用新fixture。
2. 冻结同一输入做19c scan/record/vector + dump独立对账，保留失败目录。
3. 按[W3-W5 DISPATCH](DISPATCH_W3_W5_NOSUPP_20261004.md)攻row/undo/事务关联，优先证明缺主键/ABSENT与NULL区别及rollback补偿，不先做GUI。
4. 有源码与针对性证据后立即提交、推送反馈；后续HIGH门禁不被初始代码交付替代。
5. 长目标保持active；恢复接续automation id=`oracle-redo`，每小时绑定本chat检查。额度未恢复/无行动变化保持安静；用户暂停/取消时停止接续。当前模型要求保持，禁止Luna。

技术方案：[Oracle Direct Redo](../Oracle_Direct_Redo_技术方案_v0.1_20261004.md)。长期目标、真实DML、真实capture、目标写入、独立评审、GA均未完成。
