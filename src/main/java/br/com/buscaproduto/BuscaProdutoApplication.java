package br.com.buscaproduto;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BuscaProdutoApplication {
    public static void main(String[] args) {
        dropOutdatedProductTextIndex();
        SpringApplication.run(BuscaProdutoApplication.class, args);
    }

    /**
     * MongoDB refuses to redefine an existing named index with different options
     * (error 85, IndexOptionsConflict). Whenever the weights of Product_TextIndex
     * change in {@code Product}, the old definition must be dropped so Spring Data
     * can recreate it on startup.
     */
    private static void dropOutdatedProductTextIndex() {
        String uri = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017/busca_produto");
        try (MongoClient client = MongoClients.create(uri)) {
            String database = new ConnectionString(uri).getDatabase();
            client.getDatabase(database).getCollection("products").dropIndex("Product_TextIndex");
        } catch (Exception ignored) {
            // Index absent or already matches the current field weights.
        }
    }
}
