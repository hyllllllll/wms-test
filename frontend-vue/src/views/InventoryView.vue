<script setup lang="ts">
/**
 * 库存查询页（任务2）
 *
 * 功能：
 * 1. 搜索栏：商品名称/SKU 模糊搜索 + 仓库下拉筛选
 * 2. 表格展示：商品名称、SKU、库位编码、仓库名、库存数量、更新时间
 * 3. 库存数量 < 10 的行高亮为红色
 * 4. 支持分页
 * 5. 搜索防抖处理
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventory, getWarehouses, type InventoryItem, type Warehouse } from '@/api'

// 状态管理
const keyword = ref('')
const warehouseId = ref<number | undefined>()
const warehouseList = ref<Warehouse[]>([])
const loading = ref(false)
const inventoryList = ref<InventoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

// 防抖定时器
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouseList.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载仓库列表失败')
  }
}

// 加载库存数据
const loadInventory = async () => {
  loading.value = true
  try {
    const res = await getInventory({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.code === 200) {
      inventoryList.value = res.data?.list || []
      total.value = res.data?.total || 0
    }
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

// 搜索处理（带防抖）
const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    page.value = 1
    loadInventory()
  }, 300)
}

// 分页切换
const handlePageChange = (newPage: number) => {
  page.value = newPage
  loadInventory()
}

// 表格行样式：库存 < 10 高亮红色
const getRowStyle = (row: { row: InventoryItem }) => {
  if (row.row.quantity < 10) {
    return { color: '#F56C6C', fontWeight: 'bold' }
  }
  return {}
}

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 初始化
onMounted(() => {
  loadWarehouses()
  loadInventory()
})
</script>

<template>
  <div>
    <h3>库存查询</h3>

    <!-- 搜索栏 -->
    <div style="display: flex; gap: 12px; margin-bottom: 16px">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称/SKU..."
        style="width: 300px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="warehouseId"
        placeholder="选择仓库"
        clearable
        style="width: 200px"
      >
        <el-option
          v-for="wh in warehouseList"
          :key="wh.id"
          :label="wh.name"
          :value="wh.id"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
    </div>

    <!-- 表格 -->
    <el-table
      :data="inventoryList"
      v-loading="loading"
      border
      stripe
      :row-style="getRowStyle"
    >
      <el-table-column prop="productName" label="商品名称" min-width="150" />
      <el-table-column prop="sku" label="SKU" width="150" />
      <el-table-column prop="locationCode" label="库位编码" width="150" />
      <el-table-column prop="warehouseName" label="仓库" width="120" />
      <el-table-column prop="quantity" label="库存数量" width="100" align="right" />
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <!-- 库存预警列 -->
      <template #empty>
        <el-empty description="暂无库存数据，请先完成入库操作" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div style="margin-top: 16px; text-align: right">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
/* 库存预警提示 */
:deep(.el-table tr) {
  transition: background-color 0.2s;
}
</style>
