package com.nicholassr.inventory_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;

import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.nicholassr.inventory_service.client.ProductServiceClient;
import com.nicholassr.inventory_service.dtos.InventoryDto;
import com.nicholassr.inventory_service.models.Inventory;
import com.nicholassr.inventory_service.services.InventoryServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;


@RestController
@RequestMapping("/api/v1/inventories")
@Tag(name = "Inventario", description = "API para la gestión de inventario de productos")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryServices inventoryService;
    private final ResourceConverter resourceConverter;
    private final ObjectMapper objectMapper;

    public InventoryController(InventoryServices inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.resourceConverter = new ResourceConverter(objectMapper, Inventory.class, InventoryServices.InventoryDetails.class);
    }


    @Operation(summary = "Consultar cantidad disponible de un producto",
            description = "Obtiene la cantidad disponible de un producto específico por su ID, incluyendo detalles del producto.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Información de inventario obtenida exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = InventoryServices.InventoryDetails.class))),
            @ApiResponse(responseCode = "404", description = "Producto o inventario no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor o al comunicarse con el servicio de productos")
    })
    @GetMapping(value = "/products/{productId}", produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> getInventoryByProductId(
            @Parameter(description = "ID del producto para consultar su inventario") @PathVariable Long productId) {
        try {
            // El servicio devuelve un Optional<InventoryDetails> que combina info de inventario y producto
            Optional<InventoryServices.InventoryDetails> inventoryDetails = inventoryService.getInventoryDetailsByProductId(productId);

            if (inventoryDetails.isPresent()) {

                byte[] response = resourceConverter.writeDocument(new JSONAPIDocument<>(inventoryDetails.get()));
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                        .body(response);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory or product not found for ID: " + productId);
            }
        } catch (ProductServiceClient.ProductNotFoundException e) {
            logger.warn("Solicitud de inventario para producto ID {} falló: {}", productId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error al obtener inventario para producto ID {}: {}", productId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting inventory details: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Actualizar la cantidad de inventario de un producto",
            description = "Actualiza la cantidad disponible de un producto específico en el inventario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventario actualizado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Inventory.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida o cantidad negativa"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado en el servicio de productos (opcional, si se valida)"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PatchMapping(value = "/products/{productId}", consumes = "application/vnd.api+json", produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> updateInventoryQuantity(
            @Parameter(description = "ID del producto cuyo inventario se va a actualizar") @PathVariable Long productId,
            @RequestBody byte[] requestBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);
            JsonNode attributesNode = rootNode.path("data").path("attributes");

            // Asegúrate que usas el DTO correcto aquí
            InventoryDto updateDto = objectMapper.treeToValue(attributesNode, InventoryDto.class);

            if (updateDto == null || updateDto.getQuantity() == null) {
                throw new IllegalArgumentException("Quantity attribute is missing or invalid in request body.");
            }

            Inventory updatedInventory = inventoryService.updateInventoryQuantity(productId, updateDto.getQuantity());

            logger.info("---------- | Inventario del producto ID {} actualizado a cantidad: {} | ----------------", productId, updatedInventory.getQuantity());

            byte[] response = resourceConverter.writeDocument(new JSONAPIDocument<>(updatedInventory));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                    .body(response);

        } catch (IOException e) {
            logger.error("Error de lectura/parseo JSON al actualizar inventario para producto ID {}: {}", productId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            logger.error("Error de validación al actualizar inventario para producto ID {}: {}", productId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (DocumentSerializationException e) { // Captura específicamente este error
            logger.error("Error de serialización JSON:API al actualizar inventario para producto ID {}: {}", productId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializing response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error inesperado al actualizar inventario para producto ID {}: {}", productId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating inventory: " + e.getMessage(), e);
        }
    }
}
