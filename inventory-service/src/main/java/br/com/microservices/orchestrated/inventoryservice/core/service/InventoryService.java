package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.InventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.OrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private final String CURRENT_SOURCE = "INVENTORY_SEVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final InventoryRepository inventoryRepository;
    private final OrderInventoryRepository orderInventoryRepository;

    public void updateInvetory(Event event) {
        try {
            checkCurrentValidation(event);
            createOrderInventory(event);
        } catch(Exception ex) {
            log.error("Error trying to update invetory: ", ex);
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
}