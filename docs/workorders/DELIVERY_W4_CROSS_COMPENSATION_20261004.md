# W4 普通跨块5.11补偿交付与多heap block反证

源码`0445b4328f24ef6fbe2cb85ac0c9a4e26caa0446`，main；完整提交树独立构建，93项测试0失败0错误0跳过。同件jar SHA256 `931840b788e41def334f91a4f2e222edf28ffe63162d2f883bdbcaaaba8d40c9`。[封存清单](evidence/DELIVERY-0445b43.json)关联源动作、三个归档输入、SQL/native真值、构建、正反例和失败日志。本轮仅专属19c实验源的新表动作；无业务库改动、解析器部署或StreamHouse目标写入。

## 实验条件与输入

源`oraredo-19c-lab-20261004`，19.3，supplemental MIN/ALL均NO/NO，实验ARCHIVELOG沿用既有实验配置。新表动作各执行一次，未重放已执行SQL。

- `T_UNDO_MULTI_20261004`，object/dataObject73045，PK index73046；插入两行900字节baseline，随后交替更新60keep、保存点、60discard、撤销至保存点并提交。BASE_TX10.9.1156；CHAIN_TX未出现在实际SQL stdout，10.23.1156来自raw/native独立匹配。两行同heap block，不能当成多heap block反例。
- `T_UNDO_MULTIBLOCK_20261004`，object/dataObject73047，PK index73048；插入20行900字节baseline，读ROWID断言ID1和20确属不同block后才继续交替更新同样120次及保存点撤销。源stdout直接给出ID1 relative file12/block181/slot0，ID20 file12/block183/slot5，BASE_TX10.8.1158，CHAIN_TX3.26.669。两行最终900字节TXT为keep-1-060及keep-20-059填充。
- seq11：1,182,720 bytes，SHA256 `307ce3ae8a0b685da8c63ef3e4b121e4eee58ef02b54333b3f1086e3a6863a66`，SCN[2158819,2161353)，2641 structural records；源窗口2161304..2161349。seq12：427,008 bytes，SHA256 `0b7e6a6a6f21fed1416c3d0ecde3827d350a252f5785aa73e7f0958906c233d4`，SCN[2161353,2161538)，源窗口2161483..2161534。实际执行SQL、stdout、归档和只读选窗dump分别冻结；private raw留本地。

## 从反例到代码

跨不同heap block的seq12出现118个历史KTB UBA与实验性事务cursor候选前驱不相等的节点；同heap block的seq10/11该差异为0。因此单行/同块观察不能推导通用链，不能把恢复后的行ITL UBA当事务原始前驱。

原实验驱动只选择5.6补偿，到第5个撤销即失败：RBA709:48，原undo index114，而预期115；漏掉的是RBA706:440的普通跨块5.11。原parser将5.11限定为begin形态，对此新布局会拒绝，并非已错误发布事件。原始失败和带定位信息的第二次失败保留，没有删除节点后继续宣称完整撤销。

`UndoCompensation`现按真实flags与metadata区分三种观察形态：5.6普通补偿；5.11/0x0c18 begin补偿；5.11/0x0010、非零previous DBA、rci0的普通跨块补偿。5.11不再一律称begin。未知形态仍拒绝，非法5.6伪造跨块分支拒绝。

`crossCompensationIdentity`通过当前source record UID、CON_ID、segment DBA及slot查已验证活跃完整XID。`HeapCompensation.matchCross`使用完整XID、原undo object/dataObject/ts/previous DBA/rci/wrap，以及恢复行的kind、DBA、slot、列状态和字节匹配唯一原pair；segment DBA不当原undo block DBA，低wrap不替代完整sequence，恢复KTB UBA不作当前身份。失败锁定生命周期。普通5.6与begin分支保持独立验收。

## 同件制品验证

seq10/11/12各120个原始heap pair、60个补偿、60个保留heap patch；总360 pair、180补偿、180保留patch。跨块5.11补偿分别8/8/7，共23；native独立核对补偿metadata、恢复的900字节旧值、保留物理patch及候选唯一性，三批零差异。保留patch是操作片段，不是完整before/after行或已发布事件。

三批物理列对账分别302/304/340 patch，共946；新两批644patch零差异，末XR恢复的两行TXT与独立SQL真值一致。source字典仅为这三个受控两列fixture提供参数，尚不能冒充生产SCN字典。

9项真实seq12 vector内存反例：缺原pair、重复pair、相同低wrap但完整sequence加65536、错误segment、错误恢复值、错误previous DBA、marker不在record、错record UID、缺previous DBA。9/9预期拒绝且9个生命周期锁定。harness rc0为审计成功，stdout是拒绝报告，不是事件；派生反例不算新的Oracle源动作。

新增6项测试覆盖普通5.11与begin区分、活跃完整身份、历史UBA不作当前undo、低wrap碰撞、布局/metadata拒绝、重复候选与错恢复值。93项全通过。旧seq8/9的18pair/4补偿（2begin+2普通）及row7四列SQL字节同件jar回归零差异；旧21项KDOM2/未证跨块入口拒绝继续通过。

实验性`UndoCursorProbe`在完整raw记录流上关联各120原始节点、15显式UBA切换与原生commit tail。`compare_cursor_fields.py`仅独立认证当前UBA、previous DBA/rci、位置与历史KTB等直接字段；跨块前驱sequence/record仍由候选cursor推导，刻意未认证为通用图。三批direct-field零差异不能升级成完整跨块前驱或任意缺节点安全验收。

验证器首轮END字段索引错误、随后只识别flags0x12导致seq12 flags0x2提交尾漏选，空/失败报告保留；修正验证器后通过，不为此放宽运行时parser。原始Maven阶段87项是尚未新增六项测试的研发构建；正式封存树运行93项。所有正式事件命令均rc1、stdout0，原因DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED。

## 验收与接续

代码与针对性跨块heap补偿匹配通过，W4整体集成仍进行中；部署/发布未开始，现场验收待证。完整原始跨块图、混合heap/index回滚集合、事务重试/checkpoint恢复、字典/基线、StreamHouse接入和在线采集均未关闭。

下一步把已证同块rci、显式UBA切换和全部补偿组合到受来源/record流完整性约束的原始图；针对缺末undo、缺控制、缺补偿、重复/重定向控制、跨归档、混合heap/index和恢复做独立反例。不得将实验cursor或仅最终两行一致当作这一门禁已通过。
