# Produtos e SKUs (níveis 4 e 5 do catálogo)

Hierarquia completa: **Segmento → Família → Material → Produto → SKU**.

| Nível | Coleção | Exemplo de código |
|---|---|---|
| Material (3) | `catalog_materials` | `16.6.5` |
| Produto (4) | `catalog_products` (`_id`) | `16.6.5.P2001` |
| SKU (5) | `catalog_products.skus[]` (embutido) | `16.6.5.P2001.S0001` |

`catalog_products` guarda **produtos documentados** (identidade, apresentação e valores técnicos
com fonte). Não há preço, estoque nem fornecedor aqui: cotações continuam em `products`
(`Product`/`Quote`), que não foi alterado.

## Origem dos dados

Planilha do cliente `PRICO_Compilado_Total_Final_Compatibilizado_20261006.xlsx`. Conforme a aba
`29_DIRETRIZES_IMPLANTACAO`, entra **somente `37_CATALOGO_APTO` com `PROTOCOLO PARA INSERÇÃO = OK`**.
O script também confere os controles do próprio cliente: `REGISTRO ATUAL = REGISTRO VALIDADO` e
o registro com prefixo LEN igual às células (detecta planilha editada sem recálculo).

Resultado do lote `PRICO_20261006_69814e49`: **9.237 produtos, 10.513 SKUs e 12.193 valores**,
sem nenhuma linha excluída.

Ficam fora desta carga (rastreáveis na planilha): 4.275 produtos / 4.556 SKUs B/C bloqueados,
`14_APLICABILIDADE`, `21_EVIDENCIAS`, `24_OFERTAS` (links de canal sem preço), `23`, `26` e `27`
(auditoria/correções).

## Catálogo v2 (pré-requisito dos produtos)

O cliente trabalha com o contrato de variações "Base Mestra V6", mais novo que o catálogo do banco:

| | Banco (v1) | Cliente (v2) |
|---|---|---|
| Materiais | 1.648 | 1.839 (+202 novos, 47 famílias novas) |
| Variações | 3.327 | 4.165 (+868 novas, 1.337 renomeadas) |

Sem o v2, 1.728 SKUs ficariam órfãos. O arquivo `prico_variacoes.ndjson.gz.b64` agora traz o
catálogo v2 mesclado:

- nomes de material, família e variação do cliente; nomes de segmento do banco;
- **opções predefinidas preservadas** do v1 (o contrato do cliente não tem lista de opções);
- 11 materiais e 30 variações que só existem no banco são mantidos; as variações recebem
  `contractStatus = FORA_DO_CONTRATO` (nada é apagado);
- campos novos em `variations[]`: `format` (`NUMERO`, `TEXTO`, `DIMENSOES`, `INTEIRO`),
  `acceptedUnits`, `identityRule`, `contractStatus`.

## Mapeamento planilha → MongoDB

### `catalog_products` (um documento por produto)

| Planilha | Campo | Tratamento |
|---|---|---|
| 37 · COD PRODUTO | `_id` (`productCode`) | `P001` → `P0001` (63 produtos); original em `sourceCode` |
| 37 · COD MATERIAL | `materialCode`, `familyCode`, `segmentCode` | validado contra `catalog_materials` |
| 37 · PRODUTO | `name` | espaços normalizados |
| 37 · MARCA | `brand` | grafia canônica (ex.: `VONDER`/`Vonder` → mais frequente) |
| 37 · FABRICANTE | `manufacturer` | idem |
| 37 · LINHA/MODELO | `model` | |
| 37 · FONTE PRODUTO | `sourceUrl` | |
| 37 · COBERTURA DOCUMENTAL | `documentalCoverage` | `COMPLETA`, `PARCIAL`, `INTEGRAL NAS FONTES CONSULTADAS`… |
| 37 · ORIGEM UNIÃO | `origin.union` | |
| 10 · ARQUIVO ORIGEM / LINHA ORIGEM | `origin.file`, `origin.row` | rastreio até a planilha de origem |
| 10 · DATA CONSULTA | `consultedAt` | ISO `yyyy-MM-dd` (serial do Excel convertido) |
| 10 · FINGERPRINT | `fingerprint` | chave de identidade do cliente |
| — | `active`, `contentHash`, `importBatch`, `importedAt`, `withdrawnAt` | controle da sincronização |
| — | `imageUrl`, `createdAt` | da aplicação; **nunca sobrescritos** pela carga |

### `skus[]` (dentro do produto)

| Planilha | Campo | Tratamento |
|---|---|---|
| 37 · COD SKU PRICO | `skuCode` | `S001` → `S0001`; 57 códigos soltos (`S00001`) viram `<produto>.S0001`; original em `sourceCode` |
| 37 · SKU FABRICANTE | `manufacturerSku` | **novo**; não é único (o mesmo código aparece em SKUs de cor diferente) |
| 37 · GTIN/EAN | `gtin` | **novo**; dígito verificador validado (446 preenchidos, todos válidos) |
| 37 · UNIDADE COMERCIAL | `commercialUnit` + `commercialUnitDetail` | 245 grafias → unidade base (`un`, `peça`, `caixa`…); texto original quando traz conteúdo ("Caixa com 16 placas") |
| 37 · APRESENTAÇÃO | `presentation` | |
| 37 · FONTE SKU | `sourceUrl` | |
| 11 · DATA CONSULTA | `consultedAt` | |
| 37 · MODALIDADE COMERCIAL | `commercialMode` | `MASSA`/`VOLUME` (raro) |
| 37 · CHAVE IDENTIDADE COMERCIAL | `commercialIdentityKey` | mesmo item físico em dois materiais (vínculo comprovado na aba 38) |
| 37 · CONTEÚDOS VALIDADOS | `validatedContents` | lista de códigos de variação |
| 37 · PENDÊNCIAS DE CAMPOS | `pendingVariationCodes` | variações exigidas ainda sem valor documentado |
| 37 · VARIAÇÕES JSON | `values[]` | ver abaixo |

### `skus[].values[]`

| JSON do cliente | Campo | Tratamento |
|---|---|---|
| `codigo_variacao` | `variationCode` | precisa pertencer ao material |
| `atributo` | `attribute` | nome do contrato v2 |
| `valor` | `value` | texto de exibição (decimal pt-BR: `0,75`) |
| `valor` numérico | `numericValue` | para filtros/ordenação |
| `valor` em `DIMENSOES` | `dimensions` | `"122 x 14 x 1,5"` → `[122, 14, 1.5]` |
| `unidade` | `unit` | sempre dentro de `acceptedUnits` (conferido) |
| — | `optionCode` | ligado à opção do catálogo quando o valor bate exatamente (618 de 805 possíveis) |
| `qualificador` | `qualifier` | |
| `valor_original_documentado` | `originalValue` | |
| `fonte` | `sourceUrl` | |

Não são gravados: `PROTOCOLO PARA INSERÇÃO` (filtro), `OBSERVAÇÃO` (constante, vai no manifesto),
`REGISTRO VALIDADO/ATUAL`, `CONTROLE CATÁLOGO`, `PENDÊNCIAS DA AUDITORIA` (usados só na conferência).

### Índices

`catalog_products`: `skus.skuCode` (único), `skus.gtin`, `skus.manufacturerSku`, `materialCode + active`,
`brand`. Criados pelo Spring (`auto-index-creation: true`).

## Como subir

1. Rodar `mvn test` (inclui `CatalogProductBundleTest`, que valida os 9.237 produtos contra o catálogo).
2. Fazer backup: `mongodump --uri "$MONGODB_URI" --collection catalog_materials`.
3. Publicar o backend e, com token ADMIN:
   - `POST /api/admin/catalog/sync?dryRun=true` → plano e `errors` (precisa vir `[]`). Não grava nada.
   - `POST /api/admin/catalog/sync` → `202`, roda em background (o Heroku corta requisições com mais de 30 s).
   - `GET /api/admin/catalog/sync` → histórico (`RUNNING`, `SUCCESS` ou `FAILED` com erros).

   Alternativa: `APP_CATALOG_SYNC_ON_STARTUP=true` aplica o lote uma vez no boot, também em
   background, e ignora lotes já aplicados (mesmo hash).

Primeira sincronização em um banco com o catálogo v1: **202 materiais inseridos, 1.648 atualizados
e 9.237 produtos inseridos**. Rodar de novo não regrava nada.

A sincronização valida tudo antes da primeira escrita, nunca apaga, preserva `imageUrl`/`supplierLogoUrl`
dos materiais e marca com `active=false` os produtos que saírem de uma entrega futura.

## Busca (tela principal)

`POST /api/catalog/products/search?page=0&size=10` com `query`, `segmentCode`, `familyCode`,
`materialCode`, `brand` e `onlyFavorites` (todos opcionais). Mesma lógica da busca de materiais:
filtros exatos por código, todos os termos precisam aparecer (sem acento/caixa) e o resultado é
ordenado por relevância, completude do cadastro e código natural. Relevância: código exato (produto,
SKU, GTIN, SKU do fabricante) primeiro; depois termo no nome/marca/modelo/códigos e termo na
hierarquia (material, família, segmento), para que "porcelanato" traga porcelanatos antes de um
disco de corte que só cita porcelanato no nome. Medidas são normalizadas (`60 x 60` = `60x60`).

A resposta traz `brands` (ignora o filtro de marca) e `materialCounts` (ignora o filtro de material),
usados pelos filtros Marca e Material do frontend. Os produtos ativos ficam em memória (~9 mil,
~40 MB), recarregados a cada 5 minutos em background e invalidados ao fim de cada sincronização.

Favoritos de produto: `PUT /api/favorites/workspace/PRODUCT/{productCode}`.

## Leitura

- `GET /api/catalog/{materialCode}/products` → produtos ativos do material
- `GET /api/catalog/products/{productCode}`
- `GET /api/catalog/skus?code=…` | `?gtin=…` | `?manufacturerSku=…`

## Próxima entrega do cliente

```bash
pip install python-calamine openpyxl
python tools/prico/consolidar_prico.py caminho/PRICO_nova.xlsx --repo . --report relatorio.xlsx
```

O script regrava os três arquivos de `src/main/resources/catalog/` e gera o relatório de revisão
(materiais/variações novos e renomeados, códigos reestruturados, grafias, unidades, valores fora
das opções, SKUs sem valores). A saída é determinística: a mesma planilha gera os mesmos arquivos.
