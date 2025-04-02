<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[16, 16]">
      <!-- 图片展示区 -->
      <a-col :sm="24" :md="16" :xl="18">
        <a-card title="图片预览">
          <div style="text-align: center">
            <a-image style="max-height: 600px; object-fit: contain" :src="picture.url" />
          </div>
        </a-card>
      </a-col>
      <!-- 图片信息区 -->
      <a-col :sm="24" :md="8" :xl="6">
        <a-card title="图片信息">
          <a-descriptions :column="1">
            <a-descriptions-item label="作者">
              <a-space>
                <a-avatar
                  size="small"
                  v-if="picture.user?.userAvatar"
                  :src="picture.user?.userAvatar"
                />
                <a-avatar size="small" v-else :style="avatarStyle" >
                  {{ firstLetter }}
                </a-avatar>
                {{ picture.user?.userName }}
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="名称">
              {{ picture.name ?? '未命名' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category ?? '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
            <a-descriptions-item label="主色调">
              <a-space>
                {{ picture.picColor ?? '-' }}
                <div
                  v-if="picture.picColor"
                  :style="{
                    backgroundColor: toHexColor(picture.picColor),
                    width: '16px',
                    height: '16px',
                  }"
                />
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
          <!-- 图片操作 -->
          <a-space wrap>
            <a-button type="primary" @click="doDownload">
              免费下载
              <template #icon>
                <DownloadOutlined />
              </template>
            </a-button>
            <a-button type="primary" ghost @click="doShare">
              分享
              <template #icon>
                <ShareAltOutlined />
              </template>
            </a-button>
            <a-button v-if="canEdit" type="default" @click="doEdit">
              编辑
              <template #icon>
                <EditOutlined />
              </template>
            </a-button>
            <a-popconfirm
              v-if="canDelete"
              title="确认删除？"
              ok-text="是"
              cancel-text="否"
              @confirm="doDelete"
              @cancel="cancelConfirm"
            >
              <a-button type="primary" danger>
                删除
                <template #icon>
                  <DeleteOutlined />
                </template>
              </a-button>
            </a-popconfirm>
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deletePictureUsingPost,
  getPictureVoByIdWithCacheUsingGet,
} from '@/api/pictureController.ts'
import { EditOutlined, DeleteOutlined, ShareAltOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import router from '@/router'
import ShareModal from '@/components/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'

const props = defineProps<{
  id: string | number
}>()
const picture = ref<API.PictureVO>({})

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (picture.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 获取图片详情
const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdWithCacheUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchPictureDetail()
})

// 编辑
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: {
      id: picture.value.id,
      spaceId: picture.value.spaceId,
    },
  })
}

// 删除
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    router.back()
    message.success('删除成功')
  } else {
    message.error('删除失败')
  }
}
// 取消操作
const cancelConfirm = (e: MouseEvent) => {
  message.info('操作已取消')
}
// 处理下载
const doDownload = () => {
  downloadImage(picture.value.url)
}

// 分享弹窗引用
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>()

// 分享
const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
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
  const userName = picture.value.user?.userName ?? '无名';
  return userName.charAt(0).toUpperCase() || 'W'; // 默认 'W'（无名）
});

// 根据首字母选择颜色并确保字体可见性
const avatarStyle = computed(() => {
  const userName = picture.value.user?.userName ?? '无名';
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
    verticalAlign: 'middle',
  };
});
</script>

<style scoped></style>
