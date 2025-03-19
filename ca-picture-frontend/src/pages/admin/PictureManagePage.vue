<template>
  <div id="pictureManagePage"></div>
  <a-flex justify="space-between">
    <h2>图片管理</h2>
    <a-space>
      <a-button type="primary" @click="$router.push('/add_picture')">+ 创建图片</a-button>
      <a-button type="primary" @click="$router.push('/add_picture/batch')">+ 批量创建图片</a-button>
    </a-space>
  </a-flex>
  <div style="margin-bottom: 16px" />
  <!-- 搜索表单 -->
  <a-form layout="inline" :model="searchParams" @finish="doSearch">
    <a-form-item label="关键词" name="searchText">
      <a-input
        v-model:value="searchParams.searchText"
        placeholder="请输入名称或简介"
        allow-clear
      />
    </a-form-item>
    <a-form-item label="类型" name="category">
      <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
    </a-form-item>
    <a-form-item label="标签" name="tags">
      <a-select
        v-model:value="searchParams.tags"
        mode="tags"
        placeholder="请输入标签"
        style="min-width: 180px"
        allow-clear
      />
    </a-form-item>
    <a-form-item label="审核状态" name="reviewStatus">
      <a-select
        v-model:value="searchParams.reviewStatus"
        :options="PIC_REVIEW_STATUS_OPTIONS"
        placeholder="请输入审核状态"
        style="min-width: 180px"
        allow-clear
      />
    </a-form-item>
    <a-form-item>
      <a-button type="primary" html-type="submit">搜索</a-button>
    </a-form-item>
  </a-form>
  <div style="margin-bottom: 16px" />
  <!-- 表格 -->
  <a-table
    :columns="columns"
    :data-source="dataList"
    :pagination="pagination"
    @change="doTableChange"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'url'">
        <a-image :src="record.url" :width="100" />
      </template>
      <!-- 标签 -->
      <template v-if="column.dataIndex === 'tags'">
        <a-space wrap>
          <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">{{ tag }}</a-tag>
        </a-space>
      </template>
      <!-- 图片信息 -->
      <template v-if="column.dataIndex === 'picInfo'">
        <div>格式：{{ record.picFormat }}</div>
        <div>宽度：{{ record.picWidth }}</div>
        <div>高度：{{ record.picHeight }}</div>
        <div>宽高比：{{ record.picScale }}</div>
        <div>大小：{{ (record.picSize / 1024).toFixed(2) }}KB</div>
      </template>
      <!-- 审核信息 -->
      <template v-if="column.dataIndex === 'reviewMessage'">
        <div>审核状态：{{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}</div>
        <div>审核信息：{{ record.reviewMessage }}</div>
        <div>审核人：{{ record.reviewerId }}</div>
        <div v-if="record.reviewTime">
          审核时间：{{ dayjs(record.reviewTime).format('YYYY-MM-DD HH:mm:ss') }}
        </div>
      </template>
      <template v-else-if="column.dataIndex === 'createTime'">
        {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
      </template>
      <template v-else-if="column.key === 'action'">
        <a-space wrap>
          <a-popconfirm
            v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS"
            title="确认审核通过？"
            ok-text="是"
            cancel-text="否"
            @confirm="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
            @cancel="cancelConfirm"
          >
            <a-button>
              通过
            </a-button>
          </a-popconfirm>
          <a-popconfirm
            v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT"
            title="确认审核不通过？"
            ok-text="是"
            cancel-text="否"
            @confirm="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
            @cancel="cancelConfirm"
          >
            <a-button danger>
              拒绝
            </a-button>
          </a-popconfirm>
          <a-button type="primary" ghost @click.prevent="$router.push(`/add_picture?id=${record.id}`)">
            编辑
          </a-button>
          <a-popconfirm
            title="确认删除？"
            ok-text="是"
            cancel-text="否"
            @confirm="doDelete(record.id)"
            @cancel="cancelConfirm"
          >
            <a-button type="primary" danger>
              删除
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </template>
  </a-table>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deletePictureUsingPost, doPictureReviewUsingPost,
  listPictureByPageUsingPost,
  listPictureVoByPageUsingPost
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { PIC_REVIEW_STATUS_ENUM, PIC_REVIEW_STATUS_MAP, PIC_REVIEW_STATUS_OPTIONS } from '../../constants/picture.ts'

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
    width: 80
  },
  {
    title: '图片',
    dataIndex: 'url'
  },
  {
    title: '名称',
    dataIndex: 'name'
  },
  {
    title: '简介',
    dataIndex: 'introduction',
    ellipsis: true
  },
  {
    title: '类型',
    dataIndex: 'category'
  },
  {
    title: '标签',
    dataIndex: 'tags'
  },
  {
    title: '图片信息',
    dataIndex: 'picInfo'
  },
  {
    title: '用户 id',
    dataIndex: 'pictureId',
    width: 80
  },
  {
    title: '审核信息',
    dataIndex: 'reviewMessage'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime'
  },
  {
    title: '编辑时间',
    dataIndex: 'editTime'
  },
  {
    title: '操作',
    key: 'action'
  }
]

// 数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend'
})

// 获取数据
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({
    ...searchParams,
    nullSpaceId: true
  })
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total) => `共 ${total} 条`
  }
})

// 表格变化处理
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 获取数据
const doSearch = () => {
  // 重置页码
  searchParams.current = 1
  fetchData()
}

const cancelConfirm = (e: MouseEvent) => {
  message.info('操作已取消')
}

// 提交审核
const handleReview = async (record: API.Picture, reviewStatus: number) => {
  const reviewMessage = reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  const res = await doPictureReviewUsingPost({
    id: record.id,
    reviewStatus,
    reviewMessage
  })
  if (res.data.code === 0) {
    message.success('审核操作成功')
    // 重新获取列表
    fetchData()
  } else {
    message.error('审核操作失败，' + res.data.message)
  }
}


// 删除数据
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

</script>
