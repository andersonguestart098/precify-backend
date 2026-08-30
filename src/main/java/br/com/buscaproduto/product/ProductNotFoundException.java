package br.com.buscaproduto.product;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String id) {
        super("Produto não encontrado: " + id);
    }
}
