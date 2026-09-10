# Acesso autenticado e páginas PRECIFY

## Atualizar
Atualize o backend com git fetch origin e git merge --ff-only origin/main e reinicie no IntelliJ com Java 21.
Mantenha JWT_SECRET e MONGODB_URI nas variáveis de execução. A configuração anterior continua válida.
A Vercel publica o frontend a partir da main. O backend local precisa estar atualizado antes de usar as novas páginas.

## Primeiro administrador
Se ainda não houver administrador, configure ADMIN_EMAIL, ADMIN_PASSWORD e ADMIN_NAME no IntelliJ.
O bootstrap cria uma conta ADMIN somente se o e-mail ainda não existir. Não promove usuários existentes nem redefine senhas.
A senha deve ter ao menos 8 caracteres e no máximo 72 bytes UTF-8.
Não há senha padrão nem cadastro público.

JWT_SECRET deve conter pelo menos 32 bytes aleatórios em Base64. Para gerar uma chave no PowerShell:
```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$jwtRng.Dispose()
```
Guarde a chave nas variáveis, nunca no repositório. Não troque a cada reinício: a troca invalida os tokens existentes.

## Login e sessão
- /login é a entrada para qualquer pessoa sem sessão validada. A página usa a identidade verde PRECIFY.
- A senha é validada com BCrypt; o JWT HS256 é validado pelo Spring Security com emissor e expiração.
- Token tem validade de uma hora e fica em sessionStorage da aba. A sessão é validada em /api/auth/me ao recarregar.
- Um 401 ou o vencimento retorna ao login. A navegação privada não é montada antes de validar a sessão.
- Lembrar meu e-mail armazena somente o e-mail, nunca a senha.
- Sair remove a sessão local. JWT já emitido permanece válido até expirar.
- Esqueci minha senha orienta contato com administrador; não há envio automático de e-mail de recuperação.

## Usuários
O menu Usuários abre /usuarios, disponível apenas para ADMIN.
O formulário cria contas e inicia com o perfil Administrador selecionado.
Você pode criar as duas contas ADMIN por ali. USER continua disponível como perfil de consulta.
GET e POST /api/users exigem ADMIN. Nenhuma resposta retorna hash ou senha.
POST /api/auth/register foi removido.

## Endpoints protegidos
Apenas POST /api/auth/login, preflight OPTIONS, /error e /actuator/health dispensam JWT.
Consultas do catálogo, detalhes, produtos, favoritos e imagens exigem autenticação.
Cadastro de produtos e upload/edição de imagens exigem ADMIN.
Imagens internas são carregadas pelo frontend com Authorization, convertidas em URL de blob e liberadas da memória ao desmontar.
A resposta das imagens usa Cache-Control: no-store.

## Busca e detalhes
- /busca mostra cards resumidos com foto, favorito, cotação e Ver detalhes.
- Todos e Meus favoritos ficam dentro de Busca avançada, no campo Exibir.
- Filtros e paginação ficam na URL e são preservados pelo link Voltar à busca.
- /produtos/:materialCode funciona por link direto e após recarregar, com sessão válida.
- GET /api/catalog/{code}/details retorna o material e os produtos vinculados por código ou hierarquia exata e única.
- A página mostra descrição, atributos, variações, cotações, fornecedor, imagens e datas.
- Botões de variação selecionam uma opção por vez e mostram somente as cotações vinculadas a ela.
- O modelo atual armazena cada cotação em uma variação: não se inventam combinações entre variações independentes.
- Uma opção sem oferta mostra Cotação pendente, sem reutilizar preço ou fornecedor de outra opção.
- ADMIN pode editar fotos na página do produto.
- Favoritos pertencem ao usuário autenticado e identificam o material do card.

## Fotos
PNG/JPEG até 5 MB e 16 megapixels, validados e recodificados no servidor, armazenados em GridFS.
Cadastro do produto e Editar fotos permitem enviar foto e logo do fornecedor.
Imagens ausentes exibem ícone. Os dados de produtos antigos permanecem preservados.

## Verificação
Backend: mvn test (Java 21).
Frontend: npm ci, npm test, npm run build, npm run lint.
Testes da interface usam DOM simulado e respostas de API controladas; testes HTTP usam a cadeia real de segurança e repositórios simulados.
