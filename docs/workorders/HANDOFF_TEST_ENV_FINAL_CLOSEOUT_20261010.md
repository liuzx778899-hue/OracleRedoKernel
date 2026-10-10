# 测试环境最后收尾交接

2026-10-10 集中裁定：D1–D4已实现能力本轮有限测试环境已验收。原P2修复独立复核及最终终态只读评审PASS；app-r4/7eaca10回收批准101/102共419431424字节、保留103、新进程恢复/毒化409零删除及精确恢复通过，目标4/4/4/4、20行匹配、ACK不变。已通过证据复用，本窗口无实验/构建/源DML；生产现场/发布与完整一般能力仍待，未批准OPEN约7.13GB不算总存储有界。D5不启/TDE Issue#2搁置。 [最终合并反馈](REVIEW_TEST_ENV_FINAL_CLOSEOUT_20261010.md)。

以下保留历史时点；本派单已收尾，无待执行实验，最新裁定以上述反馈为准。

## 最新终态（优先于下方历史步骤）

2026-10-10 最终修复候选7eaca10/app6b26d085实库闭环通过：切点102319102395412480/SCN3160456保留103，raw派生snapshot SHA91fb4cdabdee1b03f262c3f781131701a662505fdf0d97dfde773c4f076cb163；只回收批准101/102两副本419431424字节。独立新进程水位103/目标4/4/4/4/20行一致；切点毒化业务409拒绝、零删除、精确恢复原snapshot，ACK不变/新增DML与事件0。final-closeout.rc0，helper删除/库停止rc0。36→34文件、约7.55→7.13GB：未批准OPEN观察文件仍占用，不能宣称全部中央存储有界。已完成代码独立评审及既有D1/D2/D4/背压/两未来窗原CPHEX与实际ACK集中只读核对；最终本次终态独立核对与统一验收结论待收尾，不新增实验，D5不启。

下一窗口只需只读核对 final-closeout-proof.json 与 final-cut-r1 的原命令日志、汇总集中验收并同步两仓台账。下方三步已执行，不重跑。独立评审代理此前成功核对既有各组CPHEX/实际ACK后额度中断；不要把未完成的最终终态独立核对写成通过。当前无运行进程，库exited/helper不存在。


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
