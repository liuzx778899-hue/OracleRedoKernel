# W4 产品可信基线后真实活跃事务与marker2候选

源动作已一次结束，禁止重放。proper产品认证HTTP V3获取 `101320533446070272`，SCN2773486，snapshot SHA `9a99c587d5f21eaaec42eb6554b18bffe5e9eba7d05950132136a6c1556d55cc`；authority `owned-active-acquisition-20261007`，登记ACQUIRED_ONLY，原字段及完整metadata恢复读取相等。独立基线6 SYS行/230值/3索引/业务行全部零差异；不是手工baseline或晚SCN回填。

新helper `ork-product-active-20261007`/37266、control `ORKACTCTL_20261007`，原候选e0a79dd8只读挂载，目标未初始化/无事件绑定或发布。新原始日志75/76由既有有限脚本采集；关闭75时XID10.0.2959仍活跃、commit未发送，76提交后恢复原业务行。collector rc0/next77，两代95320/3观察块与归档数据块字节一致，原online header保留，capture_complete=false；不当正式C++或实时完整采集证明。

原kernel593e730b对75单独输入 `INCOMPLETE_TRANSACTION_IDENTITIES:1`，75+76 `UNSUPPORTED_KDOM2_UPDATE_LAYOUT`，均rc1/完整stdout0。保留原失败，未切换简单fixture绕过。raw/native定位同scope XA KDOM2 marker2、一字节长度、物理col1/三列，两正向/两undo值均24字节。新增该profile短值解码，仍要求单列map/fields/零delta/完整长度；marker3旧profile不放宽。

代码 `73c487f`；完整 **330/0/0/0**，新两个测试含真实原值/新值及长度/map/marker/字典毒化。kernel SHA `571cac2a785f42d1d3a63173991cea061624cfbe6bdf17dd687b6b61c5c450da`；生产只HeapRowPatch.class改变，127个class原字节不变。

| 新候选实际验证 | 结果 |
|---|---|
| 仅75完整入口 | 未闭合拒绝，rc1/stdout0，不发布pending事务 |
| 75+76完整入口 | 两完整UPDATE，同XID10.0.2959/begin2773776/commit2773780；完整三列像、隐藏NOTE、ID0保留；四native正逆变化及SCN/位置/schema零差异 |
| 比较器反例 | XID、commit SCN、隐藏NOTE、undo vector位置四种毒化均检出 |
| pending持久/独立JVM恢复 | 75 checkpoint pending1，state SHA `7d6eef2ad4439bdad1ec93a853de95b36810cb334637db81fe588022ba1b6512`，force后Runtime.halt23；新JVM保留raw75权威、只续76物理状态，完整事件stdout与75+76字节完全相同，SHA `408955c268f55a58eb8a1896b47ca0a7d9356aaa0c12637016d33cea56be84bd` |
| 重封校验和真实整入口反例 | marker4、短值长度不一致均重算raw checksum/metadata SHA/frame SHA/外部journal SHA，完整入口仍分别拒绝KDOM2布局/长度，rc1/stdout0；不是校验和拒绝冒充语义门禁 |

[344项双SHA证据](evidence/DELIVERY-product-active-marker2-20261007.json)封存产品可信获取、独立基线、一次源SQL/活跃会话、原捕获/归档/native、原拒绝、新kernel构建/完整报告/正反例/持久state与恢复输出。前353+234+113+100逐项未变，secret不哈希/纳管。

两驱动首次比较失败原文保留：Oracle registeredAt微秒保存与HTTP纳秒返回差1微秒；SCN JSON字符串与Python整数。仅修独立表示比较，其它登记字段/metadata精确相等；不重新获取产品baseline、不执行第二次源动作。最终helper停止false/143/exited，collector退出，源继续运行、NO/NO及旧两业务行保持，bindings0/events0/目标动作0。

**产品自动pending登记/跨发布恢复仍未实现或通过。** 当前PipelineFacade把未闭合窗口按decode拒绝停摆，当前输入批准绑定要求完整事件证据；现有产品Recovery只读已批准文件路径/SHA。不能拿本次独立JVM或已捕获后手写文件当自动产品能力。

下一具体开发包：服务端从可信获取登记与批准raw前缀生成/force持久pending checkpoint及可信恢复登记，保存来源/基线/捕获前缀摘要；pending状态不得变成发布checkpoint或消费ACK。重启核完整来源/state，后缀到达后恢复完整事务，再走现有原子目标/控制CPACK。未知或缺件拒绝零发布；客户端不得提交任意路径/SHA/事件作为权威。升级私有产品不可变kernel571cac2a后，用本冻结基线75/76验证首次pending→硬退出→恢复→后缀完整发布及故障；不重源、不重获取。一般pending SYS/补偿/index矩阵仍D2，正式C++D3，扩展类型/RAC/ASM/TDE等原目标保留。
