# W6 有界跨批身份、混合旧新事件与 Oracle 目标凭证

实验源码 557195d（同 redo 再批次）及 0baea8ea98b8ed1a0d043d53fad7a0f6841726c9（重叠窗口），生产模块/内核未改，不重建或重跑已通过5/245测试及强杀轮次。复用 target adapter 8103c262…、kernel 15b07ddf…、SPI 93e451fe…；脚本逐件对原交付清单核 SHA。[本轮双次 SHA 清单](evidence/DELIVERY-oracle-overlap-20261005.json)，输入/制品绑定承接 [原目标清单](evidence/DELIVERY-oracle-target-20261005.json)。

## 同 redo、不同 capture 摘要

仅对 sequence40 实验副本观察 sample 加1000000，按规范重封 envelope；14749原始512字节 payload 逐帧不变。derived acquisition 摘要不当新的真实观察回执。sequence41/42 原件不变。完整 kernel 输出 EVENT/ROW 与原件逐行一致，batch SHA 从 b6658e92…变为 c5df2745…。在原健康 POST 目标新批次0新增 DML，审计/成员/凭证为3/6/2；再消费新批0新增，新 ACK 稳定、原 ACK 字节未改。原三源提交 SQL 独立构造六像、核事件/成员/凭证/最终行零差异；漏成员/漏凭证/漏事件三个派生比较反例检出。POST 当前已是两批3/6/2，旧3/3/1报告是历史时点，不能直接用于当前状态断言。

## 原始关闭窗口的实际重叠

40—41 与40—42均直接用既有原始 journal，未改元数据/redo。前缀批 SHA 86cd8a04…，2事件/行完整输出与原完整批前2条一致。源表/补充日志没有动作，仅在专属同实例目标已有账号下新建 MIXD 五表。

| 消费 | 新增 DML | 目标审计/成员/凭证 |
|---|---|---|
| 原始40—41 | 2 | 2/2/1；中间行11/capture-follow-b/new-note |
| 原始40—42（两旧一新） | 1 | 3/5/2 |
| 再消费完整批 | 0 | 3/5/2 |
| 完整批之后再消费前缀 | 0 | 3/5/2 |

前缀 ACK 在完整批消费后仍逐字节一致；独立原三源提交 SQL 核六完整审计像、payload、两个批全部成员次序、凭证身份/计数、最终行零差异；两 ACK 精确 UTF-8/LF内容均核验。MIXD 当前健康。Oracle LGWR PID5601原进程保留、源业务行及NO/NO补充日志未变，helper均退出。Python预检 SQL 字面量 invalid escape SyntaxWarning 保留，实际 SQL及driver rc0；没有重复通过轮次。

这证明既有闭合窗口两旧一新的全局事件去重与每批独立提交凭证，不证明任意活跃事务切窗、跨批事务图、历史schema、消费checkpoint或日志删除安全。新目标DDL为专属实验变更，不当生产零数据库配置证明；同实例目标不当独立生产目标验收。公开decoder/完整StreamHouse pipeline/部署/现场/发布门禁仍关闭。

下一步三位置消费checkpoint：核 capture持久前沿、kernel完整事务窗口和目标提交凭证各自身份，目标commit之前禁止推进消费前沿；重叠批ACK不能只据最终行或batch数量推断可删除capture。先补元数据/成员缺失真实拒绝及恢复，再实际进程崩溃验证checkpoint原子性。任意窗口和生产可信字典仍需独立实现与证据。
