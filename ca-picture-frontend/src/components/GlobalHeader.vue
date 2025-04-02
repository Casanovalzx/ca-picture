<template>
  <div id="globalHeader">
    <a-row :wrap="false">
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="title-bar">
            <img class="logo" src="../assets/logo.png" alt="logo" />
            <div class="title">卡卡梗图</div>
          </div>
        </RouterLink>
      </a-col>
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          :items="items"
          @click="doMenuClick"
        />
      </a-col>
      <a-col flex="120px">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <ASpace>
                <a-avatar
                  v-if="loginUserStore.loginUser.userAvatar"
                  :src="loginUserStore.loginUser.userAvatar"
                />
                <a-avatar v-else :style="avatarStyle">
                  {{ firstLetter }}
                </a-avatar>
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </ASpace>
              <template #overlay>
                <a-menu>
                  <a-menu-item>
                    <router-link to="/my_space">
                      <UserOutlined />
                      我的空间
                    </router-link>
                  </a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref } from 'vue'
import { HomeOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

// 菜单列表
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/pictureManage',
    label: '图片管理',
    title: '图片管理',
  },
  {
    key: '/admin/spaceManage',
    label: '空间管理',
    title: '空间管理',
  },
  {
    key: '/add_picture',
    label: '创建图片',
    title: '创建图片',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    // 管理员才能看到 /admin 开头的菜单
    if (menu?.key?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const items = computed<MenuProps['items']>(() => filterMenus(originItems))

const router = useRouter()
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 当前选中菜单
const current = ref<string[]>([])
// 路由跳转事件
const doMenuClick = ({ key }: { key: string }) => {
  router.push({
    path: key,
  })
}

// 用户注销
const doLogout = async () => {
  const res = await userLogoutUsingPost()
  console.log(res)
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}

// 头像颜色映射
const colorMap = {
  A: '#A9C1D1', B: '#D9BFB0', C: '#A1C2A0', D: '#D1A9C1', E: '#E0C2A8',
  F: '#A0C2CC', G: '#C9C2A0', H: '#C2A698', I: '#C9D1C9', J: '#A0C2C9',
  K: '#C9A9D1', L: '#B5CC9F', M: '#D1B5A9', N: '#A698C2', O: '#D1C2A9',
  P: '#A9C9D1', Q: '#B5CCA0', R: '#CCA9B5', S: '#A0C2C9', T: '#D1C9A9',
  U: '#B5A0CC', V: '#C9C2A0', W: '#C2C2C2', X: '#A0C9B5', Y: '#C2D1CC',
  Z: '#B5A9CC',
};

// 计算首字母
const firstLetter = computed(() => {
  const userName = loginUserStore.loginUser.userName ?? '无名';
  return userName.charAt(0).toUpperCase() || 'W'; // 默认 'W'（无名）
});

// 根据首字母选择颜色并确保字体可见性
const avatarStyle = computed(() => {
  const userName = loginUserStore.loginUser.userName ?? '无名';
  const firstChar = userName.charAt(0).toUpperCase();
  const bgColor = colorMap[firstChar] || colorMap['W']; // 默认用 W 的颜色
  const r = parseInt(bgColor.slice(1, 3), 16);
  const g = parseInt(bgColor.slice(3, 5), 16);
  const b = parseInt(bgColor.slice(5, 7), 16);
  const brightness = (r * 299 + g * 587 + b * 114) / 1000;
  const textColor = brightness > 128 ? '#333333' : '#FFFFFF'; // 深灰或白色
  return {
    color: textColor,
    backgroundColor: bgColor,
  };
});
</script>

<style scoped>
#globalHeader .title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: black;
  font-size: 18px;
  margin-left: 16px;
}

.logo {
  height: 48px;
}
</style>
