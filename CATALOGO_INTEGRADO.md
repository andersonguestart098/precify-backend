# Catálogo e ofertas

A busca nova usa POST /api/catalog/search?page=0&size=10 com
familyCode, query, criteria e includeAlternatives (mantido no contrato, sem
ranking aproximado nesta rota). Sem filtros retorna os materiais do catálogo,
mesmo que não exista produto comercial.

Os filtros suportados são segmentCode, materialCode, optionCode, state e price.
Preço e estado exigem uma cotação correspondente; todos os filtros de oferta
são avaliados na mesma cotação. Opção sem oferta ainda permite mostrar o
material, mas nunca uma cotação de outra opção.

O catálogo define opções possíveis, não combinações efetivamente vendidas.
Não criamos produtos, preços ou fornecedores fictícios. Preços zero e
fornecedores "a definir" legados não são exibidos como ofertas.

Produtos novos guardam os códigos de material/família/segmento e os códigos de
variação/opção. O servidor valida a associação e determina os nomes pelo
catálogo. Registros antigos permanecem intactos. A busca aceita código
explícito ou correspondência exata e única dos três nomes da hierarquia.
Nomes antigos agrupados, como a antiga categoria genérica de iluminação, não
são migrados automaticamente: precisam de classificação manual.

O importador existente inicializa catalog_materials ao consultar o catálogo
vazio. Não remove products nem limpa dados de QA. Os seeds legados ficam
preservados; registros sem vínculo não aparecem na nova busca.

Publicar backend e frontend desta branch juntos. Não misturar com as versões
anteriores da tela. Executar mvn test com JDK 21. A suíte valida também a
integridade do catálogo compactado (1.648 materiais, 3.327 variações,
1.460 opções).
