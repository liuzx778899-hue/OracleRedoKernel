# 测试环境最后收尾交接

用户要求先检查进度，避免过度验证与死胡同，并评估换新窗口。当前原目标保持 active；若新窗口接手，确保仅一个写入者。新窗口无需重做已通过功能、源 DML、正常窗口或全套构建。

## 当前事实

- 根仓 `codex/d2-normal-index-restore`，内核 e3ac633，固定坐标 `0.1.0-readcut-53fa9a98275d`；SHA256 `53fa9a98275d22f8cadae86a7ac47d59c88450e158c46488c3eb823d2d2d01a6`。
- 产品仓 `lab/out/streamhouse-oracle-gate-worktree`，分支 `codex/oracle-dictionary-acquisition`；修复代码 `7eaca10f2899dadb59fda3bf635edc5f5c63e3da` 已推送。正式候选 `lab/out/read-cut-20261010/application-r4.jar`，SHA256 `6b26d0852e15b6b6227f43b734f3c437911f971d2f38e5a507648aa648e5f32f`，候选清单 candidate-r4.json、sealed-reclaim-package.rc0。该修复候选尚未部署。
- 原中央批准100副本已回收200MiB；原证据目录 raw/ACK 不变。当前可信切点保留101；原恢复新进程/重复回收0已通过，reclaim-proof.json / resume-r2.rc0。
- 两个未来正常窗口3和4实际通过（运行产品37ee409/app-r3）：各1事件，最终目标 applied/member/commit/ledger=4/4/4/4，20行与完整前后像一致，实际DONE任务CP/ACK逐字节一致，读至103/pending0。future-window-1-proof.json、future-window-2-proof.json、windows-r10-verify.rc0。ID1、ID2新UPDATE各执行一次，绝不能重复源DML或归档。
- 独立只读评审已完成；指出链外已消费历史可能没有SEALED锚。7eaca10已补删除前逐项sealed链校验，所有受影响历史绑定必须属于该链，否则拒绝回收。修复独立复核通过，16相关测试rc0（sealed-reclaim-review-tests.log）。当前正常单链未发现已证实漏数/错误提交，评审不替代最终验收。
- 自有库 `oraredo-19c-lab-20261004` 已停止，固定 helper `ork-product-runtime` 已删除；windows-r10清理日志/rc。无活跃构建或观察程序。共享容器不操作。

## 剩余任务：严格三步

1. 仅启必要自有库/helper，使用已构建app-r4和既有窗口/CPACK。在同一source `oracle-online-normal` 上登记下一切点，期望保留103；回收清单仅批准101和102，不碰未批准OPEN观察文件。实际目标仍4/4/4/4。若拒绝，保存原因，只修真实缺口，不重复源UPDATE。
2. 一次正常新进程恢复，核验保留水位103、目标/CPACK不漏不重。一次切点snapshot毒化拒绝，确认删除0并恢复原字节；`lab/read_cut_poison_guard.py`尚未执行，旧r2制品/前置文件硬编码必须先改成r4与当前证明。不要复跑原正常两窗。
3. 同一合并反馈与两仓STATUS收尾，复用D1/D2/D4与D3背压既有证据；区分代码、针对性、集成、部署、测试环境验收、发布。完成所需证据后才能完成原goal。

## 不再扩展

大文件反复读取与观察脚本JSON筛选/Path拼接/超时返工耗时偏多，已保留失败。不要追加性能调优、全故障矩阵、长期连续平台、ARRAY或生产现场。新SYS记录补解析已依据实际样本限定完成，14+30相关回归不重复。D5未启动；TDE为Issue #2搁置，RAC/ASM不得预先宣称支持。

原始证据在 `lab/out/read-cut-20261010`，大型文件/秘密配置不入Git。运行入口可复用 `lab/read_cut_runtime.py` 的函数，但注意参数label和__file__必须指向lab内脚本；runtime.secret.env为忽略文件，不输出其内容。旧 read_cut_windows.py 的JSON图片筛选不适用当前规范化行像，不直接重跑。

Java17 `D:/work/Java/jdk-17.0.7`，Maven离线 `D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd`，Python `C:/Users/Administrator/AppData/Local/Programs/Python/Python312/python.exe -X utf8`。保留根仓两份继承未跟踪HANDOFF，不泛化stage/清理。
