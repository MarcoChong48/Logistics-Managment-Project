package com.lms.backend.client.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.ProductRepository;

@Service
public class ClientProductService {

    private final ProductRepository productRepository;

    public ClientProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientProductResponse> listProducts(String name) {
        return productRepository.findWithStock("ACTIVE", name.strip()).stream()
                .map(product -> new ClientProductResponse(product.getProductId(), product.getSku(), product.getName(),
                        product.getCurrentPrice(), Math.max(0, product.getStockBalance())))
                .toList();
    }
}
