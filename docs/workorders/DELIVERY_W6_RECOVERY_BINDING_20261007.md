# W6 产品持久恢复输入候选

私有产品代码 `60f56f50d28e2f5d4cb5f2ac73b94eecf610ff87`，完整 **754/0/0/0**，产品 SHA256 `e0a79dd84abdc0009684f136fbc2b101aa82e7af8e2da300dffdb12ce3ff34af`。新增两引用/反例单位测试。服务端approved绑定可保存恢复文件路径和可信SHA；decode及发布读取核验，可信获取路径解析保留引用，旧JSON兼容，静态schema附恢复拒绝。客户端options不能登记/替换。

产品真实嵌入二进制kernel `593e730b67a2622ac9084eeae837eb64477249efe1946f4c5e4856059fe39ea9`、新不可变坐标 `0.1.0-unified-recovery-593e730b67a2`；adapter fbf1d30c原件、SPI所有class原件不变。历史恢复通过HistoricalRecovery生成原adapter输入，typed evidence与canonical envelope SHA必须与原批准绑定完全相同。未复制内核源码，也未修改共享StreamHouse。

[113文件双SHA证据](evidence/DELIVERY-product-recovery-binding-20261007.json)封存 `lab/out/product-recovery-binding-20261007`，前100+45+86+36+42+88+182逐项未变。构建来自私有干净代码提交树；实际制品与嵌入物逐项SHA核验。0新源动作、0目标动作、0换装。私有产品接口/反馈/STATUS已经推送。

这次仅代码、真实嵌入和单位验证：尚未执行新候选的实际HTTP恢复/原子target/控制checkpoint/ACK/硬退出/晚失败。原旧产品历史有限完整链路证据保留，但不转移为新恢复路径验收。存储/恢复独立评审仍PENDING，PR#13 draft依赖#12；现场/发布未过。两测试中的伪字节仅验证引用，不当内核恢复或DML证明。

下一D1：新专属输出与私有helper，复用原可信产品获取登记/冻结70-71及已有统一历史checkpoint的只读副本，服务端绑定其路径与SHA；实际HTTP任务→完整事件账/历史schema→原子目标→控制CP→ACK，并验证恢复文件毒化/丢失/组件混配拒绝、恢复/重复/硬退出/晚失败。新的73-74手工baseline仅有独立内核证据，不能冒充产品获取登记；禁止晚SCN补洞及重放旧源动作。需要新产品活跃跨窗时，proper HTTP V3获取后一次新fixture。

用户要求整体进度已整理为 [五个开发工作包与判据](DEVELOPMENT_CLOSEOUT_20261007.md)。本次只推进D1，五包不等于五个小任务，不能累计测试数当完成百分比；开发、部署、现场、发布分开。原完整goal active，Oracle全部完成后才查Codex卡顿及关闭automation。
