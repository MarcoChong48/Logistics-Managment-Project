package com.lms.backend.client.product;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientProductController {

    private final ClientProductService clientProductService;

    public ClientProductController(ClientProductService clientProductService) {
        this.clientProductService = clientProductService;
    }

    @GetMapping("/api/client/products")
    public List<ClientProductResponse> listProducts(@RequestParam(name = "name", defaultValue = "") String name) {
        return clientProductService.listProducts(name);
    }
}
