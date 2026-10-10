# D3 在线闭合代际正常接入 · 2026-10-10

本包达到有限正常产品链交付：自有 Oracle 19c 的三个固定 online redo 普通文件，真实100/101两次代际闭合 → 正式C++认证传输 → Java自动发现/登记 → 既有解析/发布 → JDBC目标 → checkpoint/ACK。两个窗口均PUBLISHED/rc0/1、CONSUMED，target事件/成员/提交2/2/2、ledger2、PASS gate2；20行完整值匹配，两个完整前后像和payload零差异，首窗ACK未变。producer sourceCount/publishedEvents从1到2、decodedThroughSequence从100到101、pending0；requiredRawFirstSequence仍100，未证明可删除旧raw。

只补两个真实读取缺口：`readRawObserved`验证来源及明确开放标记后等待，严格`readRawClosed`仍拒绝开放代际；重复闭合快照必须来源/闭合边界相同、有效redo数据逐字节一致，只容许本次19c头部观察到的36/172及校验字节差异，保留按传输ID排序的首份收据。没有归一化原字节，没有新增记录解析器，也未修改C++解析能力。未知头变化或有效数据变化继续拒绝。两个自动登记均由`oracle-raw-discovery`执行，未人工插入APPROVED_BOUNDED或发布/ACK。

代码：OracleRedoKernel分支`codex/d2-normal-index-restore`，核心4bbb122/01feae8，完整脚本树301f51e；产品分支`codex/oracle-dictionary-acquisition`，8850c89/99d6b1b。内核最后相关9项、产品最后发现12项rc0；原发现/登记其余相关证据复用，不重复全套测试。产品依赖固定为`oracle-redo-kernel:0.1.0-online-fff0550a36d7`，adapter4c619ca01918和正式C++f477b482复用。

同包证据在OracleRedoKernel的`lab/out/d3-online-product-20261010/`：`normal-online-proof.json` SHA256 **e642455251ed80d3ec0ae94462768b85022f91127e193b16ba41c38214ec7e05**、`runtime-r8.rc=0`、`r8-actual.stdout`、真实CP/ACK及producer文件、原失败/受控停止日志。可信HTTP基线只获取一次（SCN3081400，SHA256 **0e12fcb3c3c6684e3042a6951b423b565a9eb5df07e61331ad4a47456baa55a3**）；两条未来窗口新UPDATE各执行一次，旧源DML重放0，修正后完成阶段新源动作0。事件输入来自认证传输目录，未使用归档替代在线采集，也未使用dump/SQL truth产事件；SQL仅核初始目标和独立最终值。

必要制品/输入SHA256：

- kernel **fff0550a36d7b19c75de2072d1660b326d4bec28834ebc3aaad1fa29577d95c1**；最终application **d028ca5450bfe95bab4bcac09eafc39054b023e814f2dd32e83eeffb38ee4350**。
- online100首份 **f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820**，online101 **3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5**；每份209715712字节。
- 实际CP/ACK两份 **a19b5c6964094e51ca2a6b2422ce77cd075e79b12b7d291aa40a1b2c668ce53f**、**5669ba3adddcb101921e40722ead992d243dc3757fbb54d60811d16f5dfe6390**，分别与控制表checkpoint逐字节核等。

候选/运行边界：首窗实际使用application92a27620；修正闭合重复后最终候选d028ca54按原首窗目标、CPACK及producer验证续接101，没有重发首窗。启动脚本权限/LF及helper诊断依赖修正导致受控重启；不能把此证据写成两次切换全程同PID、长期无重启运行。闭合100重复快照只头偏移14/36/172不同，有效数据SHA同为f1a564ae；完整差异记录见`closed100-byte-comparison.json`。

实际剩余：已观察到MEMBER_COPY_DIGEST_MISMATCH和MEMBER_SPOOL_FULL，变化中的快照/队列证据保留。自动消费ACK回传清理/保留低水位、持续容量与背压、平台覆盖、长期在线仍待补；发现任务在源锁内重复读取大成员造成实际发布等待，最终自行解除，列后续读取接线优化，本包不扩性能测试。旧目标46/46/4保持，未重验旧场景。

六列处置：代码/相关验证/上述有限集成通过；专属实验换装已执行，约定生产部署待证；现场待证；发布未开始。独立评审及独立目标验收待证，作者不自验收。日志已保存、固定helper已删除、专属数据库exited，数据库及数据保留。D1–D4整体未全部完成；D2具体场景/ARRAY、D4正式获取与其它类型仍按收尾单待办，D5未启动，TDE Issue#2不作第一版门槛。
