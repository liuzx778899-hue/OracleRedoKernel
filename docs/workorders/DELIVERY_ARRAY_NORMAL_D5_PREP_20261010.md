# ARRAY 正常链与 D5 环境准备 · 2026-10-10

基线后 ARRAY INSERT 正常链已得到真实原始日志、完整事件及 JDBC 目标证据；D5 仅做实验环境准备，还未得到 RAC/ASM 接入证据。LOB 历史/恢复回收、类型变体及 D5 后续工作继续，不能将本包等同于完整 D1–D5 完成。

## ARRAY 本包

- 一个原始 QMI/QMD undo 节点保留全部物理行；索引 ARRAY INSERT/PURGE 保留逐键 slot、key、rowid。图节点按原始 UBA 计一次，完整行和事件逐行生成，不把单节点当作单行。
- 复用冻结 archive90 的 20 行/7 heap 节点/1 index 节点，key/rowid 零差异。新的专属三列 fixture 在 HTTP 获取基线 SCN3219777 后只执行一次 20 行 INSERT SELECT，ID100–119，末行尾列 NULL；事件20、唯一ID20、单事务源顺序1–20，独立 SQL 完整值差异0。
- 原始107 SHA256 `a6d1284c28b536e23d40b2b65a73705f2770eba3a549d052330fda4d2041f47a`；HTTP快照 SHA256 `014a659eb92689374ad838a80d79b28d2f6868c9363407024e9805abb868fa05`；规范事件 SHA256 `7f16de637755bc6bb625fd9395270e67185bede5fd40da209bb2e819684f8b53`。
- 实际 JDBC 首次新增20，独立连接核验提交通过，同批重试新增0；applied/member/commit/最终rows为20/20/1/21，目标与源完整值双向差集0。目标步骤未重复源 INSERT。
- 开发 jar SHA256 `e2575809138ab86a4b559f52c8113cf58b251e58abd95b9c883258f38c3c88f3`；沿用适配器 `87eb1e9064dee46e580853ac889453b458a9fb59a1d79cb40742c64e554938fb`。受影响48项测试0失败/错误/跳过，package rc0；复用其中原相同commit SCN按真实提交位置排序及恢复回归。
- 前置获取缺权限 ORA-00942、索引历史cleanout/heap padding 首轮拒绝与原失败日志保留。只对本次自有表授予既有 reader SELECT/FLASHBACK，不重建seed、不重放INSERT、不改生产日志配置。SQL/native仅独立验证，未作事件输入。
- 尚未证明一般 ARRAY 回滚、其它 ARRAY opcode/未观察布局，以及本制品的新产品 HTTP/CPACK 集成；多行共用原始 UBA 的单行补偿不得取消全部行，明确拒绝 `UNSUPPORTED_ARRAY_COMPENSATION`。安全拒绝不计作功能支持。

证据位于 `lab/out/array-normal-20261010/{verify-r1,target-r1,native-r1,source/resume-r1}`；核对 `verify-r1/proof.json`、`affected-tests-r5.rc/log`、`target-r1/proof.json/actual.stdout/stop.rc`。源库已停止rc0，helper已移除，数据库/卷保留。常规混合LOB产品包已合并StreamHouse main `8d14cf5037b0564631909cc6af468311f1890db2`；本ARRAY开发jar未替换该main依赖。

## D5 环境准备

用户授权 Docker 实验与官方下载，要求E盘。Grid包已在 `E:\OracleRedoKernel-lab\d5\downloads\LINUX.X64_193000_grid_home.zip`，2889184573字节，官方SHA256 `d668002664d9399cf61eb03c0d1e3687121fc890b1ddd50b35dcbe13c5307d2e` 核等。真实RAC官方仓库最初返回authentication required，原包下载入口也要求用户Oracle登录；用户已自行完成Grid下载。

发现Desktop镜像磁盘在C盘，因此另建自有WSL distro `OracleRedoD5`，rootfs磁盘位于 `E:\OracleRedoKernel-lab\d5\wsl`。已有Desktop镜像/容器/数据库保留。独立Docker29.8.0安装在该distro内部，安装包与官方Oracle docker-images脚本均在E盘；环境启动/停止证据在同目录 `prepare-r1`、`engine-r1`。这不代表Oracle已启动或D5已通过。

独立daemon实际启动与loop块设备检查在 `engine-preflight-r3` 通过：Docker29.8.0/overlayfs、rootfs ext4位于E盘，16MiB自有loop设备完整大小核验，结束精确detach并停止自有distro rc0。r1启动失败、r2 WSL默认shell提前展开变量导致loop路径失败及收尾日志解码失败均保留；修为隐藏的前台daemon客户端和WSL直接exec，不改Desktop或全局WSL配置。这只是实验部署前置检查，不是RAC/ASM接入证明。

官方19.3 RAC构建需要Grid Home与Database Home两个安装包。Database Home `LINUX.X64_193000_db_home.zip` 尚待用户下载拷贝到同一目录，之后核对官方SHA256 `ba8329c757133da313ed3b6d7f86c5ac42cd9970a28bf2e6233f3235233aa8d8`。RAC部署须确认两实例thread和真实ASM磁盘组，再独立冻结样本验证；不能将单实例普通文件改标签当作RAC/ASM证明。TDE保持搁置。

官方来源：[19c下载与SHA256](https://www.oracle.com/database/technologies/oracle19c-linux-downloads.html)、[Oracle RAC构建脚本](https://github.com/oracle/docker-images/tree/6a8129a1540baa292da85fb0cc94f9d402ffb53b/OracleDatabase/RAC/OracleRealApplicationClusters)。外仓脚本未复制进产品或内核源码。

六状态：代码候选交付｜针对性验证通过｜ARRAY有限JDBC集成通过/新产品接线待证｜部署待证｜现场验收待证｜发布未开始。
