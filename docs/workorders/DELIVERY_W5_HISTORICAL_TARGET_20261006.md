# W5 历史完整事件与目标原子提交候选

根代码 `020be30`，私有产品代码 `65e12c2`。[216 项封存清单](evidence/DELIVERY-historical-target-20261006.json)全部双 SHA 及封存后核验通过；前序协议 241、投影 75、完整行 189 文件逐项未变。共享 StreamHouse checkout 只读，旧 seq44—69 源动作未重放。

| 制品 | SHA256 | 验证 |
|---|---|---|
| kernel | 240c2250f00256209bf02ccb9b17918e90797c920e113ac552db2881c678c25c | 复用已通过299，不重跑 |
| adapter | 65e0fcbad44f9df769db48fdf86ea66f13ab232d6c7872c25ee64c90a1d40dbe | 完整26/0/0/0 |
| 产品 | ac98404df7f4bbe0d0bda83e7a60045a3283552663a5905e47ff6adddc74ec8a | 完整746/0/0/0 |

adapter 安装坐标 `com.streamhouse:streamhouse-engine-oracle:0.1.0-historical-target-65e0fcbad44f`，产品真实嵌入该 adapter 与 `com.streamhouse.oracle:oracle-redo-kernel:0.1.0-schema-protocol-240c2250f002`，逐 jar SHA 一致；产品 SPI jar 为 ed85e853，全部 class 与原冻结 SPI53b84d18 一致。构建时完整候选源码已纳入上述代码提交，未拿旧18f49be4冒充本轮产品。

历史私有 factory 接入原 Batch；可信登记的有序 schema 摘要选择历史 decoder，仍核完整 evidence/envelope SHA。target receipt 同时保存 baseline schema 与有序 schema 摘要；IDENT 保留 baseline，COMMITS 使用序列摘要，重复/独立 verifier 使用相同绑定。control CP 新关联完整保留，旧空/null字段按原 JSON 形状省略；错 receipt 回滚不推进。产品新增3项单测通过，但本轮未执行历史 HTTP/CP/ACK。

目标映射限定为受控预建 nullable 列并集，无自动 DDL；同名列 metadata 不一致、缺必填和未知列拒绝。物理目标缺席 nullable 列补 NULL，APPLIED 保留原逻辑像，不把第二事件三列像伪造成四列。

原早 V3 SCN2536302/SHA226c9322 与冻结 seq68/69 经原始字节工厂产生两完整 UPDATE。owned 同实例目标 `ORKTGT_CMT_20261005.HS6_*` 新建5表，初始化0/dictionary-restored/anchor/NULL：apply2、verify、重复新增0；独立 SQL 核两成员顺序、eventId/payload/全部 before/after、逻辑4→3列、hidden NOTE、EXTRA_V3 NULL、commit序列摘要及IDENT基线一致。最终0/history-v3-restored/anchor/NULL；重复独立状态字节相同。

`HSL6_*` 新目标第二事件触发 CHECK ORA-02290；客户端保留 `OBSERVED_ROW_UPDATES=1;ROLLBACK=true`，独立 SQL 证明目标回baseline、APPLIED/MEMBERS/COMMITS均0。另精确篡改 HS6 commit 序列摘要，独立 verifier rc1/stdout0/ORACLE_TARGET_COMMIT_CONFLICT；finally 精确恢复并核验通过。原始失败完整保留。0源DML/配置变更，NO/NO与旧保留行核对不变；Java helper均一次性退出。

| 状态 | 结论 |
|---|---|
| 代码交付 | 通过：完整两仓候选 |
| 针对性验证 | 通过：26/746及两事件目标正反例 |
| 集成验证 | 通过有限 direct writer；历史产品HTTP/CP/ACK待证 |
| 部署 | 不适用：仅owned目标实验；服务未换装 |
| 现场验收 | 待证；独立评审PENDING |
| 发布 | 未开始 |

下一单：[可信 V3 产品获取与历史发布执行单](DISPATCH_W5_V3_PRODUCT_RUNTIME_20261006.md)。原可信V3获取回执尚未登记到产品，禁止手造账或用晚SCN回填68/69。原全goal active；活动跨窗、正式C++采集/租约、独立目标、类型环境/高压断电、独立评审现场发布均待证，Codex卡顿最后查。PR#13保持draft依赖#12。
