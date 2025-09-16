# API 使用示例

## OAuth 2.0 API 使用示例

### 1. 获取OAuth访问令牌 (授权码流程)

**端点**: `GET /serviceApi/oauth/accessToken`

**认证**: 需要服务令牌 (Authorization) 和授权码 (Oauth-Code)

```http
GET /serviceApi/oauth/accessToken?time=3600
Authorization: Bearer <service_token>
Oauth-Code: Bearer <oauth_code>
```

**响应**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 7776000
}
```

### 2. 通过用户ID获取访问令牌

**端点**: `GET /serviceApi/accessToken`

**认证**: 需要服务令牌

```http
GET /serviceApi/accessToken?user=123&time=1800
Authorization: Bearer <service_token>
```

### 3. 刷新访问令牌

**端点**: `GET /serviceApi/oauth/refresh`

**认证**: 需要刷新令牌

```http
GET /serviceApi/oauth/refresh?time=3600
Authorization: Bearer <refresh_token>
```

**响应**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 3600
}
```

### 4. 检查授权状态

**端点**: `GET /serviceApi/oauth/status`

**认证**: 需要访问令牌

```http
GET /serviceApi/oauth/status
Authorization: Bearer <access_token>
```

**响应**:
```json
"AUTHORIZED"  // 可能的值: "AUTHORIZED", "UNAUTHORIZED", "CANCELED"
```

### 5. 获取用户信息

**端点**: `GET /serviceApi/info`

**认证**: 需要访问令牌

```http
GET /serviceApi/info
Authorization: Bearer <access_token>
```

## SSO API 使用示例

### 1. 绑定学号 (重定向到希悦)

**端点**: `GET /seiue/bind`

```http
GET /seiue/bind?redirect_uri=https://example.com/callback
```

这会重定向到希悦的授权页面:
```
https://passport.seiue.com/authorize?response_type=token&client_id=<client_id>&school_id=<school_id>&redirect_uri=<encoded_redirect>
```

### 2. 完成绑定

**端点**: `POST /seiue/bind`

```http
POST /seiue/bind?access_token=<seiue_token>&active_reflection_id=<reflection_id>
Authorization: Bearer <user_token>
```

### 3. 希悦登录/注册

**端点**: `POST /seiue/login`

```http
POST /seiue/login?access_token=<seiue_token>&active_reflection_id=<reflection_id>
Content-Type: application/json

{
  "password": "user_password",
  "email": "user@example.com",
  "emailCode": "123456"
}
```

**响应** (成功):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**响应** (需要额外信息):
```json
{
  "needPassword": true,
  "email": "existing@example.com"
}
```

### 4. 解绑学号

**端点**: `DELETE /seiue/bind`

```http
DELETE /seiue/bind?studentId=20210001
Authorization: Bearer <user_token>
```

## 认证流程示例

### OAuth服务授权流程

1. 用户访问服务，服务检测到用户未授权
2. 服务重定向用户到授权页面
3. 用户授权后，前端调用 `/authorization/code` 获取授权码
4. 前端将授权码返回给服务
5. 服务使用授权码调用 `/serviceApi/oauth/accessToken` 获取访问令牌
6. 服务使用访问令牌调用其他API获取用户信息

### SSO登录流程

1. 用户访问登录页面
2. 点击"希悦登录"，重定向到 `/seiue/bind`
3. 系统重定向到希悦授权页面
4. 用户在希悦完成授权，获得access_token和active_reflection_id
5. 前端调用 `/seiue/login` 接口进行登录
6. 系统返回内部的JWT令牌供后续使用

## 令牌有效期

| 令牌类型 | 默认有效期 | 最大有效期 |
|----------|------------|------------|
| 用户令牌 | 90天 | - |
| 服务令牌 | 180天 | - |
| OAuth授权码 | 10分钟 | - |
| OAuth访问令牌 | 1天 | 30天 |
| OAuth刷新令牌 | 90天 | - |

## 错误处理

常见的HTTP状态码：

- `401` - 无效令牌或未登录
- `403` - 权限不足  
- `404` - 资源不存在
- `400` - 请求参数错误

错误响应示例：
```json
{
  "code": 401,
  "message": "Invalid token",
  "subCode": 1
}
```