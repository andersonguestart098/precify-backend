# Busca Produto - Backend

API principal do piloto de busca técnica. Os produtos são armazenados no MongoDB com atributos flexíveis por categoria; o Java valida os critérios, elimina incompatíveis e calcula a pontuação explicável.

## Stack

- Java 21
- Spring Boot 3
- Spring Data MongoDB
- Bean Validation e Problem Details
- Actuator
- Heroku / Docker

## Executar

Requisitos: Java 21, Maven 3.9+ e MongoDB.

```bash
cp .env.example .env
mvn spring-boot:run
```

Variáveis principais:

- `MONGODB_URI`
- `CORS_ALLOWED_ORIGINS`
- `PORT`

## Endpoints iniciais

- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `POST /api/search`
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

## Fase futura com IA

Um microserviço Python poderá interpretar linguagem natural e devolver os critérios estruturados. A API Java continuará sendo a autoridade para dados, validação, busca e compatibilidade.
