# 常规混合大字段首包 · 2026-10-10

代码分支：codex/d2-normal-index-restore，以本反馈所在提交为准。开发候选及正常内核→投影→真实 JDBC 目标链已验证；StreamHouse HTTP/CPACK 新场景未证，未换装 main 固定依赖，未部署/现场验收/发布。D5 尚未启动。

## 已实现与证据

将固定 NUMBER/BLOB/CLOB 三列、两个 LOB 的绑定改为物理列和对象角色绑定。新增真实九列：ID NUMBER、TXT VARCHAR2、BIN RAW、DT DATE、TS TIMESTAMP(6)、B BLOB、C/J/X CLOB；J/X 保存 JSON/XML 文本。BasicFile、SecureFile 各一条可信基线后 UPDATE：B 10,000 字节，C 5,000 字符、JSON 5,012 字符、XML 5,007 字符。

冻结 raw→完整事务/字典边界→完整行→投影后，各1事件、九列及全部 LOB 与独立 SQL 对照零差异，JSON/XML 可独立解析。旧初始化及回滚事务没有发布。真实 JDBC 目标各 applied/member/commit=1/1/1，九列与源端独立查询零差异；独立连接确认提交，恢复复读新增应用=0。SQL/native dump 没有进入事件输入。

有限支持按已观测字节布局和静态 schema 保留，不是任意宽表/类型/LOB布局都已支持。JSON 原生类型、XMLTYPE 专用存储不在本包。LOB 历史 DDL、可信切点回收、ARRAY、时间类型变体及 D5 尚未在本包关闭。

## 制品与核验

候选目录：lab/out/common-fields-20261010/candidate-r15/。

| 制品 | 固定坐标/摘要 |
| --- | --- |
| 内核 | com.streamhouse.oracle:oracle-redo-kernel:0.1.0-common-3a9ccc651b60 |
| 内核 SHA256 | 3a9ccc651b60b7513693f55e3289c664175ab8f8d142a65b71e412ab05dbfe2a |
| 适配器开发 jar SHA256 | 87eb1e9064dee46e580853ac889453b458a9fb59a1d79cb40742c64e554938fb |
| 原始归档104，21,105,664字节 | 6658fce9c8f41e836e901c39954469abadf45926f1a756658f46ccd18fdd6290 |

适配器尚未发布新固定 Maven 坐标，不得当成 main 当前依赖。后续产品接线需要固定内核/适配器配对，再验证一次新能力 HTTP 获取与 CPACK；不要求对方重复旧已通过场景。大字段目标账本 BEFORE_IMAGE/AFTER_IMAGE 使用 CLOB；本次目标采用该布局。

证据统一在 lab/out/common-fields-20261010/：verify-r15/proof.json 两种完整投影 PASS；target-r1/proof.json 两种真实 JDBC/独立提交/无重复 PASS；candidate-r15 内核针对性及适配器15项 rc=0；final-old-lob-regression-r1.log 原三列8项 PASS；hbb-tail-tests-r1.log 17项 PASS；final-lifecycle-regression-r1.log 24项 PASS。不重复全量部署或故障矩阵。

## 拒绝与修复

verify-r1 至 verify-r14 原失败 stderr/rc 保留。逐项补了：九列 INSERT 双字节空值位图；无 L2 父补偿的 BasicFile 位图；SYS 字典写入按目标行/LOB对象限制；独立受控 quota 标记；SecureFile PUB 回滚/复用；双字段跨 undo 块推进；native 确认的维护提交扩展/无撤销尾结束；HBB Reserve Chunks 同记录前向/逆向关联；早于当前事务且已完整回滚的旧页像排除。原对象、来源、事务、完整值及未知格式拒绝边界仍在。native HBB 显示的异常高位不作为长度或身份依据。

专属源实验首次 BasicFile 成功，SecureFile 配额失败后回滚；只重试已确认回滚的 SecureFile 新事务一次，没有重做 BasicFile 或旧 fixture。原 quota 10MiB 满，受控增加至18MiB，未扩文件；这是专属实验配置，不算生产零侵入证明。所有失败保全。后续核验源 DML=0；固定 helper 自动移除，最后停止实验库 rc=0、状态 exited，数据库与卷保留。

## 后续接续

继续该首包产品 CPACK 接线、ARRAY/同 commit SCN、LOB 历史及恢复/回收等功能，再进入 D5 RAC/ASM 读取与 thread 协调。长期/压力/平台/现场及低概率组合故障放到模拟生产；明显漏数、重复、错误恢复仍在开发期修复。TDE 搁置。
