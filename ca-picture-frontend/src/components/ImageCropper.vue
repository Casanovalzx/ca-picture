<template>
  <div class="image-cropper">
    <a-modal
      class="image-cropper"
      v-model:visible="visible"
      title="编辑图片"
      :footer="false"
      @cancel="closeModal"
    >
      <vue-cropper
        ref="cropperRef"
        :img="imageUrl"
        :autoCrop="true"
        :autoCropWidth="3000"
        :autoCropHeight="3000"
        :fixedBox="false"
        :centerBox="true"
        :canMoveBox="true"
        :info="true"
        :full="true"
        :high="true"
        outputType="png"
      />
      <div style="margin-bottom: 16px" />
      <!-- 协同编辑操作 -->
      <div class="image-edit-actions" v-if="isTeamSpace">
        <a-space style="display: flex; justify-content: center">
          <a-button v-if="editingUser" disabled> {{ editingUser.userName }}正在编辑</a-button>
          <a-button v-if="canEnterEdit" type="primary" ghost @click="enterEdit">进入编辑</a-button>
          <a-button v-if="canExitEdit" danger ghost @click="exitEdit">退出编辑</a-button>
        </a-space>
      </div>
      <div style="margin-bottom: 16px" />
      <!-- 图片操作 -->
      <div class="image-cropper-edit-actions">
        <a-space style="display: flex; justify-content: center">
          <a-button @click="rotateLeft" :disabled="!canEdit">向左旋转</a-button>
          <a-button @click="rotateRight" :disabled="!canEdit">向右旋转</a-button>
          <a-button @click="changeScale(1)" :disabled="!canEdit">放大</a-button>
          <a-button @click="changeScale(-1)" :disabled="!canEdit">缩小</a-button>
          <a-button type="primary" :loading="loading" :disabled="!canEdit" @click="handleConfirm">
            确认
          </a-button>
        </a-space>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watchEffect } from 'vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebSocket.ts'
import { PICTURE_EDIT_ACTION_ENUM, PICTURE_EDIT_MESSAGE_TYPE_ENUM } from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

const props = defineProps<Props>()

interface Props {
  imageUrl?: string
  picture?: API.PictureVO
  spaceId?: number
  space?: API.SpaceVO
  onSuccess?: (newPicture: API.PictureVO) => void
}

// 是否为团队空间
const isTeamSpace = computed(() => {
  return props.space?.spaceType === SPACE_TYPE_ENUM.TEAM
})

// 编辑器组件的引用
const cropperRef = ref()

// 向左旋转
const rotateLeft = () => {
  cropperRef.value.rotateLeft()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}

// 向右旋转
const rotateRight = () => {
  cropperRef.value.rotateRight()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
}

// 缩放
const changeScale = (num: number) => {
  cropperRef.value.changeScale(num)
  if (num > 0) {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_IN)
  } else {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
  }
}

const loading = ref<boolean>(false)

// 确认裁剪
const handleConfirm = () => {
  cropperRef.value.getCropBlob((blob: Blob) => {
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    // 上传图片
    handleUpload({ file })
    // 避免其他客户端在上传图片完成前被刷新
    saveEdit()
  })
}

/**
 * 上传
 * @param file
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    params.spaceId = props.spaceId
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    message.error('图片上传失败')
  } finally {
    loading.value = false
  }
}

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

// --------- 实时编辑 ---------
const loginUserStore = useLoginUserStore()
let loginUser = loginUserStore.loginUser
// 正在编辑的用户
const editingUser = ref<API.UserVO>()
// 没有用户正在编辑中，可进入编辑
const canEnterEdit = computed(() => {
  return !editingUser.value
})
// 正在编辑的用户是本人，可退出编辑
const canExitEdit = computed(() => {
  return editingUser.value?.id === loginUser.id
})
// 可以编辑
const canEdit = computed(() => {
  // 不是团队空间，则默认可编辑
  if (!isTeamSpace.value) {
    return true
  }
  return editingUser.value?.id === loginUser.id
})

let websocket: PictureEditWebSocket | null

const initWebsocket = () => {
  // 校验图片 ID
  const pictureId = props.picture?.id
  if (!pictureId || !visible.value) {
    return
  }

  // 断开旧连接
  if (websocket && websocket.readyState !== WebSocket.CLOSED) {
    websocket.disconnect()
  }

  // 新建连接
  websocket = new PictureEditWebSocket(pictureId)
  websocket.connect()

  // 失败时重试
  websocket.onerror = () => {
    console.error('WebSocket 连接失败，3秒后重试')
    setTimeout(() => initWebsocket(), 3000)
  }

  // 监听通知消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => {
    console.log('收到通知消息：', msg)
    message.info(msg.message)
  })

  // 监听错误消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => {
    console.log('收到错误消息：', msg)
    message.error(msg.message)
  })

  // 监听进入编辑状态消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
    console.log('收到进入编辑状态消息：', msg)
    message.info(msg.message)
    editingUser.value = msg.user
  })

  // 监听初始化图片状态消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INIT_STATE, (msg) => {
    console.log('收到初始化图片状态：', msg)
    if (msg.user) {
      editingUser.value = msg.user
    }
  })

  // 监听编辑操作消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
    console.log('收到编辑操作消息：', msg)
    message.info(msg.message)
    switch (msg.editAction) {
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT:
        cropperRef.value.rotateLeft()
        break
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT:
        cropperRef.value.rotateRight()
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN:
        cropperRef.value.changeScale(1)
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT:
        cropperRef.value.changeScale(-1)
        break
    }
  })

  // 监听退出编辑状态消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
    console.log('收到退出编辑状态消息：', msg)
    message.info(msg.message)
    editingUser.value = undefined
  })

  // 监听保存图片编辑消息
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.SAVE_EDIT, (msg) => {
    console.log('收到保存图片编辑消息：', msg)
    message.info(msg.message + '，将在 2 秒内刷新')
    // 延迟 2 秒刷新页面
    setTimeout(() => {
      editingUser.value = undefined
      window.location.reload()
    }, 2000)
  })
}

watchEffect(() => {
  // 团队空间才初始化
  if (isTeamSpace.value) {
    initWebsocket()
  }
})

onUnmounted(() => {
  // 断开连接
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
})

// 关闭弹窗
const closeModal = () => {
  visible.value = false
  // 断开连接
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
}

// 进入编辑状态
const enterEdit = () => {
  if (websocket) {
    // 发送进入编辑状态的消息
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT,
    })
  }
}

// 退出编辑状态
const exitEdit = () => {
  if (websocket) {
    // 发送退出编辑状态的消息
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT,
    })
  }
}

// 编辑图片操作
const editAction = (action: string) => {
  if (websocket) {
    // 发送编辑操作的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION,
      editAction: action,
    })
  }
}

// 保存图片操作
const saveEdit = () => {
  if(websocket) {
    // 发送退出保存图片编辑的消息
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.SAVE_EDIT,
    })
  }
}
</script>

<style scoped>
.image-cropper {
  text-align: center;
}

.image-cropper .vue-cropper {
  height: 400px !important;
}

#addPicturePage .edit-bar {
  text-align: center;
  margin: 16px 0;
}
</style>
