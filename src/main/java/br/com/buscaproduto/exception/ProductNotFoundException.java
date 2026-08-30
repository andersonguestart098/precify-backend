package br.com.buscaproduto.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String id) {
        super("Produto não encontrado: " + id);
    }
}
