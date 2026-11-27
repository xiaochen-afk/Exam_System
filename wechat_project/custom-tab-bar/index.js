const tabs = [
  { path: '/pages/home/home', label: '首页', icon: 'home' },
  { path: '/pages/exams/exams', label: '考试', icon: 'calendar' },
  { path: '/pages/notices/notices', label: '通知', icon: 'bell' },
  { path: '/pages/mine/mine', label: '我的', icon: 'user' }
]

Component({
  data: { selected: '', tabs }
})
