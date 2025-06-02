package com.nicholassr.inventory_service.services;


import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import com.nicholassr.inventory_service.repository.InventoryRepository;
import com.nicholassr.inventory_service.client.ProductServiceClient;
import com.nicholassr.inventory_service.models.Inventory;
import com.nicholassr.inventory_service.dtos.ProductDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class InventoryServices {
    private static final Logger logger = LoggerFactory.getLogger(InventoryServices.class);
    private final InventoryRepository inventoryRepository;
    private final ProductServiceClient productServiceClient;



    public InventoryServices(InventoryRepository inventoryRepository, ProductServiceClient productServiceClient) {
        this.inventoryRepository = inventoryRepository;
        this.productServiceClient = productServiceClient;
    }

    /**
     * Consulta la cantidad disponible de un producto específico por su ID.
     * Llama al microservicio de productos para obtener la información del producto.
     *
     * @param productId El ID del producto.
     * @return Un Optional que contiene la información combinada del inventario y el producto, o vacío si no se encuentra.
     */
    @Transactional(readOnly = true)
    public Optional<InventoryDetails> getInventoryDetailsByProductId(Long productId) {
        // 1. Obtener la información del producto desde el microservicio de productos
        Optional<ProductDto> productDto = productServiceClient.getProductById(productId);

        if (productDto.isEmpty()) {
            logger.warn("Producto con ID {} no encontrado en el servicio de productos.", productId);
            return Optional.empty(); // Si el producto no existe, no hay detalles de inventario que mostrar
        }

        // 2. Obtener la información de inventario desde la base de datos local
        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);

        // Si no existe un registro de inventario para este producto, puedes devolverlo con cantidad 0
        // o considerarlo como no encontrado. Aquí, lo devolvemos con 0.
        Inventory currentInventory = inventory.orElseGet(() -> {
            logger.info("Inventario para producto ID {} no encontrado localmente. Asumiendo cantidad 0.", productId);
            // Si el producto existe pero no tiene un registro de inventario, crea uno temporal con 0
            return new Inventory(productId, 0);
        });

        logger.info("Consulta de inventario para producto ID {}: Cantidad {}, Nombre Producto: {}",
                productId, currentInventory.getQuantity(), productDto.get().getName());

        // 3. Combinar y devolver los detalles
        return Optional.of(new InventoryDetails(
                currentInventory.getId(),
                productId,
                productDto.get().getName(),
                BigDecimal.valueOf(productDto.get().getPrice()),
                currentInventory.getQuantity()
        ));
    }

    /**
     * Actualiza la cantidad disponible de un producto específico tras una compra.
     * Si el inventario no existe para el producto, lo crea.
     * Emite un evento simple (mensaje en consola).
     *
     * @param productId El ID del producto.
     * @param newQuantity La nueva cantidad a establecer (no es un delta, es la cantidad final).
     * @return El objeto Inventory actualizado.
     */
    @Transactional
    public Inventory updateInventoryQuantity(Long productId, Integer newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(productId);
        Inventory inventoryToSave;

        if (existingInventory.isPresent()) {
            inventoryToSave = existingInventory.get();
            logger.info("Actualizando inventario para producto ID {}. Cantidad anterior: {}, Cantidad nueva: {}",
                    productId, inventoryToSave.getQuantity(), newQuantity);
            inventoryToSave.setQuantity(newQuantity);
        } else {
            logger.info("Creando nuevo registro de inventario para producto ID {}. Cantidad inicial: {}",
                    productId, newQuantity);
            inventoryToSave = new Inventory(productId, newQuantity);
        }

        Inventory savedInventory = inventoryRepository.save(inventoryToSave);

        // Emitir un evento simple (mensaje en consola)
        emitInventoryChangeEvent(savedInventory.getProductId(), savedInventory.getQuantity());

        return savedInventory;
    }


    private void emitInventoryChangeEvent(Long productId, Integer newQuantity) {
        logger.info("EVENTO DE INVENTARIO: La cantidad del producto {} ha cambiado a {}.", productId, newQuantity);
        // Aquí podrías publicar a un topic de Kafka, una cola de RabbitMQ, etc.
    }

    // Clase auxiliar para combinar la información del inventario y el producto para la respuesta
    // Esto es un DTO compuesto, no una entidad.
    @Getter
    @Setter
    @NoArgsConstructor
    @Type("inventory-details") // <-- ¡IMPORTANTE! Anotación @Type de jsonapi-converter
    public static class InventoryDetails {

        @Id // <-- ¡IMPORTANTE! Anotación @Id de jsonapi-converter
        private String resourceId; // Usaremos este campo como el ID del recurso JSON:API
        private Long productId;
        private String productName;
        private java.math.BigDecimal productPrice;
        private Integer quantityAvailable;

        public InventoryDetails(Long inventoryId, Long productId, String productName, java.math.BigDecimal productPrice, Integer quantityAvailable) {
            this.resourceId = (inventoryId != null) ? inventoryId.toString() : "product-" + productId.toString();
            this.productId = productId;
            this.productName = productName;
            this.productPrice = productPrice;
            this.quantityAvailable = quantityAvailable;
        }
    }
}
