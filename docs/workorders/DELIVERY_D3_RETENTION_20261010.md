# D3 保留范围与源队列背压交付

本轮补齐已存在的实际缺口：中央端复用完整目标提交/事件账/CPACK/producer核验返回必需保留范围；认证交接凭证经完整SHA/长度及文件、父目录同步后，仅允许释放源端冗余副本。FINISHED、交接凭证均不是消费ACK，也不批准redo、事件或中央删除。实际失败partial按自身摘要进入独立诊断目录，不进入raw解析路径，原失败证据保留。

## 实际结果

- 保留接口实际返回100–101，`centralDeleteAllowed=false`；移走复制的最后ACK后业务拒绝，原件不动且复制件精确恢复。当前恢复/提交链仍引用100前缀，水位未推进，中央raw未删除。
- 220 MiB容量下实际`MEMBER_SPOOL_FULL`→继续交接→释放→自动观察下一成员。同一PID181释放诊断片段、正常100副本后自动排队101，完成一次正常容量闭环；三分钟观察脚本先超时，退出前101已完整排队。保留超时原文，只用PID127续接剩余交接，未重新抄录旧成员，不宣称整轮同PID或长期连续通过。
- 两个正常raw和一个实际失败partial的源队列副本释放，最终队列0；中央原件/诊断件完整SHA核等，原源失败片段仍在。applied/member/commit/ledger维持2/2/2/2，原ACK同字节；源DML/新获取/新事件均0。
- Java相关24＋13项、C++两个相关测试程序通过；只构建改变的产品/采集器，内核/适配器可信制品复用。首次Java访问器/C++测试变量编译失败、日志编码异常及观察超时均保留，修复只接续对应步骤，不重跑已通过整套验证。

证据入口为OracleRedoKernel的`lab/out/d3-retention-20261010/normal-proof.json`、`candidate.json`、两段collector日志、`floor.stdout`、`missing-ack.stdout`、`finish-actual.stdout`及对应rc；原100/101两事件发布证据复用。固定helper保存日志后移除，专属库停止；最终清理rc与只读容器盘点随上述证据保存。

## 制品与处置

产品代码`7daf091`及访问器修复`031c1cf33497c6cdc37d14561601be7cfe8f155d`；采集器/测试提交`02da3b9bbabc78e822da6bf63b179f6b0a97e6df`。Java包来自031c1cf，之后02da仅改C++测试变量。根仓执行脚本提交`6c8d13e`及本反馈提交；分支分别为`codex/oracle-dictionary-acquisition`与`codex/d2-normal-index-restore`。

| 制品 | SHA256 |
|---|---|
| application.jar | `f5cda704788f12fe7d5778e2b3a08457646ccbff5477b486c087a164da787865` |
| sh_agent | `033bf2f5d6e8a13ecc996af9fe68f67f65fab74bc4761c09550a938fb87abf78` |
| kernel（未变） | `fff0550a36d7b19c75de2072d1660b326d4bec28834ebc3aaad1fa29577d95c1` |
| adapter（未变） | `4c619ca01918157e4172c767bdaa83b74b43168ac16527943425b358a635d0f2` |

代码/针对性验证/上述有限集成通过；约定生产部署、现场验收、发布待证，独立评审待证。正向文件/目录同步在Linux容器实证，Windows部署未证。长期平台运行、中央前缀有界回收和ARRAY等未实现范围仍保留，D1–D4整体不标完成。已授权的已有能力收尾停止于本包，D5未启动，TDE Issue #2搁置。
