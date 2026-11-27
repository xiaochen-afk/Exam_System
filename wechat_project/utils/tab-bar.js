function selectTab(page) {
  const tabBar = typeof page.getTabBar === 'function' ? page.getTabBar() : null
  if (tabBar) tabBar.setData({ selected: '/' + page.route })
}

module.exports = { selectTab }
