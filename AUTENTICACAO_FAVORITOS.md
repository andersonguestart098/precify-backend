# Login, favoritos e imagens

## Ativar no IntelliJ
Atualize a pasta buscaProduto-backend-integrado com git fetch origin e git merge --ff-only origin/main.
Em Run > Edit Configurations > Environment variables, mantenha MONGODB_URI e acrescente:
- JWT_SECRET: chave aleatória de pelo menos 32 bytes em Base64. Não comitar nem compartilhar.
- ADMIN_EMAIL: e-mail do primeiro administrador (ainda não cadastrado).
- ADMIN_PASSWORD: senha particular, pelo menos 8 caracteres e até 72 bytes UTF-8.
- ADMIN_NAME: nome exibido.

Gere a chave no PowerShell:
```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$jwtRng.Dispose()
```

Reinicie o backend com Java 21. JWT_SECRET é obrigatória; sem ela o servidor não inicia.
Não troque a chave a cada reinício: a troca invalida os tokens existentes.
O administrador é criado somente se o e-mail não existir. O bootstrap não promove contas USER já cadastradas nem redefine senhas.
Depois da primeira inicialização bem-sucedida, ADMIN_PASSWORD pode ser removida das variáveis; mantenha JWT_SECRET.

## Comportamento
- Busca pública; login e cadastro em modal próprio. Cadastro público sempre cria USER.
- JWT HS256 assinado e validado pelo Spring Security, expiração de uma hora, senha BCrypt.
- Token fica no sessionStorage desta aba. Sair remove o token do navegador. Tokens já emitidos permanecem válidos até expirar.
- Roles USER/ADMIN controladas pelo servidor. ADMIN cadastra produtos e envia/edita imagens.
- Favorito identifica o material do card por materialCode e pertence ao usuário autenticado, sem userId recebido do navegador.
- Estrela cinza inicialmente; amarela após confirmação do servidor. Meus favoritos filtra antes da paginação.
- Ofertas válidas têm prioridade, seguidas de imagens, dados da oferta e variações com opções. O código desempata.
- Não inventa cotações nem vincula automaticamente produtos legados ambíguos.

## Fotos
Entre como ADMIN e clique em Editar fotos em uma oferta. Selecione a foto do produto e a logo do fornecedor, depois Salvar imagens.
O cadastro de produto também aceita upload das duas imagens.
Aceita PNG/JPEG até 5 MB e 16 megapixels; o servidor valida, recodifica e salva em GridFS no MongoDB.
As imagens são públicas, URLs relativas /api/media/id continuam válidas quando muda o domínio da API.
Sem imagem, aparece um ícone. Não foram adicionadas fotos ou logos fictícias.
Os cards sem oferta comercial continuam sem foto própria: envie a imagem em um produto vinculado ao material.

## Endpoints
POST /api/auth/register, POST /api/auth/login; GET /api/auth/me.
GET /api/favorites; PUT /api/favorites/{materialCode} com {"favorite":true/false}.
POST /api/favorites/search usa os mesmos filtros e paginação da busca pública.
POST /api/media (multipart file, ADMIN); GET /api/media/{id} público.
PATCH /api/products/{id}/images (ADMIN) aceita imageUrl/supplierLogoUrl do upload.
Campos omitidos preservam as imagens existentes; string vazia remove.

## Validação
Testes HTTP com a cadeia real do Spring Security e repositórios simulados verificam cadastro USER,
hash, login, JWT inválido/expirado, permissão ADMIN, isolamento e idempotência dos favoritos,
CORS e upload. Testes de busca verificam ordenação e favoritos antes da paginação.
Não acessam o MongoDB de produção.
