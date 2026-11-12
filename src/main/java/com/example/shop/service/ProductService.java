package com.example.shop.service;

import com.example.shop.domain.Product;
import com.example.shop.domain.ProductStatus;
import com.example.shop.dto.ProductRequest;
import com.example.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> getProducts(String category) {
        if (category == null || category.isEmpty()) {
            return productRepository.findAll();
        }
        return productRepository.findByCategory(category);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + id));
    }

    public Product createProduct(ProductRequest req) {
        Product product = new Product();
        product.setName(req.getName());
        product.setCategory(req.getCategory());
        product.setPrice(req.getPrice());
        product.setStock(req.getStock());
        product.setStatus(req.getStock() > 0 ? ProductStatus.ON_SALE : ProductStatus.SOLD_OUT);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    public void decreaseStock(Long productId, int quantity) {
        Product product = getProduct(productId);
        if (product.getStock() < quantity) {
            throw new IllegalStateException("재고가 부족합니다: " + product.getName());
        }
        product.setStock(product.getStock() - quantity);
        if (product.getStock() == 0) {
            product.setStatus(ProductStatus.SOLD_OUT);
        }
        product.setUpdatedAt(LocalDateTime.now());
    }

    public void increaseStock(Long productId, int quantity) {
        Product product = getProduct(productId);
        product.setStock(product.getStock() + quantity);
        if (product.getStatus() == ProductStatus.SOLD_OUT && product.getStock() > 0) {
            product.setStatus(ProductStatus.ON_SALE);
        }
        product.setUpdatedAt(LocalDateTime.now());
    }
}
