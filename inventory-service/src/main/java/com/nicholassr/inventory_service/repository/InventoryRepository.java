package com.nicholassr.inventory_service.repository;

import com.nicholassr.inventory_service.models.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface InventoryRepository extends JpaRepository <Inventory, Long>{
    /**
     * Busca un registro de inventario por el ID del producto asociado.
     * Este método es crucial para la lógica de negocio, ya que el inventario
     * se gestiona a nivel de 'producto_id'.
     *
     * @param productId El ID del producto.
     * @return Un Optional que contiene el registro de Inventory si se encuentra, o vacío si no.
     */
    Optional<Inventory> findByProductId(Long productId);
}
