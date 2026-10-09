# D4 LOB 最小产品接入收尾

用户已选择先收尾 D1–D4 已实现能力，避免过度验证。D5 未启动；TDE 仍暂缓 Issue #2，不作第一版前置项。

代码：kernel/adapter `b2935d0`，实际 property128 修正 `b366345`、SecureFile 锁对修正 `4b44c94`；产品接线 `c0bf90b`、最终实件 pin `6ac52b1202a3d8ec124748d33773e8c9be215348`。分支分别 `codex/d2-normal-index-restore`、`codex/oracle-dictionary-acquisition`。新增真实同 SCN BLOB/CLOB 完整值及存储对象获取；既有 raw LOB 解析事件按可信新基线过滤、核对完整初始行；投影保留 BLOB/CLOB 类型、EMPTY 与 NULL，JDBC 以完整流绑定并核长度/内容前像。产品登记并核对 LOB 元数据。旧 97 只可作物理上下文，不能回填旧事件。

必要回归：kernel 原受影响基线/字典 28、新可信 LOB 分支 2，adapter 16，产品获取服务 12 与必要打包均 rc0。property128 修正后相关 14＋获取6；冻结99实际出现零 flags 的 SecureFile heap lock，只补 LOB 正向/undo 的1→0锁对，scalar 路径及未知 flags 继续拒绝，最终相关17 rc0、同一冻结输入恢复1事件。没有重复完整 LOB 56、原源 DML、旧产品链或故障矩阵。

最终实件：kernel SHA256 `ca21c74e65421d4283a6a678f53330445320473ec20de0165d3bd922cd3ec3b2`；adapter `4c619ca01918157e4172c767bdaa83b74b43168ac16527943425b358a635d0f2`；application `a698bab15a2721f1d0b2266483fbe0d446aa7da78c149ac6af47249570a7a459`，真实嵌入 SHA 核等。BasicFile 实际使用前一 application `884153f891520d0834f2659cc016fea54b13dc71d911e68ccd5a34479c99d831`；最终候选对其同一冻结输入全部投影/凭证核等，未重复目标提交。两个实际运行件分开登记，不冒充同一件全覆盖。

正常集成通过：认证 HTTP 获取 BasicFile `102097881586192384`/SCN3049595、SecureFile `102100618185007104`/SCN3058027；各一次未来窗口新 UPDATE，各1完整事件、目标 applied/member/commit 1/1/1、源目标 BLOB/CLOB 完整长度/内容零差异、窗口 CONSUMED、任务 DONE、真实控制 checkpoint/文件 ACK 逐字节相等。两次新获取、两次新源变化；旧16事件发布0。BasicFile CP/ACK SHA `a71846784c02117dfe521b123b58c90164eca2d10af73b12268cfe122e67ffd6`，SecureFile `96e774ee7c5f68881754228f3793f14d30b13813df5fc63c9451daac7f78ab97`。汇总 `lab/out/d4-lob-product-20261010/normal-product-proof.json` SHA `669d51868d6a7de344624881482256923a720d95117a2232da4755da8151ac23`，最终 runtime-r21 rc0。

失败原文同目录追加保存：编译/L1与L2误用、首次打包目录错误、HTTP获取409/ORA-00942、脚本登记时间精度/静态可空字段/缩进错误、窗口漏列98的 REFUSED、SecureFile 锁正逆布局拒绝。窗口拒绝时目标事件0/CP0，修正后沿同任务续接；源变化从不重放。只在专属实验实例给原 reader 补 DBA_LOBS/两 fixture 的 SELECT、FLASHBACK；目标/control段初始化在可信静态基线前完成。NO/NO 日志配置未改，不当生产无配置证明。固定 helper 已保存日志并删除，专属数据库停止、卷保留。

处置：代码/上述必要回归/有限正常产品集成通过；实验换装通过，独立目标及评审/约定生产部署/现场/发布未验收。有限静态 schema、三列 NUMBER/BLOB/CLOB、连续冻结 raw 及可证明原始物理上下文；不声称一般历史 LOB DDL、持续 producer 或 RAC/ASM 支持。D2具体初始水位/同key产品实证、ARRAY、连续在线等仍按收尾单保留，D1–D4整体不标全部完成。用户指定续接目标已建立；本最小收尾包交付后停止，不自动启动 D5。
