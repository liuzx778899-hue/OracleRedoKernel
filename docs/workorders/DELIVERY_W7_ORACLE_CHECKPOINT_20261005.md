# W7 固定范围消费 checkpoint 实现与既有目标提交恢复

运行时ec3b4306ce088b93fedc5794b4ffb57f225a2306，lab deabf27690c929463b1735043313a6a824ce491d。新adapter6f06247e400a0a802cb395a224f983fcf4c600a8a7991293cc804e374edbab86，15模块测试零失败/错误/跳过（原11+新增4文件独占/释放/错误输入前拒绝），kernel ea9f58c6…/SPI93e451fe…固定、245内核旧轮次不重跑。源parser/target writer源码不改。[双次SHA清单](evidence/DELIVERY-oracle-checkpoint-20261005.json)。

新增consumer全过程独占、可信输入registry、实际target receipt强制验证、固定解析scope/输入摘要/关闭位置/目标批绑定、原子checkpoint及每批ACK。恢复即使local状态落后或丢失，也仅从全部注册原始窗口的实际target commit及完整审计/成员恢复；未注册历史输入拒绝。全部已发布状态核验后才允许首次当前批DML。接口与边界见[协议](../interfaces/oracle-consumer-checkpoint-v1.md)。

真实Linux JVM、Oracle厂商JDBC及专属既有健康MIXD target（3审计/5成员/2commit）：初次从两批目标提交凭证恢复到seq42/9块/endExclusive2366759，0新增DML；随后current=旧前缀仍42，checkpoint/两ACK字节不变。checkpoint所有batch身份/位置/输入摘要与raw完整factory和已验收两批对齐，尾checksum核验。

派生持久状态实验：完整checkpoint.tmp经实际target核验恢复并改名retained保留原字节；合法旧前缀checkpoint重新核所有注册target commit后追回42。半写temp、坏checkpoint位置/摘要、错误ACK eventCount、未知ACK输入四项均失败/stdout0，所有原状态文件SHA及文件集合保留。以上是受控文件恢复，不是实际SIGKILL或断电。所有操作后独立SQL快照完整行集合与原健康目标一致，0目标新增DML；LGWR5601保留、helper退出，0源数据库动作。

未验收：新target首次DML→checkpoint、跨阶段独立ACK恢复反例、四处真实消费者SIGKILL、跨进程独占、缺历史raw真实拒绝、Windows/其它FS、范围扩展后原始日志保留/删除。尚不替代任意活跃窗口/历史字典及全StreamHouse产品接入。下一步专属新target namespace执行prefix2+full1，再在提交前、提交后checkpoint前、temp force后replace前、checkpoint目录force后ACK前真实强杀并独立对账。lab-only故障控制与生产制品分开；不重复原target三强杀轮次，不授权删除任何capture。
