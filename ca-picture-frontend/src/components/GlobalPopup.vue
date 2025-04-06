<template>
  <div>
    <!-- 使用 a-modal 包裹弹窗内容，设置宽度为 600px -->
    <a-modal
      :visible="isVisible"
      title="欢迎使用卡卡梗图！"
      :footer="null"
      @cancel="closePopup"
      centered
      :mask-closable="true"
      width="600px"
    >
      <div class="guide">
        <p><strong>使用指南</strong></p>
        <ol>
          <li>
            <h4>浏览与搜索图片</h4>
            <p>在主页上，您可以轻松浏览网站中的<span class="highlight">所有公开图片</span>。通过输入图片<span class="bold">名称</span>或<span class="bold">简介</span>关键词，您可以快速搜索并找到感兴趣的内容，探索丰富多样的图片资源。</p>
          </li>
          <li>
            <h4>上传至公共图库</h4>
            <p>任何用户都可以将图片上传至<span class="highlight">公共图库</span>，与大家分享您的作品。上传的图片需经过<span class="bold">管理员审核</span>，只有审核通过的图片才会展示在主页上，确保内容的高质量与合规性。</p>
          </li>
          <li>
            <h4>管理个人图库</h4>
            <p>您可以开通专属的<span class="highlight">个人图库</span>，用于存储和管理自己的图片。个人图库支持多种搜索方式（如<span class="bold">关键词</span>、<span class="bold">颜色搜图</span>等），还提供“<span class="highlight">以图搜图</span>”功能，让您通过上传图片快速找到相似的资源，方便高效。</p>
          </li>
          <li>
            <h4>编辑与AI扩图</h4>
            <p>您可以对自己上传的图片进行<span class="bold">编辑</span>，或对加入的团队空间中的图片进行修改，前提是您拥有该空间的<span class="bold">编辑者</span>或<span class="bold">管理员权限</span>。此外，我们提供<span class="highlight">AI扩图</span>功能，帮助您智能扩展图片内容，释放更多创作可能。</p>
          </li>
          <li>
            <h4>团队协同编辑</h4>
            <p>您可以创建<span class="highlight">团队空间</span>并邀请多人加入，共同管理图片资源。团队成员可对同一张图片进行<span class="bold">协同编辑</span>：同一时刻仅限一人编辑，但其他进入编辑页面的成员能够<span class="highlight">实时查看</span>编辑进度，实现高效协作与即时反馈。</p>
          </li>
        </ol>
      </div>
      <div class="contact">
        <p>如有任何疑问，可以联系作者 QQ：<span class="bold">1471699856</span></p>
      </div>
      <a-button type="primary" block @click="closePopup">我知道了</a-button>
    </a-modal>

    <!-- 问号图标 -->
    <div class="help-icon" @click="openPopup">
      ?
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';

// 注册 Ant Design Vue 组件
defineOptions({
  name: 'GlobalPopup'
});

// 定义响应式数据
const isVisible = ref<boolean>(false);

// 关闭弹窗
const closePopup = () => {
  isVisible.value = false;
  localStorage.setItem('hasSeenPopup', 'true');
};

// 打开弹窗
const openPopup = () => {
  isVisible.value = true;
};

// 在组件挂载时检查是否显示弹窗
onMounted(() => {
  const hasSeenPopup = localStorage.getItem('hasSeenPopup');
  if (hasSeenPopup !== 'true') {
    isVisible.value = true;
  }
});
</script>

<style scoped>
.guide {
  margin-bottom: 20px;
}

.guide p strong {
  font-size: 16px;
  color: #333;
}

.guide ol {
  padding-left: 20px;
}

.guide li {
  margin-bottom: 20px;
}

.guide h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
}

.guide p {
  margin: 0;
  font-size: 13px;
  color: #666;
  line-height: 1.6;
}

/* 加粗样式 */
.bold {
  font-weight: 600;
  color: #333; /* 深灰色，突出但不过分抢眼 */
}

/* 高亮变色样式 */
.highlight {
  color: #005cc5; /* 蓝色，与标题一致 */
}

/* 联系信息 */
.contact {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #888;
}

.contact .bold {
  color: #007bff; /* QQ 号码用蓝色突出 */
}

/* 问号图标样式保持不变 */
.help-icon {
  position: absolute; /* 改为 absolute，相对于 footer */
  bottom: 8px; /* 调整到 footer 内部 */
  right: 20px;
  width: 32px; /* 稍微缩小 */
  height: 32px;
  background: #007bff;
  color: #fff;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  z-index: 999;
}

.help-icon:hover {
  background: #0056b3;
}
</style>
