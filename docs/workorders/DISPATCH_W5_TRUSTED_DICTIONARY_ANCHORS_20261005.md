# W5 可信SYS字典锚点获取及raw屏障

P0：marker擦除DERIVED仍rc0/2个错误事件。事后ASOF尺子已证明六条OBJ$/TAB$锚点、四条同XID COL$新行可从raw关联；V1 acquisition没有这些authority。旧capture/源动作关闭，禁止重放。

1. 实际fresh source acquisition同SCN取得SYS.OBJ$/TAB$/COL$logical/data角色、目标行位置；当前业务列须与ASOF COL$一致，前后source/container/role稳定。输出由原opaque acquisition封装，不接受外来SQL truth/自报receipt/调用者位置。SYS TAB$/COL$共用data2，不能单按data硬路由。
2. 版本化严格snapshot：source/scn、字典roles/target anchors/列完整性、重复/错角色/错SCN/缺行及旧格式权限分别验证；新authority进入控制获取账、重启校验/绑定。旧V1静态事实保留，兼容性不给一般历史权限。
3. 依据覆盖基线位置的raw写入和完整相关XID归属建立屏障，未知目标相关profile/依赖/覆盖不足拒绝；不查SQL文本或任意数字，不把所有SYS写入当DDL。新COL$slot不在旧锚点，依赖完整相关链/可信映射，不静默跳过。
4. 专属新唯一fixture/动作包：真实新获取→新raw ADD/稀疏UPDATE/恢复候选，DERIVED缺失marker/未知形态/错锚点；源获取不能晚于待重建raw。实验SELECT/FLASHBACK grant如需要仅记实验权限；生产缺权限拒绝，不自动grant或改源日志/业务配置。
5. 同候选针对性/受影响冻结回归及产品实际HTTP目标前拒绝/控制CPACK零推进，独立反例比较，保存执行源码时点/制品/输入输出SHA/rc。unknown24内核bb0225bc/260尚未进产品，不挪用旧735。

一般历史字典、跨窗/活跃DDL/间接依赖、V731三库NEW/UPGRADE、采集/租约/独立目标/现场发布原目标不缩减。禁止Luna/子代理，只修改已注册隔离产品checkout；独立评审PENDING。
