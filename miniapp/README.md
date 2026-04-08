# DrawIO 微信小程序

本项目是一个用于配合 DrawIO 后端微信扫码登录功能的微信小程序。

## 功能说明

### 扫码登录流程

```
┌─────────────┐     生成二维码      ┌─────────────┐
│   PC 端     │ ──────────────────> │  后端服务    │
│  (Web)      │                     │ (Spring)    │
│             │ <────────────────── │             │
│             │   sceneId + 二维码   │             │
└─────────────┘                     └─────────────┘
       │                                   ▲
       │ 轮询登录状态                       │
       ▼                                   │
┌─────────────┐                     ┌─────────────┐
│   轮询      │                     │   Redis     │
│ /wechat/    │ <─────────────────> │  状态存储   │
│ login/status│                     │             │
└─────────────┘                     └─────────────┘
                                           ▲
                                           │ 更新状态
┌─────────────┐                     ┌─────────────┐
│  微信小程序  │                     │  后端服务    │
│  (扫码)     │ ──────────────────> │ /wechat/    │
│             │   POST /scan        │ login/scan  │
└─────────────┘                     └─────────────┘
```

### 后端 API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/wechat/login/qrcode` | GET | 生成扫码登录二维码 |
| `/wechat/login/status?sceneId=xxx` | GET | 查询登录状态 |
| `/wechat/login/scan` | POST | 小程序扫码确认登录 |

### 登录状态说明

- `waiting`: 等待扫码
- `scanned`: 已扫码待确认
- `success`: 登录成功
- `expired`: 二维码已过期

## 项目结构

```
miniapp/
├── app.json              # 全局配置
├── app.js                # 小程序入口
├── app.wxss              # 全局样式
├── project.config.json   # 项目配置
├── sitemap.json          # 站点地图
├── pages/
│   ├── index/            # 首页
│   │   ├── index.js
│   │   ├── index.json
│   │   ├── index.wxml
│   │   └── index.wxss
│   └── login/
│       └── scan/         # 扫码登录页面
│           ├── scan.js
│           ├── scan.json
│           ├── scan.wxml
│           └── scan.wxss
├── utils/
│   └── api.js            # API 请求封装
└── images/               # 图片资源
    └── logo.png          # Logo 图片
```

## 配置步骤

### 1. 修改后端地址

编辑 `app.js`，将 `baseUrl` 替换为你的后端服务地址：

```javascript
globalData: {
  userInfo: null,
  baseUrl: 'https://your-domain.com' // 替换为实际的后端地址
}
```

### 2. 配置小程序 AppID

编辑 `project.config.json`，填入你的小程序 AppID：

```json
{
  "appid": "你的小程序AppID"
}
```

### 3. 配置服务器域名

在微信小程序后台配置服务器域名：
- 登录 [微信公众平台](https://mp.weixin.qq.com/)
- 进入「开发」->「开发管理」->「开发设置」->「服务器域名」
- 添加 `request` 合法域名

### 4. 后端配置

确保后端的 `application.yml` 中配置了微信小程序相关信息：

```yaml
wechat:
  mini-app:
    app-id: 你的小程序AppID
    app-secret: 你的小程序AppSecret
    qr-code-expire-minutes: 5
    scan-login-page: pages/login/scan
```

## 使用说明

### 扫码登录

1. PC 端访问登录页面，获取小程序码二维码
2. 用户使用微信扫描二维码
3. 小程序自动打开 `pages/login/scan` 页面
4. 小程序自动获取 `sceneId` 和微信 `code`
5. 调用后端接口完成登录
6. PC 端轮询检测到登录成功，获取用户信息和 Token

### 手动测试

如果非扫码进入小程序，可以在登录页面手动输入场景ID进行测试。

## 开发调试

1. 使用微信开发者工具打开 `miniapp` 目录
2. 在开发者工具中预览和调试

## 注意事项

1. 小程序码生成需要小程序已发布或有体验版
2. 开发调试时可以在开发者工具中勾选「不校验合法域名」
3. 生产环境必须配置正确的服务器域名