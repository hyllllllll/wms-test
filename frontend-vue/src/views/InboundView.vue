<script setup lang="ts">
/**
 * 入库管理页 — 入库单创建（任务1）
 *
 * 功能：
 * 1. 供应商名称输入
 * 2. 入库明细列表（支持添加/删除）
 * 3. 每行明细：商品下拉搜索 + 仓库/库位级联选择 + 数量输入
 * 4. 提交入库单
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createInboundOrder, getProducts, getWarehouses, getLocations, type Product, type Warehouse, type Location } from '@/api'

// 供应商名称
const supplierName = ref('')

// 入库明细列表
interface InboundItem {
  productId: number | undefined
  productName: string
  warehouseId: number | undefined
  warehouseName: string
  locationCode: string
  quantity: number
}

const items = ref<InboundItem[]>([])
const submitting = ref(false)

// 商品列表（用于下拉选择）
const products = ref<Product[]>([])
const productLoading = ref(false)

// 仓库列表
const warehouses = ref<Warehouse[]>([])
const warehouseLoading = ref(false)

// 库位列表（根据选择的仓库动态加载）
const locations = ref<Location[]>([])
const locationLoading = ref(false)

// 加载商品列表（支持搜索）
const loadProducts = async (keyword?: string) => {
  productLoading.value = true
  try {
    const res = await getProducts(keyword)
    products.value = res.data || []
  } catch (e) {
    console.error('加载商品失败', e)
  } finally {
    productLoading.value = false
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  warehouseLoading.value = true
  try {
    const res = await getWarehouses()
    warehouses.value = res.data || []
  } catch (e) {
    console.error('加载仓库失败', e)
  } finally {
    warehouseLoading.value = false
  }
}

// 加载库位列表（根据仓库ID）
const loadLocations = async (warehouseId: number) => {
  locationLoading.value = true
  try {
    const res = await getLocations(warehouseId)
    locations.value = res.data || []
  } catch (e) {
    console.error('加载库位失败', e)
  } finally {
    locationLoading.value = false
  }
}

// 商品远程搜索
const handleProductSearch = (query: string) => {
  loadProducts(query)
}

// 仓库变化时加载对应库位
const handleWarehouseChange = (item: InboundItem) => {
  item.locationCode = ''
  if (item.warehouseId) {
    loadLocations(item.warehouseId)
  }
}

// 添加明细行
const addItem = () => {
  items.value.push({
    productId: undefined,
    productName: '',
    warehouseId: undefined,
    warehouseName: '',
    locationCode: '',
    quantity: 1,
  })
}

// 删除明细行
const removeItem = (index: number) => {
  items.value.splice(index, 1)
}

// 商品选择处理
const handleProductSelect = (row: InboundItem) => {
  const product = products.value.find(p => p.id === row.productId)
  if (product) {
    row.productName = product.name
  }
}

// 仓库选择处理
const handleWarehouseSelect = (row: InboundItem) => {
  const warehouse = warehouses.value.find(w => w.id === row.warehouseId)
  if (warehouse) {
    row.warehouseName = warehouse.name
  }
}

// 校验表单
const validateForm = (): boolean => {
  if (!supplierName.value.trim()) {
    ElMessage.warning('请输入供应商名称')
    return false
  }
  if (items.value.length === 0) {
    ElMessage.warning('请至少添加一条入库明细')
    return false
  }
  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    if (!item.productId) {
      ElMessage.warning(`第 ${i + 1} 行明细请选择商品`)
      return false
    }
    if (!item.locationCode) {
      ElMessage.warning(`第 ${i + 1} 行明细请选择库位`)
      return false
    }
    if (!item.quantity || item.quantity <= 0) {
      ElMessage.warning(`第 ${i + 1} 行明细数量必须大于 0`)
      return false
    }
  }
  return true
}

// 提交入库单
const handleSubmit = async () => {
  if (!validateForm()) return

  submitting.value = true
  try {
    const data = {
      supplierName: supplierName.value.trim(),
      items: items.value.map(item => ({
        productId: item.productId!,
        quantity: item.quantity,
        locationCode: item.locationCode,
      })),
    }
    await createInboundOrder(data)
    ElMessage.success('入库单创建成功')
    // 重置表单
    supplierName.value = ''
    items.value = []
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建入库单失败')
  } finally {
    submitting.value = false
  }
}

// 初始化加载数据
onMounted(() => {
  loadProducts()
  loadWarehouses()
})
</script>

<template>
  <div>
    <h3>入库管理 - 创建入库单</h3>

    <!-- 表单 -->
    <el-card style="max-width: 1000px">
      <el-form label-width="100px" style="max-width: 800px">
        <el-form-item label="供应商名称" required>
          <el-input v-model="supplierName" placeholder="请输入供应商名称" clearable />
        </el-form-item>

        <el-form-item label="入库明细">
          <el-button type="primary" @click="addItem" plain>+ 添加明细</el-button>
        </el-form-item>
      </el-form>

      <!-- 明细列表 -->
      <div v-if="items.length > 0" style="margin-top: 16px">
        <el-table :data="items" border stripe style="max-width: 1000px">
          <el-table-column label="序号" width="60" align="center">
            <template #default="{ $index }">
              {{ $index + 1 }}
            </template>
          </el-table-column>

          <el-table-column label="商品" min-width="200">
            <template #default="{ row }">
              <el-select
                v-model="row.productId"
                placeholder="搜索并选择商品"
                filterable
                remote
                :remote-method="handleProductSearch"
                :loading="productLoading"
                style="width: 100%"
                @change="handleProductSelect(row)"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="`${product.name} (${product.sku})`"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="仓库" min-width="150">
            <template #default="{ row }">
              <el-select
                v-model="row.warehouseId"
                placeholder="选择仓库"
                :loading="warehouseLoading"
                style="width: 100%"
                @change="handleWarehouseChange(row)"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouse.name"
                  :value="warehouse.id"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="库位" min-width="150">
            <template #default="{ row }">
              <el-select
                v-model="row.locationCode"
                placeholder="选择库位"
                :loading="locationLoading"
                :disabled="!row.warehouseId"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locations"
                  :key="location.id"
                  :label="location.code"
                  :value="location.code"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="数量" width="120" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="1"
                :max="999999"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 空状态提示 -->
      <el-empty v-if="items.length === 0" description="请点击"添加明细"按钮添加入库商品" />

      <!-- 提交按钮 -->
      <div style="margin-top: 20px">
        <el-button
          type="success"
          size="large"
          :loading="submitting"
          @click="handleSubmit"
          :disabled="items.length === 0"
        >
          提交入库单
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 表格内input样式优化 */
:deep(.el-input-number .el-input__inner) {
  text-align: center;
}
</style>
