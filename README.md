# jei_mod_filter

在 JEI 配方界面增加一个模组筛选列表，点击模组后自动替换 JEI 搜索栏的内容，实现按模组筛选配方。

## 项目规格

| 项目 | 规格 |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge |
| Java | 21 |
| 配方查看器 | JEI |
| 界面位置 | JEI 配方查看界面旁 |
| 模组列表 | 图标 + 名称 |
| 排序 | 名称排序 |
| 列表滚动 | 支持 |
| 清空选项 | JEI 搜索栏内独立选项 |
| 筛选方式 | JEI 原生 `@modid` |
| 状态持久性 | 关闭、重新打开配方界面仍然保留 |

## Overview

A client-side NeoForge mod for Minecraft 1.21.1 that adds a scrollable mod list beside JEI's recipe viewer.

Clicking a mod replaces JEI's current search text with its native `@modid` filter. The separate clear control in the JEI search bar clears only the search text. JEI owns the active filter state, so it remains in place when the recipe viewer is closed and reopened.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- Just Enough Items (JEI) 19.x for NeoForge

## Build

```sh
./gradlew build
```

The distributable jar is created in `build/libs/`.

## Development environment

The Gradle build uses ModDevGradle and downloads the matching Minecraft, NeoForge, and JEI development dependencies. The included Gradle wrapper keeps the build command consistent across development machines.

## Notes

- The mod list is populated from NeoForge's loaded mod metadata and sorted by display name, with mod ID as a stable tie-breaker.
- A mod-provided logo is used when its metadata declares one; otherwise, the list displays a monogram icon.
- The UI is rendered only while JEI's recipe viewer is open.
- 代码由 GPT 辅助编写。
