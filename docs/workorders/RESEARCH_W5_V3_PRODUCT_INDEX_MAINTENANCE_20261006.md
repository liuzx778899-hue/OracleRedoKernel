# 产品V3后新窗口：索引维护研究与拒绝交付

结论：真实产品V3基线之后的一次新窗口已保全；完整历史工厂拒绝，0事件。新增证据证明叶行守恒和五节点原始前驱图，不证明完整维护事务生命周期、历史DML、产品HTTP或现场发布。

## 输入与动作边界

- 实际 acquisition `100747510573780992`，SCN `2566695`，snapshot SHA256 `323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2`。不使用晚基线回填旧日志。
- 专属 `oraredo-19c-lab-20261004`：seq70 ADD EXTRA_HP nullable／UPDATE history-product-added，seq71 DROP／UPDATE history-product-restored；两次业务事务 XID10.15.2348、10.12.2321，动作已结束，禁止重放。NOTE anchor、旧11/blocking-closed/new-note保留，补充日志NO/NO。
- collector terminal rc0，next72，观察126644/18块与归档数据块零差异。原online header保留，未归一化；collector及比较器均明确 `capture_complete=false`，不宣称正式完整采集通过。
- 产品仍原 c47a728c/750候选，kernel240/adapter65，未修改生产代码、重建制品或重跑已通过套件。helper37261 running、自动消费关闭，实际只读审计 binding0/event0；没有新target/HTTP发布/CPACK动作。

## 本轮可核验证据

1. `first-projection` rc1/stdout0，`DICTIONARY_DISCOVERY_INDEX_COVERAGE`。六拒绝位置：70:126592:380:2、126593:240:2、126610:328:2、126619:224:1、126619:460:1、126628:476:1。对应维护10.6/10.9/10.8/10.11/10.15与主字典事务10.5，不跳过或缩减输入。
2. 原始8032字节前像：行目录偏移36，445行。两条10.8各含220、226行；row目录带末尾边界，实际rowdata偏移扣除逐行2字节。三个完整字节区域与native dump零差异。
3. 分裂前后无丢行，仅新增一条0101删除标记行。结束记录70:126620:204的旧叶块槽7携带key `04c308345602c105`、rowid `004089a40033`；后续70:126628:476的10.5 itl2/slot7/size18与其undo key一致。初始前像槽7是其他key，不能直接用于恢复。
4. 丢行、重复行、交换槽7/8、错key四诊断比较反例均检出。这不是合法checksum完整入口安全矩阵，后续必须补。
5. XID9.9.660五original UBA链 `02402f4f.e6.1 → 02402f50.e6.1 → 02402f51.e6.1 → .2 → .3`；原始前驱图与5.4 commit tail闭合，endSCN2567427，original/live均5。诊断仅闭合graph，未绕过生产lifecycle。
6. 完整lifecycle探针rc1/stdout0：`UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT`。native begin是flags044a、siz136、fbi152、pxid0、PDBUID677737149；现有入口未支持此profile。未扩大门禁。

## 失败与封存

205文件双SHA及写后复核：[清单](evidence/RESEARCH-v3-product-index-maintenance-20261006.json)。包括原始capture/归档/dump、命令stdout/stderr/rc、冻结探针、研究结果；lab/out不入Git。前一产品获取407项逐一SHA/大小不变，secret排除、不哈希。

提前启动投影时capture-reference尚未生成的预检失败、完整工厂首次拒绝、完整lifecycle拒绝均保留。首预检未执行解析。产品真实历史HTTP、目标commit、控制CPACK、独立目标、现场/发布仍待证，原完整goal active。

## DISPATCH：下一步无需新源动作

优先P0，当前chat负责解析，禁止Luna/子代理。使用本封存70/71及原产品基线，在新输出目录继续，205项不可覆盖。

1. 将044a begin作为独立typed维护profile研究，核全部字段、current XID/UBA/container和五节点/end；缺节点、错父身份/UBA/尾引用均必须拒绝。不能单改flags白名单就算完成。
2. 完整绑定锁、前像、两叶重建、邻叶prev、branch separator/child、维护commit及主字典10.5。全行守恒、页代际/位置和current ITL身份共同建立槽证据；foreign历史ITL不是当前身份。branch/link尚未验证，严禁跳过维护事务。
3. 最小typed实现接入自主历史发现与全original覆盖：维护只移动物理行，不制造逻辑新增事件；真实恢复必须从raw完整槽证据取得key/rowid，不能从SQL、dump、其他索引或fixture常量补齐。
4. 原完整capture入口输出两个完整业务UPDATE且schema4→3，与独立native/阶段SQL核XID、SCN、源位置、完整像和hidden NOTE；archive/capture及擦marker等价。维护节点缺失、错link/child/槽/rowid/UBA/世代做合法checksum全入口反例，rc非0/stdout0。
5. 新代码完成后才做必要内核及适配器/产品验证和真实嵌入核验；保留旧静态兼容，不重复源fixture。再真实产品HTTP→target commit→controlCPACK、重复/同件重启及错receipt零推进。依然不当独立目标/现场/发布。

验收状态：代码交付=研究工具；针对性验证=上述诊断通过及安全拒绝；集成验证=历史完整入口拒绝；部署=未换装；现场验收=未通过；发布=未开始。
