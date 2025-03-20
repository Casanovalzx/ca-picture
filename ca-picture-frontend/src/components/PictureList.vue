<template>
  <div class="picture-list">
    <!-- 图片列表 -->
    <a-list
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :loading="loading"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <!-- 单张图片 -->
          <a-card hoverable @click="doClickPicture(picture)">
            <template #cover>
              <img
                style="height: 180px; object-fit: cover"
                :alt="picture.name"
                :src="picture.thumbnailUrl ?? picture.url"
                loading="lazy"
              />
            </template>
            <a-card-meta :title="picture.name">
              <template #description>
                <a-flex>
                  <a-tag color="green">
                    {{ picture.category ?? '默认' }}
                  </a-tag>
                  <a-tag v-for="tag in picture.tags" :key="tag">
                    {{ tag }}
                  </a-tag>
                </a-flex>
              </template>
            </a-card-meta>
            <template v-if="showOp" #actions>
              <!-- 复选框 -->
              <a-checkbox
                :checked="selectedIds.includes(picture.id)"
                @click.stop="toggleSelect(picture.id)"
                class="large-checkbox"
              />
              <share-alt-outlined @click="(e) => doShare(picture, e)" />
              <search-outlined @click="(e) => doSearch(picture, e)" />
              <edit-outlined @click="(e) => doEdit(picture, e)" />
              <a-popconfirm
                title="确定删除？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="doDelete(picture)"
                @cancel="cancelConfirm"
              >
                <a-space @click.stop>
                  <delete-outlined />
                </a-space>
              </a-popconfirm>
            </template>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import ShareModal from '@/components/ShareModal.vue'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  onReload?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
})

// 跳转至图片详情
const router = useRouter()
const doClickPicture = (picture) => {
  router.push({
    path: `/picture/${picture.id}`,
  })
}

// --------- 分享模块 ---------
// 分享弹窗引用
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>()

// 分享
const doShare = (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// -------------- 搜索 -------------
const doSearch = (picture, e) => {
  e.stopPropagation()
  window.open(`/search_picture?pictureId=${picture.id}`)
}

// -------------- 编辑 ----------------
const doEdit = (picture, e) => {
  e.stopPropagation()
  router.push({
    path: '/add_picture',
    query: {
      id: picture.id,
      spaceId: picture.spaceId,
    },
  })
}

// 单张图片删除
const doDelete = async (picture) => {
  const id = picture.id
  if (!id) return
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props?.onReload()
    // 从选中列表中移除
    selectedIds.value = selectedIds.value.filter((selectedId) => selectedId !== id)
  } else {
    message.error('删除失败')
  }
}

// 取消操作
const cancelConfirm = (e: MouseEvent) => {
  message.info('操作已取消')
}


// ----------- 监听复选框状态 ---------
// 双向绑定 selectedIds
const selectedIds = defineModel<string[]>('selectedIds', { default: [] })

const toggleSelect = (id: string) => {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((selectedId) => selectedId !== id)
  } else {
    selectedIds.value.push(id)
  }
}

</script>

<style scoped>
/* 增大复选框样式 */
.large-checkbox {
  transform: scale(1.2);
}

/* 可选：增大点击区域 */
.large-checkbox .ant-checkbox {
  padding: 6px;
}
</style>
