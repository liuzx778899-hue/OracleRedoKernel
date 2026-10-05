# W5 新字典捕获控制形态与完整链验收

前置交付：DELIVERY_W5_DICTIONARY_ANCHOR_REAL_20261006。实际V2获取与DERIVED全部schema-family擦除后字典位置拒绝通过，历史P0仍未闭合。原TRUSTED_DICTIONARY_ANCHORS范围保持。

1. 只读分析冻结seq59/60的未知24.4（PDB SCN2509423），独立native字段/来源/相关事务验证。未理解范围前保留拒绝，不把未知marker直接跳过，不能靠SQL文本分类。
2. 两条5.4 flags0x12 extension：seq59/135:216 SCN2509398、185:444 SCN2509478。先raw字段与native独立比较，再判定完整控制/XID归属及是否可排除；不能通过只按目标过滤来删除相关补偿/外来事务证据，不能笼统放宽extension。
3. 如已取得可证新profile，最小修复+真实冻结反例/错误尾/错source/XID等负例；让去锚点诊断对照完成完整DML并独立核验，而V2仍拒绝。该对照是DERIVED研究证据，不授予V1历史权限；旧capture/source动作不重放。
4. 完成实际获取账V2获取/持久化/重启/篡改拒绝/绑定；完整产品候选使用同新kernel+adapter，实际HTTP目标前拒绝、ledger/CP/ACK零推进。其他已通过轮次不重跑，无外部部署或合并授权扩大。
5. 补直接之外字典依赖/新COL$完整相关链、跨窗活跃DDL/历史coverage。独立目标/C++/采集租约/矩阵/压力断电/评审/现场发布原目标不缩减。

源实验权限仅限专属oraredo-19c-lab-20261004；共享只读。生产缺权限拒绝，不自动grant或开启补充日志。禁止Luna/子代理，失败追加/执行源码时点与制品输入输出SHA/rc保全。seq59/60两笔源事务已结束，严禁重放。
