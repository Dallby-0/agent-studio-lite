# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 初始项目结构
- 基础功能实现

### Changed
- 修复前端代理配置，确保请求正确转发
- 完善插件系统，支持OpenAPI3格式插件转换和动态HTTP请求执行
- 增强调试日志，添加插件转换、AI请求、工具调用和HTTP请求执行的详细日志
- 添加AI服务debug配置

### Fixed
- 修复HttpMethod.resolve()方法不存在的问题，改用HttpMethod.valueOf()

## [1.0.0] - 2025-01-XX

### Added
- 项目初始化
- 基础框架搭建
