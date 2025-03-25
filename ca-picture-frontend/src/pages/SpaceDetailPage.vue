<template>
  <div id="spaceDetailPage">
    <!-- 空间信息 -->
    <a-flex justify="space-between">
      <h2>{{ space.spaceName }}（私有空间）</h2>
      <a-space size="middle">
        <a-tooltip
          :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :percent="((space.totalSize * 100) / space.maxSize).toFixed(1)"
            :size="42"
          />
        </a-tooltip>
        <a-button type="primary" @click="$router.push(`/add_picture?spaceId=${id}`)">
          + 创建图片
        </a-button>
        <a-button
          type="primary"
          ghost
          :icon="h(BarChartOutlined)"
          @click="$router.push(`/space_analyze?spaceId=${id}`)"
        >
          空间分析
        </a-button>
        <a-button
          :icon="h(EditOutlined)"
          @click="doBatchEdit"
          :disabled="selectedPictureIds.length === 0"
        >
          批量编辑 ({{ selectedPictureIds.length }})
        </a-button>
        <a-popconfirm
          title="确认删除选中的图片？"
          ok-text="确定"
          cancel-text="取消"
          @confirm="handleBatchDelete"
          :disabled="selectedPictureIds.length === 0"
        >
          <a-button type="primary" danger :disabled="selectedPictureIds.length === 0">
            批量删除 ({{ selectedPictureIds.length }})
          </a-button>
        </a-popconfirm>
        <a-checkbox :checked="isAllSelected" @change="toggleSelectAll" style="margin-right: 16px">
          全选
        </a-checkbox>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    <!-- 搜索表单 -->
    <PictureSearchForm :onSearch="onSearch" />
    <div style="margin-bottom: 16px" />
    <!-- 按颜色搜索 -->
    <a-form-item label="按颜色搜索" style="margin-top: 16px">
      <color-picker format="hex" @pureColorChange="onColorChange" />
    </a-form-item>
    <!-- 图片列表 -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      showOp
      :onReload="fetchData"
      v-model:selected-ids="selectedPictureIds"
    />
    <!-- 分页 -->
    <a-pagination
      style="text-align: center"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      :show-total="() => `图片总数 ${total} / ${space.maxCount}`"
      @change="onPageChange"
    />
    <BatchEditPictureModal
      ref="batchEditPictureModalRef"
      :spaceId="id"
      :pictureList="selectedPictures"
      :onSuccess="onBatchEditPictureSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { formatSize } from '@/utils'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import {
  deletePictureByBatchUsingPost,
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import { EditOutlined, BarChartOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  id: string | number
}>()
const space = ref<API.SpaceVO>({})

// 获取空间详情
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchSpaceDetail()
})

// 获取图片列表
const dataList = ref([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 分页参数
const onPageChange = (page, pageSize) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

// 搜索
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 按颜色搜索
const onColorChange = async (color: string) => {
  const res = await searchPictureByColorUsingPost({
    picColor: color,
    spaceId: props.id,
  })
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data ?? []
    dataList.value = data
    total.value = data.length
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 分享弹窗引用
const batchEditPictureModalRef = ref()

// -------------- 批量编辑和删除-----------------------

const selectedPictureIds = ref<string[]>([])

// 计算属性：根据 selectedPictureIds 过滤出选中的图片
const selectedPictures = computed(() => {
  return dataList.value.filter((picture) => selectedPictureIds.value.includes(picture.id))
})

// 监听 PictureList 传来的选中项变化
const handleSelectedIdsUpdate = (ids: string[]) => {
  selectedPictureIds.value = ids
}

// 打开批量编辑弹窗
const doBatchEdit = () => {
  if (batchEditPictureModalRef.value) {
    batchEditPictureModalRef.value.openModal()
  }
}

// 批量编辑成功后，刷新数据
const onBatchEditPictureSuccess = () => {
  fetchData()
}

// 批量删除
const handleBatchDelete = async () => {
  const res = await deletePictureByBatchUsingPost({
    idList: selectedPictureIds.value, // 直接传字符串数组
    spaceId: props.id, // 从 props.id 获取空间 ID
  })

  if (res.data.code === 0) {
    message.success(`成功删除 ${selectedPictureIds.value.length} 张图片`)
    selectedPictureIds.value = [] // 清空选中状态
    fetchData() // 刷新图片列表
  } else {
    message.error('批量删除失败，' + res.data.message)
    fetchData() // 刷新以同步状态
  }
}

// 全选状态
const isAllSelected = computed(
  () =>
    dataList.value.length > 0 &&
    dataList.value.every((picture) => selectedPictureIds.value.includes(picture.id)),
)

// 全选/取消全选
const toggleSelectAll = (e) => {
  if (e.target.checked) {
    selectedPictureIds.value = dataList.value.map((picture) => picture.id)
  } else {
    selectedPictureIds.value = []
  }
}
</script>

<style scoped></style>
