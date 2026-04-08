/**
 * API 请求封装
 * 用于与后端服务器通信
 */

const app = getApp()

/**
 * 获取完整的 API 地址
 */
const getBaseUrl = () => {
  return app.globalData.baseUrl
}

/**
 * 封装请求方法
 * @param {string} url - 请求路径
 * @param {string} method - 请求方法
 * @param {object} data - 请求数据
 */
const request = (url, method = 'GET', data = {}) => {
  return new Promise((resolve, reject) => {
    wx.request({
      url: getBaseUrl() + url,
      method: method,
      data: data,
      header: {
        'content-type': 'application/json'
      },
      success: (res) => {
        if (res.statusCode === 200) {
          if (res.data.code === 0) {
            resolve(res.data.data)
          } else {
            reject(res.data)
          }
        } else {
          reject(res)
        }
      },
      fail: (err) => {
        console.error('请求失败:', err)
        reject(err)
      }
    })
  })
}

/**
 * 小程序扫码登录
 * @param {string} sceneId - 场景ID（从小程序启动参数获取）
 * @param {string} code - 微信登录code
 * @param {string} nickName - 用户昵称（可选）
 * @param {string} avatarUrl - 用户头像URL（可选）
 */
const scanLogin = (sceneId, code, nickName, avatarUrl) => {
  return request('/wechat/login/scan', 'POST', {
    sceneId: sceneId,
    code: code,
    nickName: nickName,
    avatarUrl: avatarUrl
  })
}

/**
 * 获取用户信息
 * @param {string} token - 登录凭证
 */
const getUserInfo = (token) => {
  return request('/user/get/login', 'GET')
}

module.exports = {
  getBaseUrl,
  request,
  scanLogin,
  getUserInfo
}