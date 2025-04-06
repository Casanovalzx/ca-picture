<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <GlobalSider class="sider" />
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
      <a-layout-footer class="footer">
        <a href="https://github.com/Casanovalzx" target="_blank"> 关于作者 </a>
        <GlobalPopup />
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from '@/components/GlobalSider.vue'
import GlobalPopup from '@/components/GlobalPopup.vue' // 引入 GlobalPopup 组件
</script>

<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  margin-bottom: 1px;
  color: unset;
  background: white;
}

#basicLayout .sider {
  background: #fff;
  padding-top: 23px;
  border-right: 0.5px solid #eee;
}

#basicLayout .content {
  background: linear-gradient(to right, #fefefe, #fff);
  margin-bottom: 28px;
  padding: 28px;
}

#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}

/* 使用 :deep() 替代 ::v-deep */
:deep(.ant-layout-sider-zero-width-trigger) {
  top: 5px !important;
  background: #1677FF;
}

/* 可选：调整 GlobalPopup 的样式，确保不被 footer 遮挡 */
:deep(.popup-overlay) {
  z-index: 1000; /* 确保弹窗在 footer 之上 */
}
</style>
