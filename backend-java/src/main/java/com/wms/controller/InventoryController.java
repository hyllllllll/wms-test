package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存与入库单 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 创建入库单
     */
    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderResponse> createInboundOrder(
            @Valid @RequestBody InboundOrderCreateRequest request) {
        InboundOrderResponse response = inventoryService.createInboundOrder(request);
        return ApiResponse.success(201, "入库单创建成功", response);
    }

    /**
     * 入库单列表
     */
    @GetMapping("/inbound-orders")
    public ApiResponse<PageResult<InboundOrderResponse>> listInboundOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Object[] result = inventoryService.listInboundOrders(page, pageSize);
        @SuppressWarnings("unchecked")
        List<InboundOrderResponse> list = (List<InboundOrderResponse>) result[0];
        long total = (Long) result[1];

        PageResult<InboundOrderResponse> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        pageResult.setTotal(total);
        return ApiResponse.success(pageResult);
    }

    /**
     * 入库单详情
     */
    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderResponse> getInboundOrder(@PathVariable Long id) {
        InboundOrderResponse response = inventoryService.getInboundOrderById(id);
        return ApiResponse.success(response);
    }

    /**
     * 库存查询
     * 支持按商品名称/SKU模糊搜索、按仓库筛选和分页
     *
     * @param keyword 商品名称或SKU搜索关键字（可选）
     * @param warehouseId 仓库ID筛选（可选）
     * @param page 页码，默认1
     * @param pageSize 每页条数，默认20，最大100
     */
    @GetMapping("/inventory")
    public ApiResponse<PageResult<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<InventoryResponse> result = inventoryService.queryInventory(
                keyword, warehouseId, page, pageSize);
        return ApiResponse.success(result);
    }
}
