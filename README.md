# colorlight-terminal

多模块结构：
- terminal-rpc-api（JDK11 编译，Dubbo RPC 共享接口/DTO）
- terminal-api（设备 REST API 定义）
- terminal-application（应用编排与领域服务）
- terminal-infrastructure（外设实现：Redis/Dubbo/WS 等）
- terminal-boot（启动、配置、控制器）
