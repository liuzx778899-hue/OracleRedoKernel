# W3 尾列 NULL capture：真实基线后无 tail commit 与省略列

本轮是失败正样本研究，完整 NULL DML 未通过。生产仍 bed7fdb/kernel58da93ea…，新实验 driver9f7504e；实验及研究源码见封存清单。原非空 INSERT/DELETE、245测试/新增类7、消费与强杀轮次不重跑。[双次 SHA 清单](evidence/RESEARCH-null-dml-capture-20261005.json)，原始目录 `lab/out/null-dml-capture-20261005`。

仅专属容器创建 `ORKLAB.T_DML_NULL_20261005`，NUMBER ID、VARCHAR2 TXT、invisible VARCHAR2 NOTE，seed0/null-seed/anchor；实际获取 snapshot SCN2423979、SHA eb97c179652ecbd0ca9a276f4559a51e1f1fd51251f3683bea0bea19cd6be487，heap73254/index73255。两业务动作仅一次：INSERT11/NULL/NULL（XID10.13.1792）、DELETE该行（XID10.0.1793），各提交后保存 SQL真值并各归档一次。源 supplemental NO/NO，无配置开启；实验 DDL/DML 不能当生产零侵入证明。

collector rc0，seq46共1939块、seq47共7块，next48；journal与原始归档数据块零差异。SHA分别5668457bf6bb6acf141d1e0100e829c178e40cf1b3e2351eab02e06a18e1b0d6、2a01486526536cb15696030bb9d54ebee808ddf6a584393b965eb718ca421f57。原始 index配对唯一 leaf50333107（0x030005b3）。完整 batch rc1/stdout0/事件0，`TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE`。

独立 native 与 raw 三结束记录对照零差异：seed46:1916:136/end2423975/flags2/tail存在，INSERT46:1934:16/end2424007/flags2/tail存在，DELETE47:2:148/end2424017/flags0/两字段/无tail。DELETE 明确在基线之后，不能再靠基线过滤解释。flags0 曾在非空 seed 观察到，不能推导 NULL 导致 flags0。

raw physical fragment研究仅配对目标对象：INSERT 与 DELETE inverse 都为完整 F/L rowpiece、storedColumns=1，ID raw c10c；TXT/NOTE 保留 ABSENT。native两处 cc1/仅col0 c10c，与 SQL ID11/NULL/NULL 对应。这是物理省略与 SQL NULL 的独立观察，尚未将省略转换为完整行，也未验收图/事件。首次研究探针未先筛目标对象，遇到外来 INSERT布局而拒绝 `UNSUPPORTED_INSERT_ROWPIECE`，stdout0；失败源码保留，改为先按raw目标undo归属筛record后两pair rc0，生产未改。

最终新表仅seed，旧fixture11/blocking-closed/new-note保留。源业务操作不重放，collector终止，后续研究只读。driver执行前封存自身实际字节及kernel SHA；raw/dump不入Git。

下一步两个门禁顺序执行：

1. flags0普通commit缺tail闭合：完整原始窗口中核生命周期、全部undo分配/前驱、heap/index配对和全部补偿归属，用native完整节点集合独立对照。不能 `close(lastSeenUba)` 伪装来自native tail，也不能只看 flags0 判图完整。拟新增明确无引用闭合语义，须区分分配历史尾与native尾证据；缺原始/前驱/控制/end、重复identity、未知布局、晚失败必须拒绝。先用冻结真实DELETE和旧seed验证，不重新执行源操作。
2. 可信固定schema完整F/L INSERT（及DELETE inverse）的省略尾列：仅在获取SHA/源/物理列完整且nullable得到验证时解释，物理Patch继续保留ABSENT。中间缺列、非nullable省略、非完整rowpiece、错误before/extent及不可信调用者Baseline不得靠转换通过。两完整NULL事件与native/SQL及最终seed必须零差异；再做混合/rollback矩阵和target checkpoint链路。

两项都未实现/验收；非空闭合 capture 已通过不等于NULL或通用DML支持。public decoder/完整产品/部署/现场/发布继续未通过。
