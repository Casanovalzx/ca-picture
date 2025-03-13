<template>
  <div class="picture-list">
    <!-- 批量操作区域 -->
    <div v-if="showOp" style="margin-bottom: 16px; text-align: right;">
      <a-checkbox
        :checked="isAllSelected"
        @change="toggleSelectAll"
        style="margin-right: 16px;"
      >
        全选
      </a-checkbox>
      <a-popconfirm
        title="确认删除选中的图片？"
        ok-text="确定"
        cancel-text="取消"
        @confirm="handleBatchDelete"
      >
        <a-button
          type="primary"
          danger
          :disabled="selectedIds.length === 0"
        >
          批量删除 ({{ selectedIds.length }})
        </a-button>
      </a-popconfirm>
    </div>
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
              <a-space @click="(e) => doSearch(picture, e)">
                <search-outlined />
                搜索
              </a-space>
              <a-space @click="(e) => doEdit(picture, e)">
                <edit-outlined />
                编辑
              </a-space>
              <a-popconfirm
                title="确定删除？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="doDelete(picture)"
              >
                <a-space @click.stop>
                  <delete-outlined />
                  删除
                </a-space>
              </a-popconfirm>
            </template>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons-vue'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  onReload?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false
})

// 管理选中的图片 ID
const selectedIds = ref<string[]>([])

// 跳转至图片详情
const router = useRouter()
const doClickPicture = (picture) => {
  router.push({
    path: `/picture/${picture.id}`
  })
}

// 搜索
const doSearch = (picture, e) => {
  e.stopPropagation()
  window.open(`/search_picture?pictureId=${picture.id}`)
}

// 编辑
const doEdit = (picture, e) => {
  e.stopPropagation()
  router.push({
    path: '/add_picture',
    query: {
      id: picture.id,
      spaceId: picture.spaceId
    }
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
    selectedIds.value = selectedIds.value.filter(selectedId => selectedId !== id)
  } else {
    message.error('删除失败')
  }
}

// 切换单张图片选中状态
const toggleSelect = (id: string) => {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter(selectedId => selectedId !== id)
  } else {
    selectedIds.value.push(id)
  }
}

// 全选/取消全选
const isAllSelected = computed(() =>
  props.dataList.length > 0 && props.dataList.every(picture => selectedIds.value.includes(picture.id))
)

const toggleSelectAll = (e) => {
  if (e.target.checked) {
    selectedIds.value = props.dataList.map(picture => picture.id)
  } else {
    selectedIds.value = []
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return

  const res = await Promise.all(
    selectedIds.value.map(id => deletePictureUsingPost({ id }))
  )

  const allSuccess = res.every(r => r.data.code === 0)
  if (allSuccess) {
    message.success(`成功删除 ${selectedIds.value.length} 张图片`)
    selectedIds.value = [] // 清空选中
    props?.onReload()
  } else {
    message.error('部分或全部删除失败')
    props?.onReload() // 刷新列表以同步最新状态
  }
}

// 取消操作（可选）
const cancelConfirm = (e: MouseEvent) => {
  message.info('操作已取消')
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
