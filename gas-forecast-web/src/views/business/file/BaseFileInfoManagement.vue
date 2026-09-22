<template>
  <BaseDataManagement ref="managementRef" :page-config="fileInfoDataConfig">
    <template #table-actions>
      <el-button type="success" :icon="Upload" @click="openUpload">上传文件</el-button>
    </template>
  </BaseDataManagement>

  <AppDialog v-model="uploadVisible" eyebrow="原始数据文件" title="上传文件" width="720px" align-center>
    <el-form class="upload-form" :model="uploadForm" label-position="top">
      <el-form-item label="数据类型">
        <el-select v-model="uploadForm.dataType" class="form-control">
          <el-option label="日销量数据" value="daily-sales" />
          <el-option label="月销量数据" value="monthly-sales" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-upload
      v-model:file-list="uploadFiles"
      class="file-uploader"
      drag
      multiple
      :auto-upload="false"
      :limit="20"
      accept=".xlsx,.xls,.csv"
      :on-exceed="handleExceed"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到这里，或点击选择文件</div>
      <template #tip>
        <div class="upload-tip">
          支持 Excel、CSV 文件；当前选择 {{ uploadFiles.length }} 个文件，合计 {{ totalUploadSize }}
        </div>
      </template>
    </el-upload>

    <template #footer>
      <el-button type="info" plain @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!uploadFiles.length" @click="submitUpload">
        提交上传
      </el-button>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import { createRow } from '@/api/management'
import AppDialog from '@/components/AppDialog.vue'
import BaseDataManagement from '../base/BaseDataManagement.vue'
import { fileInfoDataConfig } from '../base/baseDataManagementConfigs'

defineOptions({ name: 'BaseFileInfoManagement' })

const managementRef = ref<InstanceType<typeof BaseDataManagement>>()
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFiles = ref<UploadUserFile[]>([])
const uploadForm = reactive({
  dataType: 'daily-sales'
})

const totalUploadSize = computed(() => {
  const total = uploadFiles.value.reduce((sum, file) => sum + (file.size || 0), 0)
  if (total < 1024 * 1024) {
    return `${Math.max(total / 1024, 0).toFixed(1)} KB`
  }
  return `${(total / 1024 / 1024).toFixed(2)} MB`
})

const openUpload = () => {
  uploadFiles.value = []
  uploadVisible.value = true
}

const handleExceed = () => {
  ElMessage.warning('单次最多选择 20 个文件')
}

const hashFile = async (file?: File) => {
  if (!file || !window.crypto?.subtle) {
    return `mock-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }
  const buffer = await file.arrayBuffer()
  const digest = await window.crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map((item) => item.toString(16).padStart(2, '0'))
    .join('')
}

const createFileRecord = async (file: UploadUserFile) => {
  const rawFile = file.raw
  const fileHash = await hashFile(rawFile)
  await createRow(fileInfoDataConfig.endpoint, {
    fileName: file.name,
    objectKey: `raw/upload/${uploadForm.dataType}/${Date.now()}-${file.name}`,
    fileHash,
    status: 'UPLOADED',
    totalCount: 0,
    errorMessage: ''
  })
}

const submitUpload = async () => {
  if (!uploadFiles.value.length) {
    return
  }
  uploading.value = true
  try {
    for (const file of uploadFiles.value) {
      await createFileRecord(file)
    }
    ElMessage.success('上传记录已创建')
    uploadVisible.value = false
    await managementRef.value?.loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 6px;
}

.form-control {
  width: 100%;
}

.file-uploader :deep(.el-upload-dragger) {
  padding: 30px 20px;
  border-radius: 8px;
}

.upload-icon {
  color: #1890ff;
  font-size: 42px;
}

.upload-tip {
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .upload-form {
    grid-template-columns: 1fr;
  }
}
</style>
