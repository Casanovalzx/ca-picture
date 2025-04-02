<template>
  <div id="spaceManagePage"></div>
  <a-flex justify="space-between">
    <h2>空间成员管理</h2>
  </a-flex>
  <div style="margin-bottom: 16px" />
  <!-- 添加成员表单 -->
  <a-form layout="inline" :model="formData" @finish="handleSubmit">
    <a-form-item label="用户 id" name="userId">
      <a-input v-model:value="formData.userId" placeholder="请输入用户 id" allow-clear />
    </a-form-item>
    <a-form-item>
      <a-button type="primary" html-type="submit">添加用户</a-button>
    </a-form-item>
  </a-form>
  <div style="margin-bottom: 16px" />
  <!-- 表格 -->
  <a-table :columns="columns" :data-source="dataList">
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'userInfo'">
        <a-space>
          <a-avatar
            size="small"
            v-if="record.user?.userAvatar"
            :src="record.user?.userAvatar"
          />
          <a-avatar size="small" v-else :style="avatarStyle(record)" >
            {{ firstLetter(record) }}
          </a-avatar>
          {{ record.user?.userName }}
        </a-space>
      </template>
      <template v-if="column.dataIndex === 'spaceRole'">
        <a-select
          v-model:value="record.spaceRole"
          :options="SPACE_ROLE_OPTIONS"
          @change="(value) => editSpaceRole(value, record)"
        />
      </template>
      <template v-else-if="column.dataIndex === 'createTime'">
        {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
      </template>
      <template v-else-if="column.key === 'action'">
        <a-space wrap>
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
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { SPACE_ROLE_OPTIONS } from '../../constants/space.ts'
import {
  addSpaceUserUsingPost,
  deleteSpaceUserUsingPost,
  editSpaceUserUsingPost,
  listSpaceUserUsingPost
} from '@/api/spaceUserController.ts'

// 表格列
const columns = [
  {
    title: '用户id',
    dataIndex: 'userId',
  },
  {
    title: '用户名',
    dataIndex: 'userInfo',
  },
  {
    title: '角色',
    dataIndex: 'spaceRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 定义属性
interface Props {
  id: string
}

const props = defineProps<Props>()

// 数据
const dataList = ref([])

// 获取数据
const fetchData = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await listSpaceUserUsingPost({
    spaceId,
  })
  if (res.data.data) {
    dataList.value = res.data.data ?? []
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 添加用户
const formData = reactive<API.SpaceUserAddRequest>({})

const handleSubmit = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await addSpaceUserUsingPost({
    spaceId,
    ...formData,
  })
  if (res.data.code === 0) {
    message.success('添加成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('添加失败，' + res.data.message)
  }
}

// 编辑数据
const editSpaceRole = async (value, record) => {
  const res = await editSpaceUserUsingPost({
    id: record.id,
    spaceRole: value,
  })
  if (res.data.code === 0) {
    message.success('修改成功')
  } else {
    message.error('修改失败，' + res.data.message)
  }
}

// 删除数据
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteSpaceUserUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

const cancelConfirm = (e: MouseEvent) => {
  message.info('操作已取消')
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
const firstLetter = computed(() => (record) => {
  const userName = record?.user?.userName ?? '无名';
  return userName.charAt(0).toUpperCase() || 'W'; // 默认 'W'（无名）
});

// 根据首字母选择颜色并确保字体可见性
const avatarStyle = computed(() => (record) => {
  const userName = record?.user?.userName ?? '无名';
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
