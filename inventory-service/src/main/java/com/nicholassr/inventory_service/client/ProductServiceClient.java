package com.nicholassr.inventory_service.client;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.nicholassr.inventory_service.services.InventoryServices;
import com.nicholassr.product_service.dtos.ProductDto; // Asegúrate de que este import sea correcto

import com.nicholassr.product_service.models.Product;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono; // Importa Mono para el manejo de errores del cliente

import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ProductServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceClient.class);
    private final WebClient webClient;
    private final ResourceConverter resourceConverter;
    private final String apiKey;

    /**
     * Constructor del cliente para el servicio de productos.
     * Inyecta las propiedades de configuración y el ObjectMapper.
     * Configura WebClient con timeouts y headers necesarios.
     *
     * @param productServiceUrl URL base del microservicio de productos.
     * @param apiKey Clave API para autenticación con el servicio de productos.
     * @param timeoutMs Tiempo de espera para las peticiones en milisegundos.
     * @param objectMapper ObjectMapper para serialización/deserialización JSON.
     */
    public ProductServiceClient(
            @Value("${products.service.url}") String productServiceUrl,
            @Value("${products.service.api-key}") String apiKey,
            @Value("${product-service.timeout-ms}") int timeoutMs,
            ObjectMapper objectMapper) {

        this.apiKey = apiKey;

        // Configuración de HttpClient para gestionar timeouts a nivel de conexión, lectura y escritura.
        // Se asegura una configuración robusta para la comunicación de red.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs) // Timeout para la conexión
                .responseTimeout(Duration.ofMillis(timeoutMs)) // Timeout total para la respuesta (desde el envío de la solicitud hasta la recepción completa de la respuesta)
                .doOnConnected(conn -> // Handlers para timeouts de lectura/escritura más específicos
                        conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs / 1000, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(timeoutMs / 1000, TimeUnit.SECONDS)));

        // Construcción de WebClient:
        // - baseUrl: La URL base del servicio de productos.
        // - defaultHeader: Establece el tipo de contenido esperado (JSON:API).
        // - clientConnector: Utiliza el HttpClient configurado para los timeouts.
        this.webClient = WebClient.builder()
                .baseUrl(productServiceUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.parseMediaType("application/vnd.api+json").toString())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        // Configuración de ResourceConverter para mapear las respuestas JSON:API a ProductResponseDto.
        this.resourceConverter = new ResourceConverter(objectMapper, ProductDto.class, InventoryServices.InventoryDetails.class);
    }

    /**
     * Obtiene la información de un producto del servicio de productos por su ID.
     * Implementa reintentos para errores de red o del servidor (5xx).
     * Los errores de cliente (4xx) se manejan sin reintentos, especialmente 404.
     *
     * @param productId El ID del producto a buscar.
     * @return Un Optional que contiene el ProductResponseDto si se encuentra el producto, o vacío si no.
     */
    @Retryable(
            value = { WebClientResponseException.class }, // Reintentar en caso de excepciones de WebClient (red/servidor)
            maxAttemptsExpression = "${product-service.max-retries}", // Número máximo de reintentos desde application.yml
            backoff = @Backoff(delayExpression = "${product-service.retry-delay-ms}") // Retraso entre reintentos desde application.yml
    )
    public Optional<ProductDto> getProductById(Long productId) {
        logger.info("Intentando obtener producto con ID {} del servicio de productos. Reintentando...", productId);
        try {
            String responseBody = webClient.get()
                    .uri("/{id}", productId) // Define la URI para la petición GET
                    .header("X-API-KEY", apiKey) // Agrega el header de autenticación
                    .retrieve() // Inicia la recuperación de la respuesta
                    // Manejo de estados 4xx: Si es un 404, se lanza ProductNotFoundException sin reintentos.
                    // Otros 4xx se transforman en excepciones para detener la ejecución o permitir manejo superior.
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                            logger.warn("Producto con ID {} no encontrado en el servicio de productos (HTTP 404).", productId);
                            // Se lanza una excepción específica para no reintentar en un 404
                            return Mono.error(new ProductNotFoundException("Producto con ID " + productId + " no encontrado."));
                        }
                        logger.error("Error de cliente ({}) al obtener producto {}: {}", clientResponse.statusCode(), productId, clientResponse.headers().asHttpHeaders());
                        return clientResponse.createException(); // Convertir otros 4xx en excepciones de WebClient
                    })
                    // Manejo de estados 5xx: Errores del servidor, se convierten en excepciones para que Spring Retry pueda reintentar.
                    .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                        logger.error("Error del servidor ({}) desde el servicio de productos al obtener producto {}: {}", clientResponse.statusCode(), productId, clientResponse.headers().asHttpHeaders());
                        return clientResponse.createException(); // Convertir 5xx en excepciones de WebClient para reintentos
                    })
                    .bodyToMono(String.class) // Convierte el cuerpo de la respuesta a un String
                    .block(); // Bloquea de forma reactiva para obtener el resultado (usado en un contexto síncrono aquí)

            // Si la respuesta es exitosa y no está vacía, mapea a ProductResponseDto
            if (responseBody != null && !responseBody.isEmpty()) {
                JSONAPIDocument<ProductDto> document = resourceConverter.readDocument(responseBody.getBytes(), ProductDto.class);
                return Optional.ofNullable(document.get());
            }
            return Optional.empty(); // Si el cuerpo está vacío, retorna Optional.empty()

        } catch (ProductNotFoundException e) {
            // Este catch es para el 404 específico, no se reintenta
            logger.warn("Producto con ID {} no fue encontrado (no hay más reintentos).", productId);
            return Optional.empty();
        } catch (WebClientResponseException e) {
            // Este catch maneja otros errores de WebClient (incluidos 5xx, que se reintentarán)
            logger.error("Error de WebClient al obtener producto {}: {}. Se reintentará si está configurado.", productId, e.getMessage());
            throw e; // Relanza la excepción para que Spring Retry la capture
        } catch (Exception e) {
            // Manejo de cualquier otra excepción inesperada
            logger.error("Ocurrió un error inesperado al obtener producto {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("Error inesperado al obtener producto del servicio de productos: " + e.getMessage(), e);
        }
    }

    /**
     * Método de recuperación para WebClientResponseException después de que todos los reintentos fallaron.
     *
     * @param e La última excepción de WebClientResponseException.
     * @param productId El ID del producto que se intentaba obtener.
     * @return Siempre un Optional.empty() indicando que la operación falló definitivamente.
     */
    @Recover
    public Optional<ProductDto> recover(WebClientResponseException e, Long productId) {
        logger.error("Todos los reintentos fallaron para el producto ID {}. Último error: {}. Retornando vacío.", productId, e.getMessage());
        return Optional.empty();
    }

    /**
     * Método de recuperación para RuntimeException después de que todos los reintentos fallaron.
     *
     * @param e La última excepción de RuntimeException.
     * @param productId El ID del producto que se intentaba obtener.
     * @return Siempre un Optional.empty() indicando que la operación falló definitivamente.
     */
    @Recover
    public Optional<ProductDto> recover(RuntimeException e, Long productId) {
        logger.error("Todos los reintentos fallaron para el producto ID {} debido a RuntimeException. Último error: {}. Retornando vacío.", productId, e.getMessage());
        return Optional.empty();
    }

    /**
     * Excepción personalizada para cuando un producto no es encontrado (HTTP 404).
     * No activa reintentos.
     */
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
}