package com.nicholassr.inventory_service.dtos;


import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Type("inventories")
public class InventoryDto {
    @Id // Esto es para cuando el ResourceConverter serializa/deserializa un recurso completo con ID
    private String id; // El ID del registro de inventario (String porque así lo maneja JSON:API en el JSON)

    private Long productId; // ID del producto al que se refiere este inventario
    private Integer quantity; // Cantidad de stock disponible

    // Constructor sin ID para la creación inicial (si el ID lo genera la DB)
    public InventoryDto(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
