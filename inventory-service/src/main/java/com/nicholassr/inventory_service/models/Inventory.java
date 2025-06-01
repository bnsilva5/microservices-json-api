package com.nicholassr.inventory_service.models;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Type("inventories")
public class Inventory {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_seq")
    @SequenceGenerator(name = "inventory_seq", sequenceName = "inventory_seq", allocationSize = 1)
    @Id
    private Long id;

    private Long productId;
    private Integer quantity;

    // Constructor adicional para facilitar la creación de objetos Inventory sin especificar el 'id'
    // El 'id' será generado automáticamente por la base de datos.
    public Inventory(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

}
