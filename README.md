# Busca Produto - Backend

API principal do piloto de busca técnica. Os produtos são armazenados no MongoDB com atributos flexíveis por categoria; o Java valida os critérios, elimina incompatíveis e calcula a pontuação explicável.

Cada produto também pode armazenar `imageUrl` e `supplierLogoUrl`. Os arquivos permanecem no Cloudinary e apenas suas URLs são persistidas no MongoDB.

## Stack

- Java 21
- Spring Boot 3
- Spring Data MongoDB
- Bean Validation e Problem Details
- Actuator
- Heroku / Docker

## Arquitetura MVC

O backend está organizado no formato convencional usado em APIs Spring:

- `controller`: recebe e responde as requisições HTTP;
- `service`: concentra regras de negócio e cálculo de compatibilidade;
- `repository`: acessa o MongoDB com Spring Data;
- `model`: representa os documentos da coleção `products`;
- `dto`: contratos de entrada e saída da busca;
- `enums`, `config` e `exception`: tipos e infraestrutura de apoio.

O React é a camada visual da aplicação. No backend REST, os DTOs JSON ocupam o papel de resposta da camada de apresentação; por isso não há páginas Thymeleaf dentro do Java.

## Executar

Requisitos: Java 21 e Maven 3.9+. Para subir um MongoDB local com Docker:

```bash
docker compose up -d
```

Depois, configure as variáveis no terminal ou na IDE. O Spring Boot não carrega `.env` automaticamente; esse arquivo é apenas uma referência.

PowerShell:

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/busca_produto"
$env:APP_SEED_DEMO_DATA="true"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
mvn spring-boot:run
```

Bash:

```bash
export MONGODB_URI="mongodb://localhost:27017/busca_produto"
export APP_SEED_DEMO_DATA="true"
export CORS_ALLOWED_ORIGINS="http://localhost:5173"
mvn spring-boot:run
```

Para usar o MongoDB Atlas, substitua `MONGODB_URI` pela connection string `mongodb+srv://...` em uma variável privada. Nunca publique usuário ou senha no Git. Com `APP_SEED_DEMO_DATA=true`, quatro luminárias são inseridas somente quando a coleção estiver vazia.

Ao iniciar, uma migração idempotente cria `imageUrl` e `supplierLogoUrl` vazios nos produtos antigos. Depois que URLs reais forem informadas, a migração não as sobrescreve nos próximos starts.

Variáveis principais:

- `MONGODB_URI`
- `CORS_ALLOWED_ORIGINS`
- `PORT`
- `APP_SEED_DEMO_DATA`

## Endpoints iniciais

- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `POST /api/search` (legado, retorna lista)
- `POST /api/search/paged?page=0&size=10`
- `GET /actuator/health`

## Exemplo de busca

```json
{
  "category": "Iluminação",
  "query": "luminária comercial",
  "includeAlternatives": true,
  "criteria": [
    {
      "key": "temperature",
      "label": "Temperatura mínima",
      "value": "4000 K",
      "mode": "REQUIRED",
      "operator": "MINIMUM",
      "weight": 40
    },
    {
      "key": "protection",
      "label": "Proteção",
      "value": "IP65",
      "mode": "PREFERRED",
      "operator": "EXACT",
      "weight": 20
    }
  ]
}
```

## Busca paginada

Use `POST /api/search/paged?page=0&size=10` com o mesmo JSON da busca. `page` começa em zero, `size` aceita de 1 a 100 e o ranking completo é calculado antes do recorte da página.

Resposta:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalCompatibleElements": 0,
  "totalAlternativeElements": 0,
  "totalPages": 0,
  "numberOfElements": 0,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

O endpoint não paginado foi mantido temporariamente para não quebrar o frontend atual.

## Fase futura com IA

Um microserviço Python poderá interpretar linguagem natural e devolver os critérios estruturados. A API Java continuará sendo a autoridade para dados, validação, busca e compatibilidade.
