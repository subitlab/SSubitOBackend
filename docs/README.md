# OAuth 和 SSO 实现文档

本文档回答了关于 SSubitOBackend 项目中 OAuth 实现以及与现有 SSO 关系的问题。

## 快速回答

**问题：这个项目的oauth怎么实现的？和原有的sso在一个api地址上吗？**

**答案：** 
- OAuth 2.0 在 `/serviceApi/oauth/*` 路径实现，用于服务间授权
- SSO 在 `/seiue/*` 路径实现，用于用户登录认证  
- **两者使用不同的API地址**，服务于不同的认证场景

## 详细文档

### 📚 实现说明
查看 [OAuth_SSO_Implementation.md](./OAuth_SSO_Implementation.md) 了解：
- OAuth 2.0 和 SSO 的架构设计
- 令牌类型和认证配置
- 两个系统的主要区别

### 🔧 API 使用示例
查看 [API_Usage_Examples.md](./API_Usage_Examples.md) 了解：
- 具体的 API 调用示例
- 认证流程步骤
- 错误处理和令牌管理

## 核心架构图

```
SSubitOBackend 认证系统
├── OAuth 2.0 系统 (/serviceApi/oauth/*)
│   ├── 授权码流程
│   ├── 令牌刷新
│   ├── 状态查询
│   └── 用户信息获取
├── SSO 系统 (/seiue/*)
│   ├── 希悦登录
│   ├── 学号绑定
│   └── 用户注册
└── 统一 JWT 令牌管理
    ├── 用户令牌
    ├── 服务令牌
    ├── OAuth 令牌系列
    └── 令牌验证机制
```

## 主要特点

✅ **分离的认证系统**: OAuth 和 SSO 使用不同的 API 路径  
✅ **完整的 OAuth 2.0 实现**: 支持授权码流程、令牌刷新等  
✅ **外部 SSO 集成**: 与希悦平台的无缝集成  
✅ **统一的 JWT 管理**: 所有令牌使用相同的验证机制  
✅ **灵活的权限控制**: 支持不同级别的用户信息访问  

## 使用场景

### OAuth 2.0 (/serviceApi/oauth/*)
- 第三方服务需要访问用户数据
- 服务间的安全授权
- 细粒度的权限控制

### SSO (/seiue/*)
- 用户通过希悦账号登录
- 学号绑定和管理
- 新用户注册流程

## 安全特性

- JWT 令牌自动过期
- 密码更改时令牌失效
- 服务密钥撤销保护
- 跨域请求保护
- 速率限制支持

---

**需要更多信息？** 查看源代码中的关键文件：
- `src/main/kotlin/cn/org/subit/JWTAuth.kt` - JWT 令牌管理
- `src/main/kotlin/cn/org/subit/route/ServiceApi.kt` - OAuth API 实现
- `src/main/kotlin/cn/org/subit/route/Seiue.kt` - SSO 实现
- `src/main/kotlin/cn/org/subit/plugin/Authentication.kt` - 认证配置