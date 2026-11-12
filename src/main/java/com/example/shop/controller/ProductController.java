package com.example.shop.controller;

import com.example.shop.domain.Product;
import com.example.shop.dto.ProductRequest;
import com.example.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> getProducts(@RequestParam(required = false) String category) {
        return productService.getProducts(category);
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping
    public Product createProduct(@RequestBody ProductRequest req) {
        return productService.createProduct(req);
    }
}
