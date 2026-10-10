# D5 安装介质与构建停止点 · 2026-10-11

两个安装包已正确放到 `E:\OracleRedoKernel-lab\d5\downloads`，大小及官方 SHA256 均核等：

|介质|字节数|SHA256|
|---|---:|---|
|LINUX.X64_193000_grid_home.zip|2889184573|d668002664d9399cf61eb03c0d1e3687121fc890b1ddd50b35dcbe13c5307d2e|
|LINUX.X64_193000_db_home.zip|3059705302|ba8329c757133da313ed3b6d7f86c5ac42cd9970a28bf2e6233f3235233aa8d8|

软件构建使用 E 盘专属 `OracleRedoD5` WSL/Docker，原 Docker Desktop 数据盘未迁移。官方 docker-images 源码固定 `6a8129a1540baa292da85fb0cc94f9d402ffb53b`，UPL 资料留在 E 盘，不复制到内核源码。归档首次挂起主动停止 rc130；本地规范字节逐项核对 Git blob 后继续。OL8 构建因 Grid supportedOSCheck 的 INS-08101/NPE 失败 rc127，停止 rc0；随后选择这份19.3 Dockerfile默认的 `oraclelinux:7-slim`，保留失败、不改安装器检查。

`rac-build-r3/build.log` 已有 Grid/DB 软件安装成功输出及 RAC relink/root 脚本输出，同时包含 INS-13013 前置条件警告。尚不能据此确认二进制、镜像或集群可用。用户随后要求关闭其它运行环境释放内存，镜像软件层提交期间对精确匹配的自有 daemon 发 TERM；构建 rc1、WSL停止 rc0，记录于 `rac-build-r3/user-stop.txt`。完成的缓存及安装包保留，未创建/启动 RAC 数据库或 ASM，D5没有事件、真实接入或验收证据。

用户逐项授权后八个 Desktop 业务容器全部停止，保留容器/数据卷；Desktop也已停止。缓存回收及停止专属环境后实时 Win32 内存占用约68%、可用19.8GiB。Windows内核Token及另一SQL验证JVM的高占用只记录在忽略的 `lab/out/memory-diagnostic-20261011/findings.md`，没有改验证程序或卸载驱动。

接续入口为 `py -3.12 lab/d5_build_rac_e.py --resume-after-memory-release`，复用已完成缓存和同一介质，输出新目录 `rac-build-r4`。用户当前要求停运行环境，本轮不执行该入口。下一轮先完成镜像和实际二进制检查、核对前置警告及WSL/systemd运行条件，具备条件后才配置两节点共享ASM并冻结真实thread样本；不能把单实例thread1或普通文件读取当D5证明。TDE仍搁置；普通LOB历史/恢复回收、类型变体及ARRAY待项保持原状态。

官方来源：[安装包与摘要](https://www.oracle.com/database/technologies/oracle19c-linux-downloads.html)、[固定版本Dockerfile](https://github.com/oracle/docker-images/blob/6a8129a1540baa292da85fb0cc94f9d402ffb53b/OracleDatabase/RAC/OracleRealApplicationClusters/dockerfiles/19.3.0/Dockerfile)。大包、镜像、完整外仓及私有诊断不入Git。原StreamHouse main混合LOB交付不受本环境停止点影响，重新编译/部署仍按原清单执行。
