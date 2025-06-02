# Inventario de productos

Este proyecto está desarrollado utilizando las siguientes tecnologías y patrones de diseño. La finalidad de este servicio es proporcionar una API que interactúe con una base de datos PostgreSQL, implementando funcionalidades clave como la autenticación mediante API Key y el uso de JPA para la persistencia de datos.

## Tecnologías Utilizadas

- **Java 17**: Lenguaje de programación utilizado, aprovechando sus nuevas características de rendimiento, sintaxis y seguridad.
- **Spring Boot**: Framework utilizado para desarrollar aplicaciones Java de manera rápida y sencilla, optimizado para crear microservicios y APIs RESTful.
- **JPA (Java Persistence API)**: Proporciona un estándar para la gestión de datos relacionales, facilitando la interacción con bases de datos y la creación de entidades.
- **JSON:API**: Protocolo utilizado para estructurar las respuestas de la API en formato JSON, promoviendo la consistencia y eficiencia en la comunicación entre el servidor y el cliente.
- **API KEY**: Mecanismo de autenticación y autorización utilizado para asegurar que sólo los usuarios o aplicaciones que poseen la clave API tengan acceso a los recursos del servidor.
- **Junit**: Framework utilizado para realizar pruebas unitarias y asegurar la calidad y fiabilidad del código.
- **PostgreSQL**: Sistema de gestión de bases de datos relacionales utilizado para almacenar los datos del proyecto. Se ha seleccionado debido a su robustez, escalabilidad y soporte completo de SQL.

**Justificación de PostgreSQL**: Se eligió PostgreSQL debido a que es una base de datos robusta y eficiente para manejar relaciones entre tablas. En este proyecto, se trabajan con dos tablas relacionadas, lo que hace que PostgreSQL sea una opción ideal para gestionar estas relaciones de manera eficiente.

## Patrones de Diseño Implementados

### 1. **DTO (Data Transfer Object)**
   - Se utiliza el patrón DTO para transferir datos entre las capas de la aplicación de manera estructurada y desacoplada. Esto mejora la mantenibilidad del código y facilita la integración con otras aplicaciones o servicios, al asegurarse de que solo se transfieren los datos necesarios.

### 2. **Repository**
   - El patrón Repository se usa para abstraer la lógica de acceso a datos. Permite interactuar con la base de datos de manera más sencilla, al proporcionar métodos específicos para realizar operaciones CRUD (crear, leer, actualizar, eliminar) sin tener que escribir consultas SQL complejas directamente en el código de la aplicación.

## Estructura del Proyecto

- **src/main/java**: Contiene el código fuente principal de la aplicación.
  - **com.nicholassr**: Paquete base donde se encuentran los controladores, servicios, repositorios y entidades.
- **src/test/java**: Contiene las pruebas unitarias y de integración utilizando JUnit.

## Instrucciones para Ejecutar el Proyecto

1. **Requisitos Previos**:
   - Java 17
   - Maven (para la gestión de dependencias)
   - PostgreSQL en ejecución

2. **Clonar el Repositorio**:
   ```bash
   https://github.com/bnsilva5/microservices-json-api.git

# Endpoints del proyecto

## Products

### Documentación Swagger
http://localhost:8082/swagger-ui/index.html#/Productos

- **POST** /api/v1/products?Content-Type=application/vnd.api+json&Accept=application/vnd.api+json&API-Key=xxxxxx
- **GET** /api/v1/products/3
- **PUT** /api/v1/products/3
- **DELETE** /api/v1/products/2
- **GET** /api/v1/products

## Inventory

### Documentación Swagger
http://localhost:8083/swagger-ui/index.html

- **GET** /api/v1/inventories/products/5
- **PATCH** /api/v1/inventories/products/5

## Instalacion y ejecucion
- Java 17 y Maven
- PostgreSql
- Intellij / Eclipse (O algun editor para java - spring)
- Clonar el repositorio rama master, abrir en el editor de codigo e instalar las dependencias en los dos microservicios, ejecutar/correr el archivo de aplicacion de los dos servicios.
- Probar endpoinst con postman.
