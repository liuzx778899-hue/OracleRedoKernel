# W5 实际获取账关联新 raw 完整 DML 交付

代码交付/针对性验证通过；集成验证通过专属有界范围。部署未开始、现场待证、发布未开始；独立评审PENDING，长期goal active。

产品代码996cfd56c985380403e24e3a8199af1f91ef77d4，完整提交树735项0失败/错误/跳过。应用SHA256 53f53c27c9faabd58811ca4bdf4680a7d8e147a94143e851d04e2b9c48048fdc；adapter50feffa8、kernel96095420固定制品复用，内核/adapter已接受轮次不重跑。输入新增acquisitionId，解析和发布重新检查actual ledger/authority/source/SCN/hash/对象/物理列；路径来自服务端，普通options不授权。旧APPROVED_BOUNDED冻结输入保持兼容，公开生产decoder仍关闭。

## 唯一新实验

- 专属源oraredo-19c-lab-20261004，source八字段保持；新ORKLAB.T_ACQUIRED_DML_20261005及PK_ACQUIRED_DML_20261005，heap74528/index74529。旧fixture不修改、不重复旧seq44—51。
- 一次新DDL/seed0/acquired-seed/anchor、只读账号对新表SELECT/FLASHBACK实验grant。实验权限/DDL不是生产零配置证明。控制新schema ORKACDML_20261005，helper ork-oracle-acquired-dml-20261005 / loopback37219，app -Xmx1536m实际完成。
- 实际认证HTTP获取ID100583855030874112/SCN2495684/SHA28315e9941661c69e560efee2ad3b3465dc5991fdd3a454cbcebaaf168ce0c9b，获取账ACQUIRED_ONLY。固定SCN独立DUMP核来源/对象/物理列/rowid/所有初始值字节零差异，seed1行。
- 三笔源事务各一次：10.31.2143 INSERT11/acquired-before/acquired-note→TXT acquired-after→DELETE11提交；10.22.2142 INSERT12全回滚；10.29.2140 DELETE seed0全回滚。逐步独立SQL完整像保留，最终仅seed0。
- 新seq52/53/54，191449/12/16块，capture/归档原始数据块零差异。52 SHA1a009cc62fdb91ab6a43e9bf5dcc156f75b724b174d4a241516c7b989ff345af，53 SHA3071b8cf92f9e33f44b0af47282d86a8b6ffa74c5d21e463a2defd36c7039274，54 SHA89f6e23d3eb80266ba268895aa7f9e98b5e14ca536c6198cbc56d8811c6c7729。52约164MB journal有旧日志前缀，未裁剪/归一化；header SCN链2429934→2495741→2495762→2495773覆盖基线和三个事务。
- raw唯一leaf50339451；完整batch SHA06207966f6a3473bd0167b104b347cfa3494e86b521d3d13175752e9cc002b4e，schema SHA504767a445be76719387f45c1b6b1c390225d7f9f6924b7ce46d81a955aa1ab5。三事件同XID/end2495732/位置52:191439:480:6/8/9；两回滚零事件。四物理图11/4/7与完整native零差异，三完整中间像/顺序与native和源SQL步骤零差异；错像/缺事件比较反例检出。

## 实际产品发布

批准输入acquisitionId指向真实控制账、baselinePath=null，raw路径服务端绑定。独立SQL核实际绑定JSON/获取ID/authority/state与登记一致，获取账不升状态。新专属同实例目标ACDML；source为oracle-acquired-new-DML、window acquired-new-DML、consumer acquired-consumer。

实际篡改authority后task100587908754386944 FAILED，保留REFUSED批次100587909488390144/0事件；目标0业务应用/0commit/账本0、checkpoint null、window PENDING。恢复原证后相同task DONE，唯一PUBLISHED批次100587992019709952/3事件，target完整INSERT/UPDATE/DELETE中间像、3成员顺序、commit3、控制ledger3、全部checkpoint/ACK身份/源/位置/摘要零差异。最终seed相同仅是辅证，不代替三条变更证据。

DONE重复及应用重启后DONE恢复：同制品、目标/控制核验快照和ACK3536 bytes逐字节一致；真实异步完成审计分别DONE windows1/published1、两次DONE windows0/published0及初始FAILED windows1/published0，不能以HTTP200或即时DONE状态冒充异步完成。三个目标operation/checkpoint-source/ACK比较反例检出。专属helper正常停止；源NO/NO/旧11/blocking-closed/new-note保持，source动作不重放。

## 失败与边界

采集驱动process.wait(timeout55)超时后未重启采集或源DML；随后核同host/remote句柄已不存在、原collector终态记录3代际齐全，只续接只读复制/归档比较/dump。host进程rc无法追溯，不冒称0；关闭header完整性及独立比较分别证明本次有限raw输入。

首轮lab raw leaf探针在对象路由前解码非目标undo flags拒绝；lab探针改为内核已有对象路由规则，目标flags仍严格、生产内核不改，失败rc1/stdout0原文保全，仅补失败和后续未执行项。初次CLOB SQL显示换行保留；最终用显式VARCHAR2(4000)查询且核本次ACK长度3536，全JSON无截断，不声称泛化大checkpoint支持。

证据唯一lab/out/acquired-dml-capture-20261005；原始dump/制品/secret不入Git。284非secret文件双SHA，执行前source动作/制品/probe pin与执行后脚本摘要分开。不把当前metadata、独占fixture无DDL前提当历史字典/ABA证明；受控批准输入不代表一般生产批准。三库新增迁移新建/升级、真实跨窗/一般类型/并发租约、C++持续采集、独立目标、Windows/ASM/高压/断电、现场/发布仍待证。

下一步优先历史schema/DDL后稀疏DML及ABA反例，补原始字节中的字典代际门禁；并补V731适用三库新建/升级。不能靠重新读取当前metadata、普通options或独立SQL真值开放一般生产事件。
