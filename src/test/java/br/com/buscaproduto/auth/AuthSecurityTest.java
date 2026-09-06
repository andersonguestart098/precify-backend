package br.com.buscaproduto.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.buscaproduto.config.WebConfig;
import br.com.buscaproduto.controller.*;
import br.com.buscaproduto.favorite.*;
import br.com.buscaproduto.repository.ProductRepository;
import br.com.buscaproduto.service.*;
import br.com.buscaproduto.security.SecurityConfig;
import br.com.buscaproduto.model.CatalogMaterial;

@SpringBootTest(classes=AuthSecurityTest.TestApp.class, properties={
    "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.cors.allowed-origins=https://busca-produto-frontend.vercel.app"})
@AutoConfigureMockMvc
class AuthSecurityTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
    @Import({SecurityConfig.class, WebConfig.class, AuthService.class, AuthController.class, UserController.class, CatalogDetailController.class,
        FavoriteController.class, CatalogSearchService.class, CatalogSearchController.class, MediaController.class})
    static class TestApp {}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired AuthService auth;
    @MockitoBean UserRepository users;
    @MockitoBean FavoriteRepository favorites;
    @MockitoBean CatalogService catalog;
    @MockitoBean ProductRepository products;
    @MockitoBean GridFsTemplate files;
    final Map<String,AppUser> db = new HashMap<>();
    final Map<String,Favorite> fav = new HashMap<>();
    @BeforeEach void setup() {
        when(users.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));
        when(users.insert(any(AppUser.class))).thenAnswer(i -> { AppUser u=i.getArgument(0);
            if(db.containsKey(u.id())) throw new org.springframework.dao.DuplicateKeyException("duplicate");
            db.put(u.id(),u); return u; });
        when(favorites.findByUserId(anyString())).thenAnswer(i -> fav.values().stream().filter(f -> f.userId().equals(i.getArgument(0))).toList());
        when(favorites.save(any(Favorite.class))).thenAnswer(i -> { Favorite f=i.getArgument(0); fav.put(f.id(),f); return f; });
        doAnswer(i -> { fav.remove(i.getArgument(0)); return null; }).when(favorites).deleteById(anyString());
        when(catalog.findAll()).thenReturn(List.of(new CatalogMaterial("1.1.1","1","Segmento","1.1","Familia","Material","EM_REVISÃO","",List.of())));
        when(products.findAll()).thenReturn(List.of());
    }
    String register(String email) throws Exception {
        if (!db.containsKey("bootstrap@example.com")) auth.createUser("Admin", "bootstrap@example.com", "SenhaTeste123!", AppUser.Role.ADMIN);
        String adminToken = auth.login("bootstrap@example.com", "SenhaTeste123!").accessToken();
        var body = json.writeValueAsString(Map.of("name","Teste","email",email,"password","SenhaTeste123!","role","USER"));
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        return auth.login(email, "SenhaTeste123!").accessToken();
    }
    @Test void registersUserHashesPasswordAndLogsInWithRealJwt() throws Exception {
        var token=register("USER@example.com");
        assertThat(db.get("user@example.com").passwordHash()).startsWith("$2").isNotEqualTo("SenhaTeste123!");
        assertThat(decoder.decode(token).getSubject()).isEqualTo("user@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@example.com"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"user@example.com\",\"password\":\"SenhaTeste123!\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"user@example.com\",\"password\":\"incorrect\"}")).andExpect(status().isUnauthorized());
    }
    @Test void favoritesAreIsolatedAndIdempotent() throws Exception {
        var a=register("a@example.com"); var b=register("b@example.com");
        for(int i=0;i<2;i++) mvc.perform(put("/api/favorites/1.1.1").header("Authorization","Bearer "+a)
            .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}")).andExpect(status().isOk());
        assertThat(fav).hasSize(1);
        mvc.perform(get("/api/favorites").header("Authorization","Bearer "+b)).andExpect(content().json("[]"));
        mvc.perform(post("/api/favorites/search").header("Authorization","Bearer "+a).contentType(MediaType.APPLICATION_JSON)
            .content("{\"criteria\":[]}")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/favorites/search").header("Authorization","Bearer "+b).contentType(MediaType.APPLICATION_JSON)
            .content("{\"criteria\":[]}")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/favorites/1.1.1").header("Authorization","Bearer "+a)
            .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":false}")).andExpect(status().isOk());
        assertThat(fav).isEmpty();
    }
    @Test void protectsAdminWritesAndRequiresAuthenticationForFavorites() throws Exception {
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
        var user=register("a@example.com");
        mvc.perform(patch("/api/products/p1/images").header("Authorization","Bearer "+user)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        db.put("admin@example.com", new AppUser("admin@example.com","Admin",db.get("a@example.com").passwordHash(),AppUser.Role.ADMIN,Instant.now()));
        var admin=auth.login("admin@example.com","SenhaTeste123!").accessToken();
        mvc.perform(patch("/api/products/missing/images").header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
    @Test void rejectsExpiredAndTamperedTokens() throws Exception {
        var claims=JwtClaimsSet.builder().issuer("precify").subject("a@example.com").issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(3600)).claim("roles",List.of("ADMIN")).build();
        var expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        var token=register("a@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token.substring(0,token.lastIndexOf('.')+1)+"invalid"))
            .andExpect(status().isUnauthorized());
    }
    @Test void allCatalogEndpointsRequireJwtAndCorsStillWorks() throws Exception {
        for (String path : List.of("/api/catalog", "/api/catalog/1.1.1/details", "/api/products", "/api/media/012345678901234567890123"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/catalog/search").contentType(MediaType.APPLICATION_JSON).content("{\"criteria\":[]}")).andExpect(status().isUnauthorized());
        String token = register("reader@example.com");
        mvc.perform(post("/api/catalog/search").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"criteria\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(options("/api/favorites").header("Origin","https://busca-produto-frontend.vercel.app")
            .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization,ngrok-skip-browser-warning"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://busca-produto-frontend.vercel.app"));
    }
    @Test void usersCanOnlyBeCreatedByAdminAndPublicSignupIsDisabled() throws Exception {
        String body = "{\"name\":\"New admin\",\"email\":\"second@example.com\",\"password\":\"SenhaTeste123!\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String user = register("reader@example.com");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + user).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String admin = auth.login("bootstrap@example.com", "SenhaTeste123!").accessToken();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(decoder.decode(auth.login("second@example.com", "SenhaTeste123!").accessToken()).getClaimAsStringList("roles")).containsExactly("ADMIN");
        mvc.perform(get("/api/catalog/1.1.1/details").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andExpect(jsonPath("$.material.materialCode").value("1.1.1"));
    }
    @Test void mediaRejectsNonImagesAndStoresValidPngForAdmin() throws Exception {
        register("a@example.com");
        db.put("admin@example.com", new AppUser("admin@example.com","Admin",db.get("a@example.com").passwordHash(),AppUser.Role.ADMIN,Instant.now()));
        var admin=auth.login("admin@example.com","SenhaTeste123!").accessToken();
        mvc.perform(multipart("/api/media").file("file","<script>alert(1)</script>".getBytes()).header("Authorization","Bearer "+admin))
            .andExpect(status().isBadRequest());
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",out);
        when(files.store(any(java.io.InputStream.class),anyString(),anyString(),any(org.bson.Document.class))).thenReturn(new ObjectId());
        mvc.perform(multipart("/api/media").file("file",out.toByteArray()).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url").exists());
    }
}
