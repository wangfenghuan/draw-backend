// pages/login/scan.js
const api = require('../../utils/api.js')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    sceneId: '',
    loading: false,
    loginSuccess: false,
    errorMessage: '',
    userInfo: null,
    countdown: 5 // 倒计时秒数
  },

  /**
   * 生命周期函数--监听页面加载
   * 从二维码扫码进入时，获取场景值
   */
  onLoad(options) {
    console.log('页面加载参数:', options)

    // 从启动参数中获取 scene
    // 注意：scene 需要使用 decodeURIComponent 才能获取到生成二维码时传入的 scene
    let scene = decodeURIComponent(options.scene || '')
    console.log('sceneId:', scene)

    if (scene) {
      this.setData({
        sceneId: scene
      })
      // 自动进行登录确认
      this.doScanLogin()
    } else {
      // 非扫码进入，显示手动输入场景ID的界面（用于测试）
      this.setData({
        errorMessage: '未检测到扫码参数，请扫描PC端二维码登录'
      })
    }
  },

  /**
   * 执行扫码登录
   */
  async doScanLogin() {
    const { sceneId } = this.data

    if (!sceneId) {
      wx.showToast({
        title: '缺少场景ID',
        icon: 'error'
      })
      return
    }

    this.setData({ loading: true, errorMessage: '' })

    try {
      // 1. 调用 wx.login 获取 code
      const loginRes = await new Promise((resolve, reject) => {
        wx.login({
          success: resolve,
          fail: reject
        })
      })

      console.log('wx.login 结果:', loginRes)

      if (!loginRes.code) {
        throw new Error('获取微信登录凭证失败')
      }

      // 2. 可选：获取用户信息
      let nickName = ''
      let avatarUrl = ''

      // 尝试获取用户信息（如果用户已授权）
      try {
        const userInfoRes = await new Promise((resolve, reject) => {
          wx.getUserProfile({
            desc: '用于完善用户资料',
            success: resolve,
            fail: reject
          })
        })
        nickName = userInfoRes.userInfo.nickName
        avatarUrl = userInfoRes.userInfo.avatarUrl
      } catch (e) {
        console.log('用户未授权获取信息，使用默认信息')
      }

      // 3. 调用后端接口完成登录
      await api.scanLogin(sceneId, loginRes.code, nickName, avatarUrl)

      // 4. 登录成功
      this.setData({
        loading: false,
        loginSuccess: true
      })

      wx.showToast({
        title: '登录成功',
        icon: 'success'
      })

      // 5. 开始倒计时关闭页面
      this.startCountdown()

    } catch (error) {
      console.error('登录失败:', error)
      this.setData({
        loading: false,
        errorMessage: error.message || '登录失败，请重试'
      })

      wx.showToast({
        title: '登录失败',
        icon: 'error'
      })
    }
  },

  /**
   * 开始倒计时
   */
  startCountdown() {
    let countdown = 5
    this.setData({ countdown })

    const timer = setInterval(() => {
      countdown--
      this.setData({ countdown })

      if (countdown <= 0) {
        clearInterval(timer)
        // 关闭小程序或返回首页
        wx.switchTab({
          url: '/pages/index/index'
        })
      }
    }, 1000)
  },

  /**
   * 手动输入场景ID（用于测试）
   */
  onSceneIdInput(e) {
    this.setData({
      sceneId: e.detail.value
    })
  },

  /**
   * 手动触发登录（用于测试）
   */
  onManualLogin() {
    this.doScanLogin()
  },

  /**
   * 返回首页
   */
  goHome() {
    wx.switchTab({
      url: '/pages/index/index'
    })
  }
})