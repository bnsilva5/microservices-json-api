package com.nicholassr.product_service.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.nicholassr.product_service.dtos.ProductDto;
import com.nicholassr.product_service.models.Product;
import com.nicholassr.product_service.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.github.jasminb.jsonapi.Links;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos", description = "API para la gestión de productos")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final ResourceConverter resourceConverter;
    private final ObjectMapper objectMapper;

    public ProductController(ProductService productService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.objectMapper = objectMapper;
        this.resourceConverter = new ResourceConverter(objectMapper, Product.class);
    }

    @Operation(summary = "Crear un nuevo producto",
            description = "Crea un nuevo producto en el sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Producto creado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    @PostMapping(consumes = "application/vnd.api+json", produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> createProduct(@RequestBody byte[] requestBody, UriComponentsBuilder ucb) {
        try {
            // 1. Usa ObjectMapper para parsear el JSON raw
            JsonNode rootNode = objectMapper.readTree(requestBody);

            // 2. Extrae la sección 'attributes'
            JsonNode attributesNode = rootNode.path("data").path("attributes");

            // 3. Mapea los atributos a tu ProductDto
            ProductDto productDto = objectMapper.treeToValue(attributesNode, ProductDto.class);

            // 4. Mapear el DTO a la entidad Product
            Product productToCreate = new Product();
            if (productDto == null) {
                throw new IllegalArgumentException("Product data is missing from request body.");
            }
            productToCreate.setName(productDto.getName());
            productToCreate.setPrice(productDto.getPrice());

            Product createdProduct = productService.createProduct(productToCreate);
            logger.info("Producto creado: {}", createdProduct.getId());

            URI location = ucb.path("/api/v1/products/{id}").buildAndExpand(createdProduct.getId()).toUri();
            // 5. Serializar la respuesta (Product con ID) usando ResourceConverter
            byte[] response = resourceConverter.writeDocument(new JSONAPIDocument<>(createdProduct));
            return ResponseEntity.created(location)
                    .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                    .body(response);

        } catch (IOException e) {
            logger.error("Error de lectura/parseo JSON al crear producto: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error al crear producto: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error creating product: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Obtener un producto por ID",
            description = "Recupera un producto específico por su ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Producto encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping(value = "/{id}", produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> getProductById(
            @Parameter(description = "ID del producto a buscar") @PathVariable Long id) {
        Optional<Product> product = productService.getProductById(id);
        if (product.isPresent()) {
            try {
                byte[] response = resourceConverter.writeDocument(new JSONAPIDocument<>(product.get()));
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                        .body(response);
            } catch (DocumentSerializationException e) {
                logger.error("Error serializando producto: {}", id, e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializing product", e);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id " + id);
        }
    }

    @Operation(summary = "Actualizar un producto",
            description = "Actualiza la información de un producto existente por su ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Producto actualizado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @PutMapping(value = "/{id}", consumes = "application/vnd.api+json", produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> updateProduct(
            @Parameter(description = "ID del producto a actualizar") @PathVariable Long id,
            @RequestBody byte[] requestBody) {
        try {
            // Use ObjectMapper to parse the raw JSON
            JsonNode rootNode = objectMapper.readTree(requestBody);

            // Extract the 'id' from the request body's "data" section for validation
            JsonNode idNode = rootNode.path("data").path("id");
            if (idNode.isMissingNode() || idNode.isNull() || !idNode.asText().equals(id.toString())) {
                throw new IllegalArgumentException("Resource ID in payload must match path ID or be present and valid.");
            }

            // Extract the 'attributes' section for ProductDto mapping
            JsonNode attributesNode = rootNode.path("data").path("attributes");
            ProductDto productDto = objectMapper.treeToValue(attributesNode, ProductDto.class);

            if (productDto == null) {
                throw new IllegalArgumentException("Product data is missing from request body.");
            }

            Product updatedProduct = new Product();
            updatedProduct.setName(productDto.getName());
            updatedProduct.setPrice(productDto.getPrice());

            Product savedProduct = productService.updateProduct(id, updatedProduct);
            logger.info("Producto actualizado: {}", savedProduct.getId());

            byte[] response = resourceConverter.writeDocument(new JSONAPIDocument<>(savedProduct));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                    .body(response);

        } catch (IOException e) {
            logger.error("Error de lectura/parseo JSON al actualizar producto: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format: " + e.getMessage(), e);
        } catch (RuntimeException e) { // Catch the RuntimeException from service if product not found
            logger.error("Error al actualizar producto (no encontrado): {}", id, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (DocumentSerializationException e) {
            logger.error("Error serializando respuesta de actualización: {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializing update response", e);
        } catch (Exception e) {
            logger.error("Error inesperado al actualizar producto: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error updating product: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Eliminar un producto",
            description = "Elimina un producto existente por su ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Producto eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "ID del producto a eliminar") @PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            logger.info("Producto eliminado: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error al eliminar producto: {}", id, e);
            // In a real app, you might check if the product existed before throwing 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found or could not be deleted with id " + id, e);
        }
    }

    @Operation(summary = "Listar todos los productos",
            description = "Obtiene una lista paginada de todos los productos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de productos obtenida",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Product.class)))
    })
    @GetMapping(produces = "application/vnd.api+json")
    public ResponseEntity<byte[]> getAllProducts(
            @Parameter(description = "Número de página (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de la página") @RequestParam(defaultValue = "10") int size) throws DocumentSerializationException {

        Page<Product> productPage = productService.getAllProducts(page, size);
        List<Product> products = productPage.getContent();
        logger.info("Listando productos, página: {}, tamaño: {}, total elementos: {}", page, size, productPage.getTotalElements());

        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath("/api/v1/products")
                .toUriString();

        Map<String, String> linksMap = new HashMap<>();
        linksMap.put("self", baseUrl + "?page=" + page + "&size=" + size);
        linksMap.put("first", baseUrl + "?page=0&size=" + size);

        if (productPage.hasPrevious()) {
            linksMap.put("prev", baseUrl + "?page=" + productPage.previousPageable().getPageNumber() + "&size=" + size);
        } else {
            // JSON:API recomienda un enlace a null si no existe la página
            linksMap.put("prev", null);
        }

        if (productPage.hasNext()) {
            linksMap.put("next", baseUrl + "?page=" + productPage.nextPageable().getPageNumber() + "&size=" + size);
        } else {
            linksMap.put("next", null);
        }

        if (productPage.getTotalPages() > 0) {
            linksMap.put("last", baseUrl + "?page=" + (productPage.getTotalPages() - 1) + "&size=" + size);
        } else {
            linksMap.put("last", null);
        }

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("totalPages", productPage.getTotalPages());
        metaMap.put("totalElements", productPage.getTotalElements());
        metaMap.put("currentPage", productPage.getNumber());
        metaMap.put("pageSize", productPage.getSize());

        JSONAPIDocument<List<Product>> jsonApiDocument = new JSONAPIDocument<>(products);
        jsonApiDocument.setMeta(metaMap);
        jsonApiDocument.setLinks(new Links());

        byte[] response = resourceConverter.writeDocumentCollection(jsonApiDocument);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                .body(response);
    }

    // Si tienes otros métodos (getProductById, updateProduct, deleteProduct), cópialos aquí debajo.
}
