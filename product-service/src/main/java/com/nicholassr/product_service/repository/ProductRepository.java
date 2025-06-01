package com.nicholassr.product_service.repository;

import com.nicholassr.product_service.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
