# 发布检查记录

日期：2026-11-17。范围仅当前项目；没有上传 GitHub、创建提交或确定许可证。

## 敏感信息检查

对源码、构建配置、工作流和参考代码进行凭据关键字、私钥标识及本机路径扫描，未发现明确硬编码凭据。扫描不能保证二进制及压缩文件内没有敏感数据。

- `public/错误报告-2026-8-24_20.17.13.zip` 是启动器诊断资料，可能含身份、路径和登录参数；整个 public 已忽略，勿上传。
- `run/` 中的世界、玩家、配置和日志，以及 `.gradle/`、`build/`、IDE 配置均已忽略。
- 未发现项目 `.git`，没有可审核的提交历史。`.gitignore` 不会清除已经跟踪的文件。首次提交前检查 `git status --short` 和 `git diff --cached`，不要强制添加本地资料。

## 授权清单

| 内容 | 证据与状态 | 发布前处理 |
| --- | --- | --- |
| NeoForged MDK 模板 | TEMPLATE_LICENSE.txt 声明模板为 MIT | 保留通知，不视为整个 Mod 授权 |
| phase_two_transition.ogg | 与 public/150s.ogg SHA-256 相同，未见音乐作者/授权 | 确认音乐与录音再分发授权或替换 |
| phase_three_transition.ogg | 与 public/120s.ogg SHA-256 相同，未见授权 | 同上 |
| public/clip_01_0000-0910.ogg | 本地参考，不参与构建 | 保持不提交 |
| public 中三份 Java 参考文件 | 包名 org.land.superenderman，未找到许可声明 | 核实来源及许可；正式源码的携带/投掷借鉴逻辑也需审查，不能只忽略参考文件 |
| 物品/粒子 JSON | 引用 minecraft 原版资源；未发现独立 PNG 副本 | 不将原版资源纳入自己的代码许可证 |
| Gradle Wrapper、NeoForge、Mojang 映射 | 第三方构建基础设施和依赖 | 保留原通知，依赖通过构建下载，不提交缓存/反编译源码；遵守独立许可 |
| 动画默认 JSON、其余代码/文本 | 本地材料无法证明全部权属 | 作者确认后纳入选定许可 |

**发布分离：私人音乐文件及 sounds.json 已加入 .gitignore，本地文件保留。releaseJar 任务明确排除音乐和其声音定义，公开分发仅使用带 -release 后缀的包。普通 build 的本机包仍含私人音乐，不要上传。公开前仍需确认参考代码授权并选择项目许可证。**

聊天记录仅记载提供三份本地参考文件，没有上游仓库链接或许可证正文。对包名和类名的公开搜索未找到可确认的上游；用户记忆来自 GitHub 开源项目，需要补充地址核实具体许可证和署名要求。

## 供作者选择的许可证

以下仅为候选，未创建 LICENSE，`mod_license=All Rights Reserved` 保持原值。

| 候选 | 适合的意图 | 主要要求 |
| --- | --- | --- |
| [MIT](https://choosealicense.com/licenses/mit/) | 宽松复用，包括商业和闭源衍生 | 保留版权与许可通知 |
| [Apache-2.0](https://choosealicense.com/licenses/apache-2.0/) | 宽松复用并提供明确专利授权 | 保留许可及相关通知、标记修改、遵守专利条款 |
| [GPL-3.0](https://choosealicense.com/licenses/gpl-3.0/) | 希望分发的衍生作品保持开放 | 对应源码提供及同许可义务，先核实依赖/借鉴代码兼容性 |

来源：[GitHub Choose a License 对照](https://choosealicense.com/appendix/)。代码许可证不自动覆盖第三方音乐。选择后需补充正式许可证、版权主体及素材声明，更新 Mod 元数据；GPL 需明确 only 或 or-later。

## 构建验证

无音乐发布构建：`gradlew.bat build releaseJar` 成功（8s）。检查 ZIP 条目：个人 JAR 保留两段 OGG 及 sounds.json，release JAR 不含 OGG 或 sounds.json，仍包含 NeoForge 元数据。无新增游戏内验证。

在 Windows / Microsoft JDK 21.0.12 下执行 `gradlew.bat build`，结果为 `BUILD SUCCESSFUL in 12s`，5 项任务执行完成。目标 Minecraft 1.21.1 / NeoForge 21.1.248；输出 `build/libs/ashenenderdragon-1.0.0.jar`。

`test NO-SOURCE` 表示没有自动化测试源码，不能据此声称通过了游戏内或多人实测。本次未启动游戏。构建有已有 API 弃用和 Gradle 10 兼容性警告，不影响当前 Gradle 9.2.1 构建。使用了本机已有依赖缓存，未验证完全无缓存环境。
