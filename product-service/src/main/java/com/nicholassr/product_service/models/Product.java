package com.nicholassr.product_service.models;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Data // Genera getters, setters, toString, equals, hashCode con Lombok
@NoArgsConstructor
@AllArgsConstructor
@Type("products") // Define el tipo de recurso JSON:API
public class Product {

    @jakarta.persistence.Id // Marca el campo como ID para JPA
    @Id // Marca el campo como ID para JSON:API
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal price;

}
