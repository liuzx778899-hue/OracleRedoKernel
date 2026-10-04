# W7 原始关闭代际跨member捕获

代码f46e776，Java内核未改，不重复233项已通过测试。普通文件路径增加`follow_closed_members.py`，捕获原始online成员的关闭代际；不要求源库开启新的归档、补充日志、业务DDL或重启。本轮专属环境本来就是ARCHIVELOG，三次显式归档仅作实验切换与独立真值，不能据此宣称NOARCHIVELOG或实时延迟通过。

## 有界真实证据

源DBID1276726821、activation1276717861、thread1、resetlogs1245693159。成员redo01/02/03各209715712 bytes；初始原始头seq40 CURRENT NAB=0xffffffff，旧关闭seq38/39有NAB15/12。collector只读轮询三个member，核对source身份及文件inode/大小，关闭头NAB/first-next SCN，完整关闭范围双读一致、头一致、全部块checksum/index/sequence。保存ORACAP01原始块帧，journal fsync后原子发布带SHA/关闭边界的receipt。block0保留online容量，block1保留实际关闭头；没有改NAB、容量或校验和伪造归档。

一次新实验：只读获取实际基线回执SHA `646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf`；既有ID11 TXT从blocking-closed依次CAS到capture-follow-a、capture-follow-b、再恢复blocking-closed，每步commit并专属实验归档。三次源命令均rc0，NOTE仍new-note；已提交动作不得重放。每步等collector保存该代际后才继续切换，故这是受控速率矩阵，不是任意切换压力证明。

| sequence/member | 原始关闭块数 | first→next SCN | journal SHA256 |
|---|---:|---|---|
| 40/redo01 | 14749 | 2362469→2366739 | 97ffa0ece0e233092bdfad4fb5c0c896bd591648b0295cc42b485d71754359fe |
| 41/redo02 | 7 | 2366739→2366749 | 36732c64aa8b20280d14d0ae4fab6dcaa795029746cf6d7ef65446ed71d23f8f |
| 42/redo03 | 9 | 2366749→2366759 | bb9ce199f50ad1fc07ed5b6e802215b3c561cd38d948ea8bcf37e0f86fb49116 |

原始关闭范围共14765块/帧；独立随后冻结archive的14759个数据块全部找到字节相同的稳定观察，无缺块/差异。关闭头未归一化，不宣称online和archive头相同。collector完整终止rc0，next_sequence43。

随后redo01真实复用至seq43，另一个只读collector从40追赶：CAPTURE_MISSING_GENERATION rc1，没有捕获代际输出/receipt，不能静默跳过40。七个真实闭合前缀派生反例：checksum、重封NAB越界、重封块号、重封DBID混流、缺41、两个不同路径同41、42 first SCN+1，均指定拒绝。SCN gap分支先有合法41 receipt，42 journal不是已验收代际；捕获持久前缀与整条CDC流验收分开。派生fixture超NAB尾部是人工零稀疏区域，不当源原始全文件真值。

增加布局/源profile/online容量及读后inode检查后的最终源码，在真实当前成员只读复采41/42并独立对账零差异。已从容器导出执行文件，与提交工作树字节一致：collector SHA `20a9126ad23af46ba5f7aec30c727d41b8c78121ed5a6cc58d2f896ebcf37024`，helper SHA `fc0155f98ce9651c9f08a581ba28ccf8e8c98b4302d347b04570e005a8602f4b`。首轮40—42执行在这些追加检查之前，当时未封存执行源码SHA，不将首轮三组实验冒充最终制品三组验收；其原始journal/归档/命令/源动作事实保留。后续driver已补执行源码冻结和容器SHA核对，但新增driver封存步骤本轮未重跑。

[证据清单](evidence/DELIVERY-closed-follow-20261005.json)双次SHA关联三个lab/out目录、源码、基线、原始journal、归档、完整命令/rc/stdout/stderr。无口令或原始业务数据上传。

## 接续门禁

这条路径有日志组关闭延迟；当前未关闭代际不产出完整record。多member collector尚无进程重启续接，之前固定member续接不是本collector的恢复验收；本轮未注入读中复用、真实断电或高压切换。regular-file/Linux512/19c有限profile不证明Windows、ASM、RAC、多副本去重、NOARCHIVELOG可用性。capture_complete=false、consumer_ack=false、事件0。

下一步Java直接从保留原始头的journal验证完整关闭范围并重组record/vector，复用现有字节解析而不造归档文件。独立native/dump/SQL只作比较器。再补跨代际collector恢复、消费位置/ACK及W6接入；全流程、现场、发布仍未通过。
