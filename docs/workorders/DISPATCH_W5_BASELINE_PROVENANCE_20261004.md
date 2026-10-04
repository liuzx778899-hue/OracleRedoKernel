# W5 基线来源与未改列认证门禁

状态：待实现。直接失败依据是[完整行试验交付](DELIVERY_W5_ROW_IMAGES_20261004.md)中的`unverified-untouched-key`，不是推测。

## 当前缺口

`ArchivedHeapRows`能从真实固定SCN基线顺序恢复三次完整行，变化列逆向值均校验，但`Baseline`仍是调用者声明。输入基线ID由10伪造为11，三次UPDATE从未记录ID逆向值，局部重建仍rc0；独立原生/SQL比较出现3处差异。禁止将调用者填的来源字段、正确SCN、文件自报SHA或两份当前字典相等当来源/历史schema认证。

## 执行路径

1. 实现独立只读基线获取/冻结环节，原始获取凭据与行字节一并保存：实际源连接/实验绑定，DBID/activation/resetlogs/thread、container UID/ID、同次固定snapshot SCN、logical object/data-object及heap/index角色、SEGMENT/INTERNAL_COLUMN_ID和完整行定位。行使用同一个明确AS OF SCN；禁止先读当前行再标旧SCN。
2. 将获取者冻结的完整结果和SHA receipt绑定，导入器核对冻结原件、参数来源与完整schema/列数/行唯一性后产生baseline，重建驱动不再手填示例行。受信任receipt来自实际获取操作；不能让被篡改文件通过重算自己的SHA冒充原获取者。明确可信边界，来源无法核实时保持实验状态/零发布。
3. 保留未改列反例，用新接口分别篡改ID、NOTE/列map、SCN、source/PDB/世代；原receipt下全部拒绝、stdout0。仅正常receipt+真实冻结快照支持，比较器拒绝不代替入口拒绝。
4. 旧seq25表已TRUNCATE，旧ASOF已实际ORA-01466，不重跑旧DML，不拿新世代补旧基线。已有冻结证据只能做离线导入/重建；需要实时获取正例时使用专属容器新表/新输出目录/一次性fixture，完整记录控制命令与失败。
5. 补初始事务跨snapshot边界的真实/合成证据：begin<=snapshot<end、缺begin/end、快照后提交、同record提交在undo之前都须分别验证。固定schema样本之外，DDL/ABA与logical/data-object转换继续独立拒绝，不把宣称区间当历史字典。

## 验收和后续

- 用同件完整树jar复用三次真实before/after及SQL两快照，至少核漏变化、错未改键、伪NULL、错物理列map、错世代；错误必须先于输出。
- 真实ASOF基线获取只读，不启用supplemental、不要求业务DDL或配置变化。专属新fixture DDL仅用于验证，不计生产侵入需求。
- 不将只支持一个固定行/固定schema的证明定义为项目终点。随后完成typed世代映射、INSERT/DELETE/rollback与并发完整行覆盖、持续capture/ACK、StreamHouse接入及独立现场/发布验收。
- 交付代码、失败原文、输入/receipt/jar/输出SHA与命令rc、中文报告和STATUS；基线来源/历史字典门禁未通过前public events继续拒绝。
