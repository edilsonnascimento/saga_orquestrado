package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.History;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Order;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.inventoryservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.InventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.OrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private final String CURRENT_SOURCE = "INVENTORY_SEVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final InventoryRepository inventoryRepository;
    private final OrderInventoryRepository orderInventoryRepository;

    public void updateInventory(Event event) {
        try {
            checkCurrentValidation(event);
            createOrderInventory(event);
            updateInventory(event.getPayload());
            handleSuccess(event);
        } catch(Exception ex) {
            log.error("Error trying to update inventory: ", ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void checkCurrentValidation(Event event) {
        if(orderInventoryRepository.existsByOrderIdAndTransactionId(event.getOrderId(),
                                                                    event.getTransactionId()))
           throw new ValidationException("There's another transactionId for this validation");
    }

    private void createOrderInventory(Event event) {
        event
                .getPayload()
                .getOrderProducts()
                .forEach(orderProduct -> {
                    var inventory = findInventoryByProductCode(orderProduct.getProduct().getCode());
                    var orderInventory = createOrderInventory(event, orderProduct, inventory);
                    orderInventoryRepository.save(orderInventory);
                });
    }

    private Inventory findInventoryByProductCode(String productCode) {
        return inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Inventory not found by informed product, code: " + productCode));
    }

    private OrderInventory createOrderInventory(Event event, OrderProduct orderProduct, Inventory inventory) {
        return OrderInventory.builder()
                .inventory(inventory)
                .oldQuantity(inventory.getAvailable())
                .orderQuantity(orderProduct.getQuantity())
                .newQuantity(inventory.getAvailable() - orderProduct.getQuantity())
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .build();
    }

    private void updateInventory(Order order) {
        order.getOrderProducts()
                .forEach(orderProduct -> {
                    var inventory = findInventoryByProductCode(orderProduct.getProduct().getCode());
                    checkInventory(inventory.getAvailable(), orderProduct.getQuantity());
                    inventory.setAvailable(inventory.getAvailable() - orderProduct.getQuantity());
                    inventoryRepository.save(inventory);
                });
    }

    private void checkInventory(int available, int orderQuantity) {
        if (orderQuantity > available)
            throw new ValidationException("Product is out of stock!");
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Inventory update successfully!");
    }

    private void addHistory(Event event, String message) {
        var history = History.builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to update invetory: " + message);
    }

    public void rollbackInventory(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        try {
            returnInventoryToPreviousValues(event);
            addHistory(event, "Rollback executed for inventory!");
        } catch (Exception ex) {
            addHistory(event, "Rollback executed for inventory: " + ex.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void returnInventoryToPreviousValues(Event event) {
        orderInventoryRepository
                .findByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())
                .forEach(orderInventory -> {
                    var inventory = orderInventory.getInventory();
                    inventory.setAvailable(orderInventory.getOldQuantity());
                    inventoryRepository.save(inventory);
                    log.info("Resotored inventory for order {} from {} to {}",
                              event.getPayload().getId(),
                              orderInventory.getNewQuantity(),
                              inventory.getAvailable());
                });
    }
}