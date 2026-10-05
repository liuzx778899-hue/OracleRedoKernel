# W5 历史 schema 与 DDL 后稀疏 DML 门禁

背景：实际获取账→新raw→完整三DML→产品target/control/ACK已通过有界独占fixture，不能推广到DDL/ABA。当前冻结物理列并用patch重放；新增nullable列之后仅更新旧列，可能仍具有旧列数的合法稀疏patch。当前metadata前后相同也不能证明中间没有DDL。一般生产门禁继续关闭。

P0判据：真实基线后发生schema改变时，旧schema输入不得输出伪完整事件；未知历史代际/覆盖不足应rc非0、stdout0、target/ledger/checkpoint/ACK零推进。既有静态schema有限输入不能被不相关系统事务误判；不得跳过目标相关未知字典操作。

实施顺序：

1. 新独立专属fixture/唯一动作包，保全初始真实获取台账及raw位置；一次ADD nullable列→仅更新旧列提交，再一次能够实证的DDL恢复形态与其后DML，保留所有失败，不重放原源步骤。先明确真实ABA能否保持同object/data identity，不能伪造结论；没有真实ABA则只记派生/待证。
2. 从完整原始redo/header/事务链关联字典改变和物理列代际。dump和SQL truth仅尺子；若格式/依赖信息不足则拒绝该批次或明确限制支持，不能以最新metadata掩盖历史不足。格式结论来自独立真实实验，禁止复制AGPL源码。
3. 先用实际失败输入定位旧schema是否被错误接受，然后最小修复原始字节代际屏障；原反例保留，完整批次materialize之前输出0。源码/制品/输入/命令rc/事件数关联SHA封存。
4. 核新门禁后产品获取账/绑定/目标之前拒绝，已接受静态轮次不重复全矩阵；仅必要受影响回归。独立评审PENDING，公开decoder/生产批准不开放。

并行性只指可独立工作的事项，不授权子代理：V731适用三库NEW/UPGRADE还需实际验证；产品仅改已注册隔离worktree，共享StreamHouse只读。旧T_BASELINE及seq52—54/先前三笔新源事务已经关闭，严禁作为本实验反复执行。仍保留跨窗/租约/C++/独立目标/Windows/ASM/高压/断电/现场/发布主线。
