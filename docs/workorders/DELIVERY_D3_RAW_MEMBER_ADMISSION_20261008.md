# D3 原始闭合成员内核接口交付

代码7617040，完整334测试零失败错误跳过。公开CaptureJournal.readRawClosed(Path,expectedSha256)返回现有Verified；完整raw传输SHA先核、容量匹配实际字节、只解析有效闭合范围、原始header保留，与ORACAP共用块/来源/闭合校验，不引入数据库依赖。Source.journalSha256/journalBytes对raw表示原始文件身份，不表示ORACAP帧。只针对既有支持布局和有界不可变普通文件，在线持续/ASM/TDE/断电/高压不当已支持。

提交树制品SHA2690e584ae63c80a4002844e8a5d2bfb55caca9e70e79d641dc81a6dea25d093；私有坐标0.1.0-d3-raw-2690e584ae63，未覆盖旧版本。真实C++输出77/78与原可信baseline产生三个完整有效UPDATE，操作顺序、eventId、payload、完整像和schema核同；产品3309781/782同件实际原子目标/控制CP/ACK闭合，参见产品REPORT_ORACLE_D3_RAW_ADMISSION_20261008及证据摘要，428项双SHA封存，禁止追加。

首编译checked throws及安装调用失败原文保留。root STATUS保留移交快照，不在此升级现场/发布；PR1待独立评审，D2剩余及D3常驻/HTTP/恢复/压力/平台、D4D5及独立目标/部署/生产现场发布完整范围继续。
