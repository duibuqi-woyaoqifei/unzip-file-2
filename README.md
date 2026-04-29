# 解压专家 (Unzip Pro) - 高性能 Android 原生解压缩应用

## ✨ 核心特性
- **全格式支持**：基于原生 C++ 实现，支持 ZIP, RAR, 7Z, TAR, GZ 等主流压缩格式。
- **高性能引擎**：使用 JNI 调用原生库，处理大文件解压速度远超纯 Java 实现。

## 🛠 技术架构
- **应用层 (App Layer)**：Kotlin + ViewBinding + 协程 (Coroutines)。
- **原生层 (Native Layer)**：C++20 + NDK + CMake，集成 `libzip`, `unrar`, `lzma` 等高性能库。
- **安全存储**：使用 `EncryptedSharedPreferences` 存储会员数据与收益记录，带 Checksum 校验。

## 📂 项目结构
- `app/src/main/cpp`: 原生 C++ 源码及解压库集成。
- `app/src/main/java/.../membership`: 会员与奖励系统核心逻辑。
- `app/src/main/java/.../utils`: 包含友盟统计、广告管理、设备指纹等工具类。
- `.github/workflows`: 自动化部署与同步脚本。

## 🔨 开发环境要求
- **Android Studio**: Flamingo 或更高版本。
- **JDK**: 17+。
- **NDK**: r25b 或以上。
- **Gradle**: 8.0+。

## 📜 许可证
MIT License (部分原生库遵循其各自开源协议)。
