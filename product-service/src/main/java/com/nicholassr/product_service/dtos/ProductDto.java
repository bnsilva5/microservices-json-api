package com.nicholassr.product_service.dtos;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data // De Lombok: genera getters, setters, toString, equals, hashCode
@NoArgsConstructor // De Lombok: genera constructor sin argumentos
@AllArgsConstructor // De Lombok: genera constructor con todos los argumentos
@Type("products")
public class ProductDto {

    private String name;
    private BigDecimal price;
}
