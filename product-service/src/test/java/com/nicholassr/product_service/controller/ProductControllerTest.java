package com.nicholassr.product_service.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nicholassr.product_service.exception.GlobalExceptionHandler;
import com.nicholassr.product_service.models.Product;
import com.nicholassr.product_service.services.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;
    private ProductController productController;

    // Inicializa ObjectMapper para usarlo en el test
    private final ObjectMapper testObjectMapper = new ObjectMapper();

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    // Define el tipo de medio personalizado JSON:API
    private static final String JSON_API_MEDIA_TYPE = "application/vnd.api+json";

    @BeforeEach
    void setUp() {
        // AQUÍ ES DONDE PASAMOS MANUALMENTE LAS DEPENDENCIAS AL CONSTRUCTOR DEL CONTROLADOR
        // Asegúrate de que el orden de los argumentos coincida con el constructor de tu ProductController
        // public ProductController(ProductService productService, ObjectMapper objectMapper) { ... }
        productController = new ProductController(productService, testObjectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(globalExceptionHandler)
                .build();
    }

    // Método de ayuda para crear una entidad Product para mocking
    // --- Métodos de ayuda (igual que antes, usan 'testObjectMapper' implícitamente o explícitamente) ---
    private Product createMockProduct(Long id, String name, double price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(BigDecimal.valueOf(price));
        return product;
    }

    // Método de ayuda para crear un cuerpo de solicitud JSON:API
    private String createJsonApiRequestBody(String type, String name, double price) throws Exception {
        ObjectNode productAttributes = testObjectMapper.createObjectNode(); // Usa testObjectMapper
        productAttributes.put("name", name);
        productAttributes.put("price", price);

        ObjectNode dataNode = testObjectMapper.createObjectNode(); // Usa testObjectMapper
        dataNode.put("type", type);
        dataNode.set("attributes", productAttributes);

        ObjectNode jsonApiRequestBody = testObjectMapper.createObjectNode(); // Usa testObjectMapper
        jsonApiRequestBody.set("data", dataNode);

        return testObjectMapper.writeValueAsString(jsonApiRequestBody);
    }

    private String createJsonApiUpdateRequestBody(Long id, String name, Double price) throws Exception {
        ObjectNode productAttributes = testObjectMapper.createObjectNode();
        if (name != null) productAttributes.put("name", name);
        if (price != null) productAttributes.put("price", price); // Permite null para tests de inválidos

        ObjectNode dataNode = testObjectMapper.createObjectNode();
        // Solo añade el ID si no es null. Esto permite el test de MissingIdInPayload.
        if (id != null) { // <-- ¡Añadir esta verificación!
            dataNode.put("id", id.toString());
        }
        dataNode.put("type", "products");
        dataNode.set("attributes", productAttributes);

        ObjectNode jsonApiRequestBody = testObjectMapper.createObjectNode();
        jsonApiRequestBody.set("data", dataNode);

        return testObjectMapper.writeValueAsString(jsonApiRequestBody);
    }

    @Test
    void createProduct_shouldReturn201CreatedAndLocationHeader() throws Exception {
        Product mockCreatedProduct = createMockProduct(123L, "Test Product", 99.99);

        when(productService.createProduct(any(Product.class))).thenReturn(mockCreatedProduct);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(JSON_API_MEDIA_TYPE)
                        .accept(JSON_API_MEDIA_TYPE)
                        .content(createJsonApiRequestBody("products", "Test Product", 99.99)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/123"));
    };

    @Test
    void updateProduct_ValidRequest_ReturnsUpdatedProduct() throws Exception {
        Long productId = 6L;
        Product mockUpdatedProduct = createMockProduct(productId, "Producto Actualizado", 150.00);

        when(productService.updateProduct(eq(productId), any(Product.class))).thenReturn(mockUpdatedProduct);

        String jsonApiUpdateBody = createJsonApiUpdateRequestBody(productId, "Producto Actualizado", 150.00);

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(JSON_API_MEDIA_TYPE)
                        .accept(JSON_API_MEDIA_TYPE)
                        .content(jsonApiUpdateBody))
                .andExpect(status().isOk());
    }

    @Test
    void updateProduct_NonExistingId_ReturnsNotFound() throws Exception {
        Long productId = 99L;
        // Simula que el servicio lanza una excepción para un producto no encontrado
        when(productService.updateProduct(eq(productId), any(Product.class)))
                .thenThrow(new RuntimeException("Product not found with id " + productId));

        String jsonApiUpdateBody = createJsonApiUpdateRequestBody(productId, "Nombre Cualquiera", 10.00);

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(JSON_API_MEDIA_TYPE)
                        .accept(JSON_API_MEDIA_TYPE)
                        .content(jsonApiUpdateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_MalformedJson_ReturnsBadRequest() throws Exception {
        Long productId = 1L;
        String malformedJson = "{ \"data\": { \"type\": \"products\", \"attributes\": { \"name\": \"Test\", \"price\": 100.0 }"; // JSON incompleto

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(JSON_API_MEDIA_TYPE)
                        .accept(JSON_API_MEDIA_TYPE)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_InvalidAttributes_ReturnsBadRequest() throws Exception {
        Long productId = 11L;

        // Crea un payload con un precio inválido (ej. negativo, si tu ProductDto lo valida)
        // Para que esto funcione, ProductDto debería tener @Min(0) o similar y usar @Valid en el controlador.
        String jsonApiUpdateBody = createJsonApiUpdateRequestBody(productId, "Producto con precio inválido", -50.00);

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(JSON_API_MEDIA_TYPE)
                        .accept(JSON_API_MEDIA_TYPE)
                        .content(jsonApiUpdateBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProduct_ExistingId_ReturnsNoContent() throws Exception {
        Long productId = 12L;

        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isNoContent()); // Espera un 204 No Content
    }

    @Test
    void deleteProduct_NonExistingId_ReturnsNotFound() throws Exception {
        Long productId = 99L;
        // Cuando el servicio deleteProduct sea llamado con un ID que no existe, simula que lanza una excepción
        // El controlador maneja cualquier excepción aquí y la mapea a 404 Not Found.
        doThrow(new RuntimeException("Product not found with id " + productId))
                .when(productService).deleteProduct(productId);

        mockMvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isNotFound()); // Espera un 404 Not Found
    }

}
