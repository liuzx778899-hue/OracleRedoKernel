# W5 实际获取到可信控制账候选交付

代码交付通过；针对性验证通过有限范围；集成验证通过专属 Oracle 控制库有限范围。部署未开始，现场待证，发布未开始。独立评审 PENDING，长期目标仍进行中。

- 产品代码 ded59ab4b6eeabfe2bb9c52c33b3be5ec8f141ad，完整提交树 733 项测试，0 失败/错误/跳过。新 adapter 固定坐标 `0.1.0-source-snapshot-50feffa8`，SHA256 50feffa8494a9225e48a8886752b071918e9ae50d3373c0952c6add398cb3b35；内核已通过轮次不重跑。产品 jar SHA256 2e05128a109bcbec8e7f1337682f8eb2844921b89de32d73507dd298053fc837。
- 默认关闭的新 HTTP 入口只接受 sourceId。来源连接、预期八字段、表/index 范围、目录和 authority 全部来自服务端配置；来源连接不注册全局 DataSource。旧客户端路径登记不用于此链路。
- 获取真实字节后 force 临时文件、原子移动、目录 force，再核 SHA/内核物理导入并事务写入获取账和审计。只有 ACQUIRED_ONLY 状态，三套 V731 约束禁止其他状态；没有批准输入或发布写 API。
- 专属控制用户 ORKACCTL_20261005、loopback37218、helper `ork-oracle-acquisition-20261005`。实际启动应用了24个迁移。认证 HTTP 得到 ID100549095692771328、SCN2484139、快照 SHA162a5f2cc72844f56d41295ecc5094f242c610cc63742704e36f563c91f5c3e8；重启相同制品后全部返回字段一致。
- 独立固定 SCN DUMP 与实际快照来源、对象/物理列、rowid、所有值字节零差异。实际客户端 authority 注入拒绝；正确 POST 未认证401；截断文件、孤立半文件、控制账 authority/SCN/schema 篡改均拒绝，恢复原证后成功。受控插入故障保留新快照孤儿但无新增获取登记；原获取账仍唯一。
- 独立 SQL：获取1、批准绑定0、事件账本0、六次失败审计；源补充日志仍 NO/NO、旧表仍11/blocking-closed/new-note。未执行源业务 DDL/DML、目标写入、源重启。仅专属控制 schema 新建/故障注入，故障 trigger 已删除，helper正常停止。
- 原始证据 `lab/out/oracle-acquisition-controller-20261005`，168非secret文件双读SHA封存；输入制品/命令/HTTP/SQL/失败/孤儿关联。源码执行前 pin 与执行后脚本摘要分开，不把修后源码冒充全部执行时版本。

失败追加：首轮构建 Windows 测试临时目录非canonical；修改测试配置后733通过。首次未认证探针用不存在的GET方法返回方法错误，正确POST401。独立比较脚本截取错代码区间导致语法失败，未执行SQL；改区间后真实DUMP通过。登记故障driver rc1是明确预期拒绝，原文与孤儿保全。未重复源业务动作。

边界：trusted control DB 与服务端配置是当前信任根，不声称抵御控制库/配置被管理员篡改。文件/摘要不授予发布。当前 metadata 前后核对不是历史DDL/ABA证明。SCN2484139晚于旧44—51 captures，不能用于重建这些旧DML。三库迁移只实际验证Oracle新建/重启；MySQL/SQLServer新建/升级、Oracle升级、真实存储崩溃/多writer/Windows force仍待证。没有新完整DML发布，没有现场/生产验收。

下一主线：完成新增迁移适用三库新建/升级；取得受控新基线之后的新raw范围与完整字典/leaf/事务证据，再把受控获取账关联批准输入、完整DML、中间像、目标commit、控制checkpoint/ACK。普通options、dump或SQL真值不可替代生产原始字节链。历史字典/跨窗/C++持续采集/独立目标/现场发布继续保留。
