package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.entity.InboundOrder;
import com.wms.entity.InboundOrderItem;
import com.wms.entity.Inventory;
import com.wms.entity.Product;
import com.wms.repository.InboundOrderItemRepository;
import com.wms.repository.InboundOrderRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 库存与入库单 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * 入库单创建
     * 1. 生成入库单号（格式 IN-YYYYMMDD-XXX）
     * 2. 校验商品和库位是否存在
     * 3. 在事务中同时创建入库单和更新库存
     */
    @Transactional
    public InboundOrderResponse createInboundOrder(InboundOrderCreateRequest request) {
        // 1. 生成入库单号
        String orderNo = generateOrderNo();

        // 2. 校验并收集商品信息
        Map<Long, Product> productMap = new HashMap<>();
        for (var item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new BusinessException("商品不存在: id=" + item.getProductId()));

            // 校验库位是否存在
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new BusinessException("库位不存在: " + item.getLocationCode());
            }

            productMap.put(item.getProductId(), product);
        }

        // 3. 创建入库单主表
        InboundOrder order = InboundOrder.builder()
                .orderNo(orderNo)
                .supplierName(request.getSupplierName())
                .status("COMPLETED")
                .build();
        order = inboundOrderRepository.save(order);
        log.info("创建入库单: orderNo={}, supplier={}", orderNo, request.getSupplierName());

        // 4. 创建明细并更新库存
        List<InboundOrderResponse.InboundOrderItemResponse> itemResponses = new ArrayList<>();
        for (var itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());

            // 创建入库单明细
            InboundOrderItem item = InboundOrderItem.builder()
                    .orderId(order.getId())
                    .productId(itemReq.getProductId())
                    .quantity(itemReq.getQuantity())
                    .locationCode(itemReq.getLocationCode())
                    .build();
            inboundOrderItemRepository.save(item);

            // 更新库存（累加）
            Inventory inventory = inventoryRepository
                    .findByProductIdAndLocationCode(itemReq.getProductId(), itemReq.getLocationCode())
                    .orElse(Inventory.builder()
                            .productId(itemReq.getProductId())
                            .locationCode(itemReq.getLocationCode())
                            .quantity(0)
                            .build());
            inventory.setQuantity(inventory.getQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);
            log.info("更新库存: productId={}, location={}, 累加={}",
                    itemReq.getProductId(), itemReq.getLocationCode(), itemReq.getQuantity());

            // 收集响应数据
            itemResponses.add(InboundOrderResponse.InboundOrderItemResponse.builder()
                    .productId(itemReq.getProductId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .locationCode(itemReq.getLocationCode())
                    .build());
        }

        // 5. 返回响应
        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * 生成入库单号，格式：IN-YYYYMMDD-XXX
     * XXX 为当天序号，每天从001开始
     */
    private String generateOrderNo() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        Optional<String> maxOrderNo = inboundOrderRepository.findMaxOrderNoToday(startOfDay, endOfDay);

        int sequence = 1;
        if (maxOrderNo.isPresent()) {
            String lastNo = maxOrderNo.get();
            // 格式: IN-YYYYMMDD-XXX，取最后3位序号
            if (lastNo != null && lastNo.length() >= 15) {
                try {
                    sequence = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
                } catch (NumberFormatException e) {
                    sequence = 1;
                }
            }
        }

        return "IN-" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
               String.format("%03d", sequence);
    }

    /**
     * 入库单列表
     * @return 包含列表和总数的对象数组 [list, total]
     */
    public Object[] listInboundOrders(int page, int pageSize) {
        var pageable = org.springframework.data.domain.PageRequest.of(page - 1, pageSize,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        var pageResult = inboundOrderRepository.findAll(pageable);

        List<InboundOrderResponse> list = pageResult.getContent().stream()
                .map(this::toOrderResponse)
                .toList();

        return new Object[]{list, pageResult.getTotalElements()};
    }

    /**
     * 入库单详情
     */
    public InboundOrderResponse getInboundOrderById(Long id) {
        InboundOrder order = inboundOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "入库单不存在"));
        return toOrderResponse(order);
    }

    /**
     * 将入库单实体转换为响应 DTO
     */
    private InboundOrderResponse toOrderResponse(InboundOrder order) {
        List<InboundOrderItem> items = inboundOrderItemRepository.findByOrderId(order.getId());

        List<InboundOrderResponse.InboundOrderItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElse(null);
                    return InboundOrderResponse.InboundOrderItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(product != null ? product.getName() : "未知商品")
                            .quantity(item.getQuantity())
                            .locationCode(item.getLocationCode())
                            .build();
                }).toList();

        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * 库存查询 — 候选人实现
     */
    public List<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        // TODO: 候选人实现
        throw new UnsupportedOperationException("请实现库存查询功能（任务2）");
    }
}
