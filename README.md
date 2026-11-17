
# 灭烬末影龙 · Ashen Ender Dragon

Minecraft 1.21.1 / NeoForge 末影龙 Boss 战改造 Mod，ID：`ashenenderdragon`，当前版本：1.0.0。

## 功能

- 多阶段 Boss 战、飞行/地面模式及转阶段蓄力大招。
- 低空吐息、龙陨星、俯冲、抓取、召唤爪牙；地面扫尾、下压、小咬、扇形吐息、龙车和风压起飞。
- 末影水晶机制、光柱、粒子与 BGM，自定义动画编辑/预览工具。
- 灵魂抽取效果、老金苹果和长枪没有枪物品。

项目围绕原版末地战斗流程扩展；多人同步、复活和其他 Boss Mod 的兼容性仍需实机测试。大招会改变地形，请使用备份后的测试世界。

## 环境与安装

- Minecraft **1.21.1**，NeoForge **21.1.248**（当前构建目标），64 位 Java **21**。
- 不支持 Fabric，其他 Minecraft/加载器版本未经验证。
- 将构建出的 `ashenenderdragon-1.0.0.jar` 放入游戏实例 `mods/`。
- 联机时主机/服务端及所有客户端安装同一份 JAR；开发版本可能同名，建议核对 SHA-256。
- 当前没有提供公开下载地址；分发前必须完成下方授权确认。

## 构建与开发

公开发布使用 `gradlew.bat build releaseJar`（Linux/macOS：`./gradlew build releaseJar`），分发 `build/libs/ashenenderdragon-1.0.0-release.jar`。该包排除 BGM 音频及其 sounds.json 定义；保留转阶段视觉效果，并且不压制原版音乐。

本机普通 `build` 仍生成带私人 BGM 的 `ashenenderdragon-1.0.0.jar`，只供个人使用。私人音乐目录及 sounds.json 已加入 .gitignore；公开仓库克隆不会含这些文件，也可正常构建无音乐版本。不要强制提交被忽略的音乐文件。

安装 JDK 21，设置 `JAVA_HOME` 并确认 `java -version`。自带 Gradle Wrapper 9.2.1，无需全局 Gradle；首次构建需要网络下载依赖。

Windows PowerShell（仓库根目录）：

```powershell
.\gradlew.bat build
```

Linux/macOS：

```sh
chmod +x gradlew
./gradlew build
```

安装包输出至 `build/libs/ashenenderdragon-1.0.0.jar`。开发启动使用 `gradlew.bat runClient` 或 `gradlew.bat runServer`；服务端首次启动需自行阅读并接受 Minecraft EULA。运行数据保存在 `run/`。

## 目录

- `src/main/java/`：战斗控制器、事件、物品、动画、Mixin。
- `src/main/resources/`：配方、语言、动画默认值、粒子和音乐。
- `src/main/templates/`：生成 Mod 元数据的模板。
- `gradle/`、`gradlew`、`gradlew.bat`：完整构建 Wrapper。
- `.github/workflows/build.yml`：JDK 21 Linux 构建检查。
- `public/`：本地参考资料，不参与构建、不提交。

## 授权状态

许可证尚待作者选择，当前元数据保留 `All Rights Reserved`，不能将整个项目宣称为已采用开源许可证。模板 MIT 仅覆盖 `TEMPLATE_LICENSE.txt` 指定的 NeoForged MDK 文件。

音乐和参考代码来源尚待核实，详见 [发布检查与许可证候选](RELEASE_READINESS.md)。Minecraft 及其原版资源属于各自权利人；本项目不是 Mojang/Microsoft 官方产品。

## 开发参考

- [NeoForge 文档](https://docs.neoforged.net/)
- [Mojang 映射许可参考](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)：映射具有独立授权，不由本项目许可证覆盖。
