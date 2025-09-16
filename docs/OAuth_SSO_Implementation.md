# OAuth和SSO实现说明

## 概述

SSubitOBackend项目同时实现了OAuth 2.0授权系统和SSO（单点登录）功能，但它们服务于不同的目的并使用不同的API地址。

## OAuth 2.0 实现

### API地址
OAuth相关接口位于：`/serviceApi/oauth/*`

### 主要功能
1. **授权码流程** - `/serviceApi/oauth/accessToken`
2. **令牌刷新** - `/serviceApi/oauth/refresh`
3. **授权状态查询** - `/serviceApi/oauth/status`

### 令牌类型
项目实现了5种JWT令牌类型：
- `USER` - 用户令牌
- `SERVICE` - 服务令牌
- `OAUTH_CODE` - OAuth授权码
- `OAUTH_ACCESS_TOKEN` - OAuth访问令牌
- `OAUTH_REFRESH_TOKEN` - OAuth刷新令牌

### 实现细节
```kotlin
// 关键实现文件：
// - src/main/kotlin/cn/org/subit/JWTAuth.kt (JWT令牌管理)
// - src/main/kotlin/cn/org/subit/route/ServiceApi.kt (OAuth API端点)
// - src/main/kotlin/cn/org/subit/plugin/Authentication.kt (认证配置)
```

## SSO (单点登录) 实现

### API地址
SSO相关接口位于：`/seiue/*`

### 主要功能
1. **学号绑定** - `/seiue/bind`
2. **希悦登录/注册** - `/seiue/login`
3. **解绑学号** - `/seiue/bind` (DELETE)

### 外部集成
SSO通过希悦（Seiue）平台实现：
- 外部OAuth提供者：`https://passport.seiue.com`
- API端点：`https://open.seiue.com/api/v3/oauth/me`

### 实现细节
```kotlin
// 关键实现文件：
// - src/main/kotlin/cn/org/subit/route/Seiue.kt (SSO实现)
```

## 主要区别

| 特性 | OAuth 2.0 (`/serviceApi/oauth/*`) | SSO (`/seiue/*`) |
|------|-----------------------------------|------------------|
| **用途** | 服务间授权 | 用户登录认证 |
| **目标用户** | 第三方服务开发者 | 最终用户 |
| **认证方式** | 内部JWT令牌系统 | 外部希悦平台 |
| **令牌管理** | 完整的OAuth 2.0流程 | 简化的SSO流程 |
| **API路径** | `/serviceApi/oauth/*` | `/seiue/*` |

## 认证配置

系统在`Authentication.kt`中配置了三种认证方式：

1. **ssubito-auth** - 标准用户认证
2. **ssubito-oauth-code** - OAuth授权码认证
3. **auth-api-docs** - API文档访问认证

## 总结

**OAuth和SSO在不同的API地址上运行：**
- OAuth系统用于服务间的授权，位于`/serviceApi/oauth/*`
- SSO系统用于用户的单点登录，位于`/seiue/*`
- 两者都基于JWT令牌系统，但服务于不同的认证场景
- 两个系统可以独立使用，也可以配合使用来提供完整的认证和授权解决方案

这种设计允许系统既支持内部服务的OAuth授权，又支持通过外部SSO提供者进行用户认证，提供了灵活且安全的认证架构。