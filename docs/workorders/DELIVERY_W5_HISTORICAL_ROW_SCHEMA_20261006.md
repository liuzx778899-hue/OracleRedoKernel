# W5 实际历史 schema 完整业务行交付

代码 `9e720110101579a57979e5e7a9384d0f5d0a7ad8`，kernel SHA256 `3ccca289e717f7fd8f188af30c8625baea2577640fbac7b2ff1c3ae384346e55`；299 测试，0 failure/error/skipped。[封存清单](evidence/DELIVERY-historical-row-schema-20261006.json)含189文件双SHA，上一374项逐项未变。候选 `lab/out/historical-row-schema-candidate-20261006`；原日志和秘密不入Git。

## 完整业务功能结果

实际可信基线 SCN2536302/SHA226c9322，冻结seq68/69自主SYS事务历史→物理事务→完整行→新 `CapturedHistoricalHeapBatch`，实际2条UPDATE，生成完成前不输出。事件生产仅使用原始capture与可信baseline；native dump、SQL是独立比较输入。

|原始undo位置|完整XID|record提交SCN|列结构|完整before→after|
|---|---|---|---|---|
|68:34027:16:4|10.25.2305|2536404|ID/TXT/隐藏NOTE/EXTRA_V3|0/dictionary-restored/anchor/NULL → 0/history-v3-added/anchor/NULL|
|69:12:16:4|5.27.602|2536423|ID/TXT/隐藏NOTE|0/history-v3-added/anchor → 0/history-v3-restored/anchor|

两schema digest分别 `c88266cebfb41e24b20729bfaa31e36fea8b45467be4f3fdb85dfe6a3d69b4b2`、`9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0`；batch `f23d4f27da43cf0998a34c71e0da97fc4dbaedf0ea88da55de12d2fb410c07ec`。相同输入重复输出逐字节相同；四DDL marker擦除capture的schema/event ID/payload/完整行逐字节相同，batch digest因input journal SHA变化而不同。

独立native两记录的正逆TXT字节、row DBA/slot、完整XID、record SCN零差异；实际执行阶段SQL ADD/DROP行与schema相符，既有基线独立行/完整SYS ASOF证据衔接。没有新的业务ASOF查询，也没有以最终行相同代替过程。四个比较器wrong-before/schema/XID/order反例检出。索引leaf50342539沿用冻结格式证据；只读SYS.IND$基线root50342538不被误当leaf，stats blevel为空不证明leaf层数。

## 语义及安全结果

Timeline私有raw factory构造，与完整baseline/source/input headers绑定；物理pair/compensation与完整行按真正record SCN取schema，commit前用可信列identity映射已有行。新增nullable尾列NULL需要完整COL$ DEFAULT$为NULL；隐藏列保留。未知映射、非空default、事务跨schema和活跃同key重叠拒绝，尚未实现这些情形的完整支持。

原七checksum合法SYS篡改capture全由完整事件factory拒绝rc1/stdout0；新四业务反例wrong-before/fullXID/commit tail/未知opaque end word同样拒绝，不交付第一笔部分事件。旧V2静态三IUD输出逐字节不变、原/擦marker历史V2仍拒绝原原因。旧公开固定schema入口没有解除拒绝。

首轮完整事件实际拒绝 `UNSUPPORTED_TRANSACTION_END_EXTENSION` 已保全。seq69 native ext=0的opaque tail word0x0b00是新增窄profile，未推测其含义；extension/CRC/ext/tail身份仍检查，单测及真实合法checksum0x0a00反例拒绝。首轮业务篡改定位失败因record跨block头，保留失败driver/日志后只修虚拟record到physical偏移映射，无源实验重放。

## 下一验收

封存后复核发现初始manifest包含仍打开的sealer stdout，写入结束后该日志SHA变化。保留初始manifest和失败差异，只排除sealer自身运行日志、更新封存脚本及189项清单；修复后全部SHA匹配。代码、输入、事件与测试制品未变化，未重复功能轮次。

本轮0源业务/配置变更、0目标动作、无产品构建/换装。当前StreamHouse产品仍旧efd068f/737，V3获取候选未纳入产品获取账；历史事件尚未进入HTTP/target/checkpoint/ACK。[接续执行单](DISPATCH_W5_HISTORICAL_PRODUCT_20261006.md)。独立目标、活动跨窗、正式采集租约、环境/类型/压力断电、评审/现场/发布全部继续待证。原完整goal active；所有Oracle任务完成后才查Codex卡顿。
