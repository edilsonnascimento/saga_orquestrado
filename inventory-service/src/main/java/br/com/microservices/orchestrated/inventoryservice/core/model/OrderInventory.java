package br.com.microservices.orchestrated.inventoryservice.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_inventory")
public class OrderInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false)
    private Integer orderQuantity;

    @Column(nullable = false)
    private Integer oldQuantity;

    @Column(nullable = false)
    private Integer newQuantity;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime updateAt;

    @PrePersist
    private void prePersist() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updateAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updateAt = LocalDateTime.now();
    }

}
