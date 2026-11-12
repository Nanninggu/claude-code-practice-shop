package com.example.shop.repository;

import com.example.shop.domain.Product;
import com.example.shop.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategory(String category);
}
