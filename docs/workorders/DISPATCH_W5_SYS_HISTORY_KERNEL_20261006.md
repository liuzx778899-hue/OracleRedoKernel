# W5 归档自主完整SYS历史内核

前置：b65ade5/kernelf78ad0f9/292，十一行pair/十index/一个控制共22original及764完整值诊断零差异；七合法checksum raw语义拒绝；旧220/219冻结不变。V3实际获取候选6b68786/adaptere095822b/22、新SCN2533047晚于旧seq66/67，不能回填旧历史权限。seq66/67源事务结束不可重放。

1. 实现内核自主归档/capture入口，输入只包含源绑定的V3完整baseline+外部trusted receipt及raw。由原始字节发现SYS相关完整XID、构建完整original/control/end/tail覆盖，不能接受调用者裁剪的节点或SQL/dump事件。全部已发现original需typed处置；未知间接依赖、窗口缺begin/end/control/补偿/跨窗状态不足拒绝，不能跳过或当空事务。
2. 按真实commit SCN应用完整OBJ$/TAB$/COL$行像和三索引生命周期；V3baseline推导三索引初态，新增位置须三索引key/rowid/parent一致，cluster key index不当OBJ#。严格验证inverse值、role/generation、current UBA/fullXID、原始位置顺序及索引增删之前存在性。LOCK保留完整节点而不造业务变化。中间暂名/col#0/property32800状态可内部保留，但不签发未经稳定schema核验的业务事件。
3. 生产IRP nullable tail只有实际观察到的32/36 profile、明确完整layout四尾nullable、原始长度/bitmap和完整逆向证据满足时才能完成NULL；不把任意ABSENT补NULL。SYS.TYPE#/NULL$/PROPERTY、物理segcol/internal、可见COLS和INTCOLS/KERNELCOLS需一致，稳定列史才供业务完整行重放。未知LONG/default/timestamp非NULL或其它类型继续拒绝，保持原全目标未完成。
4. 复用冻结旧研究仅检验字节/算法，不作为新authority；合法checksum缺依赖/index/row/before/parent/generation/XID/commitSCN/ABA反例须结构通过语义拒绝。适当单位检查后，明确生产源码提交树和SHA、完整build；旧静态V2保持，旧历史V2仍拒绝。
5. 先新的专属fixture和新V3真实固定SCN获取，随后ADD/DROP及稀疏DML各一次，新raw闭合后不重放；完整历史事件包括EXTRA=NULL，与真实native/同SCN ASOF独立一致。之后升级adapter及P产品获取账/API/依赖，完整P build、实际HTTP→目标commit→control CP/ACK→重复重启。结构/诊断/安全拒绝不当历史DML或产品通过。

继续跨窗口活跃事务、正式C++采集/租约、独立目标实例、扩展类型/环境/压力断电及独立评审现场发布。共享checkout只读，禁止Luna/子代理/AGPL复制；秘密/大件不入Git，失败追加。原goal/automation保留，Codex全Oracle完成后才查。
