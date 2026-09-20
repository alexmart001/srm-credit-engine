# REVIEW.md — Code Review do Anexo A (endpoint de liquidação)

> Revisão como se eu fosse o revisor deste PR antes do merge de sexta-feira.
> Cada item traz: o problema, o impacto em produção, e a correção proposta.
> Ordenado por severidade — do que bloquearia o merge para o que eu pediria
> como follow-up.

## Resumo executivo

O código do Anexo A tem **dois grupos de problema muito diferentes** que é
importante não misturar na análise:

1. Um grupo de bugs **triviais de detectar mas catastróficos** (injeção de
   SQL, ponto flutuante para dinheiro, taxa base com escala errada por um
   fator de 100).
2. Um grupo de falhas de **design de concorrência e transação** que, juntas,
   explicam exatamente o incidente do Anexo B (liquidações duplicadas) — não
   é coincidência, é causa e efeito.

Nenhum item abaixo é hipotético: dado o código como está, o incidente do
Anexo B **vai acontecer**, é só questão de quando o primeiro retry de rede
ou a primeira corrida de concorrência aparecer em produção.

---

## 1. [BLOQUEIA MERGE] Injeção de SQL em todas as três queries

```ts
const receivable = await db.queryOne(
  `SELECT * FROM receivables WHERE id = ${receivableId}`
);
...
await db.query(
  `INSERT INTO settlements (receivable_id, amount, currency)
   VALUES (${receivableId}, ${finalAmount.toFixed(2)}, '${currency}')`
);
```

**Impacto em produção:** `receivableId` e `currency` vêm direto de
`req.body`, sem sanitização, e são concatenados como string na query. Um
payload como `{"receivableId": "1; DROP TABLE settlements;--"}` é suficiente
para apagar a tabela de liquidações de um FIDC. Isso não é um risco teórico
de segurança — é a porta de entrada mais óbvia possível num sistema
financeiro exposto via API.

**Correção:** prepared statements / query parametrizada em todas as três
operações, sem exceção:
```ts
const receivable = await db.queryOne(
  `SELECT * FROM receivables WHERE id = $1`, [receivableId]
);
```
No nosso backend (Spring Data JPA), isso é garantido por padrão desde que
não se use `nativeQuery` com concatenação manual — vale um item de checklist
de PR explícito para isso (ver seção "Prevenção sistêmica" ao final).

---

## 2. [BLOQUEIA MERGE] Nenhuma idempotência no endpoint

O endpoint não recebe nem verifica nenhuma chave de idempotência. Toda
requisição — inclusive um retry de rede idêntico ou um duplo clique no
painel do operador — é tratada como uma liquidação nova.

**Impacto em produção:** é o requisito explícito do item 4.1.3 do desafio
("a mesma requisição repetida não pode gerar duas liquidações") sendo
violado da forma mais direta possível. Combinado com os itens 3 e 4 abaixo,
é o mecanismo mais provável por trás do incidente do Anexo B.

**Correção:** exigir um header `Idempotency-Key` na requisição, com
constraint `UNIQUE` na coluna correspondente da tabela de liquidações (fonte
de verdade no banco, não só uma checagem de leitura na aplicação — uma
checagem "SELECT primeiro, INSERT depois" sozinha ainda tem corrida). No
`SettlementService` já implementado, a chave é obrigatória
(`SettlementCommand`) e a constraint única resolve a corrida (ver
`V4__create_settlements.sql`).

---

## 3. [BLOQUEIA MERGE] Exceção engolida, sem transação, retorna 200 mesmo em erro

```ts
try {
  await db.query(`INSERT INTO settlements ...`);
  await db.query(`UPDATE receivables SET status = 'SETTLED' ...`);
} catch (e) {
  // se falhar aqui, o insert já rodou, então segue o jogo
}

res.status(200).json({ ok: true, amount: finalAmount.toFixed(2) });
```

Este é o trecho mais grave do arquivo inteiro, e vale entender por que
**exatamente este comentário** ("já rodou, então segue o jogo") é a causa
raiz mais provável do incidente do Anexo B:

- As duas escritas (`INSERT` em `settlements`, `UPDATE` em `receivables`)
  não estão numa transação. Se o `UPDATE` falhar por qualquer motivo
  (deadlock, timeout, conexão caiu) **depois** do `INSERT` ter sido
  commitado, o resultado é: existe um registro de liquidação, mas o
  recebível **continua com status anterior** (nunca vira `SETTLED`).
- A exceção é capturada e descartada — sem log, sem métrica, sem re-throw.
  Ninguém fica sabendo que isso aconteceu.
- A resposta HTTP retorna `200 OK` com `{ok: true}` mesmo quando a segunda
  escrita falhou — do ponto de vista do cliente (mesa de operações, sistema
  de origem), a liquidação foi um sucesso completo.
- Como o recebível nunca é marcado como liquidado, **nada impede uma
  segunda requisição de liquidar o mesmo recebível de novo** (não há sequer
  uma checagem de status antes de calcular `presentValue`) — gerando um
  **segundo** registro em `settlements` para o mesmo recebível.

Isso bate exatamente com a descrição do Anexo B: "três cedentes receberam a
mesma liquidação duas vezes". Não é preciso nem concorrência real para
reproduzir isso — uma falha transitória de rede/banco entre as duas
escritas, sozinha, já é suficiente.

**Impacto em produção:** double-pay para o cedente (dinheiro saindo do caixa
do fundo duas vezes por um único ativo), inconsistência entre o extrato de
liquidações e o status dos recebíveis, e — o mais perigoso — **nenhum sinal
de erro em lugar nenhum** até o cedente ligar reclamando.

**Correção:**
- As duas escritas devem ocorrer na mesma transação ACID (`@Transactional`
  no Spring) — se qualquer uma falhar, as duas são revertidas.
- Nenhuma exceção pode ser engolida sem, no mínimo, log estruturado; o
  padrão correto aqui é deixar a exceção propagar e o `GlobalExceptionHandler`
  traduzir para um status HTTP semântico (nunca `200 OK`).
- Implementado no `SettlementService`: `@Transactional` no método `settle()`,
  sem `try/catch` genérico — os únicos `catch` existentes são específicos
  (`DataIntegrityViolationException` para corrida de idempotência,
  `ObjectOptimisticLockingFailureException` para conflito de concorrência) e
  cada um re-lança uma exceção de domínio explícita, nunca esconde a falha.

---

## 4. [BLOQUEIA MERGE] Nenhuma verificação de status nem controle de concorrência

O código nunca verifica se `receivable.status` já é `SETTLED` antes de
calcular e persistir uma liquidação, e não usa nenhum mecanismo de lock
(nem otimista, nem pessimista).

**Impacto em produção:** distinto do item 3 (que é sobre retry sequencial
após falha parcial), este é sobre **concorrência real**: duas requisições
simultâneas para o mesmo `receivableId` (duplo clique real, dois operadores
diferentes, um retry de timeout que chega ao mesmo tempo que a requisição
original ainda em voo) vão ambas ler o recebível como "liquidável", ambas
calcular `presentValue`, e ambas inserir uma liquidação — sem que nenhuma
das duas veja um erro. É uma classe de bug clássica (race condition /
TOCTOU) que só aparece sob carga, o que a torna especialmente perigosa: passa
despercebida em teste manual e em ambiente de baixo tráfego.

**Correção:** optimistic locking (`@Version`) no recebível, com o `UPDATE`
de status protegido por esse controle de versão — se duas transações
concorrentes tentarem marcar o mesmo recebível como liquidado, a segunda
recebe um conflito de versão e falha de forma segura, em vez de silenciosamente
duplicar. Implementado em `Receivable.version` + `SettlementService` (que
traduz o conflito para `ConcurrentSettlementException`, HTTP 409) e coberto
por um teste de concorrência real com duas threads
(`SettlementServiceConcurrencyIT`).

---

## 5. [SEVERO] Ponto flutuante binário para valores monetários

```ts
const presentValue =
  receivable.face_value / Math.pow(1 + BASE_RATE + spread, receivable.term);
```

Toda a aritmética usa `number` do JavaScript (IEEE-754 binário), incluindo a
divisão e a exponenciação. `.toFixed(2)` sobre um float não é uma operação
de arredondamento decimal correta — é conhecida por produzir resultados
inconsistentes em casos de borda (ex.: `(1.005).toFixed(2)` retorna `"1.00"`,
não `"1.01"`, porque `1.005` não é representável exatamente em binário).

**Impacto em produção:** erros de centavos que parecem aleatórios, difíceis
de reproduzir (dependem dos valores específicos envolvidos), e que se
acumulam em volume — exatamente o tipo de erro que o próprio desafio
classifica como eliminatório a partir do nível pleno (item 12).

**Correção:** `BigDecimal` (ou equivalente decimal) em toda a cadeia de
cálculo, com arredondamento explícito e determinístico (half-even) aplicado
uma única vez, no resultado final — implementado em `PricingEngine`.

---

## 6. [SEVERO] Taxa base e spreads com escala numérica errada

```ts
const BASE_RATE = 1.0; // taxa base mensal
...
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
```

O enunciado define taxa base de referência em **1,00% a.m.** (isto é,
`0.01`) e spreads de **1,5% a.m.** e **2,5% a.m.** (`0.015` e `0.025`). O
código usa `1.0`, `1.5` e `2.5` diretamente — ou seja, **100%, 150% e 250%
ao mês**. O código roda sem erro nenhum e devolve um número plausível à
primeira vista (é só um valor bem menor do que deveria), o que o torna mais
perigoso que um erro que quebra: ele passa despercebido em teste manual
rápido, porque "parece" estar funcionando.

**Impacto em produção:** com `i = 1.0 + 1.5 = 2.5` (250% a.m.), um recebível
de R$100.000 em 3 meses seria descontado para menos de R$3.000 — um deságio
absurdo, que sangraria o caixa do fundo ou (se fosse ao contrário) pagaria
o cedente uma fração do que deveria. Este é exatamente o tipo de erro
"sutilmente incorreto" que o item 7.2 do desafio pede para identificar: o
código compila, roda, e produz um número — só que financeiramente
catastrófico.

**Correção:** taxa base e spreads como frações decimais (`0.01`, `0.015`,
`0.025`), validados contra os golden cases do item 4.3 do desafio como teste
de regressão obrigatório — exatamente o que `PricingEngineGoldenCasesTest`
faz.

---

## 7. [MODERADO-SEVERO] Spread resolvido por comparação de string mágica, sem Strategy

```ts
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
```

Dois problemas numa linha só: (1) o valor de comparação `"DUPLICATA"`
provavelmente não bate com o valor real de domínio (o enunciado usa
"Duplicata Mercantil" / "Cheque Pré-datado"); e (2) **qualquer** tipo que
não seja exatamente essa string cai no `else` e recebe o spread de cheque
pré-datado — silenciosamente, sem erro.

**Impacto em produção:** um novo tipo de recebível adicionado ao sistema (o
tipo de mudança que o desafio explicitamente testa na defesa ao vivo, item
9.2) herda o spread errado por padrão, em vez de falhar de forma visível.
Isso também viola diretamente o requisito do item 4.1.2 do desafio
("desacople a regra do cálculo com o padrão Strategy") — o código do Anexo A
não tem Strategy nenhuma, tem um `if/else` codificado que precisa ser achado
e editado manualmente a cada novo tipo.

**Correção:** Strategy pattern (`SpreadStrategy` + `SpreadStrategyFactory`),
onde um tipo sem estratégia registrada lança exceção explícita em vez de
herdar um valor por acaso — implementado no `PricingEngine`.

---

## 8. [MODERADO] Moeda de liquidação como parâmetro livre da requisição

```ts
const { receivableId, currency } = req.body;
...
if (currency === "USD") { ... }
```

A moeda usada na liquidação vem do corpo da requisição, não é uma
propriedade inerente ao recebível. Isso permite (por erro do cliente ou por
uso malicioso) liquidar o mesmo recebível ora em BRL, ora em USD, dependendo
do que o chamador decidir mandar naquela requisição específica — quando, na
realidade, a moeda de pagamento é uma decisão tomada na aquisição do ativo,
não algo renegociável a cada chamada de liquidação.

**Impacto em produção:** falha de semântica financeira (exatamente a
categoria que a rubrica do desafio pede para revisores sênior/staff
identificarem) — a moeda de liquidação deveria ser uma consequência do
estado do recebível, não uma entrada arbitrária e não validada do request.

**Correção:** `paymentCurrency` é definido na aquisição do recebível (nosso
`ReceivableService.acquire`) e a liquidação (`SettlementService`) sempre usa
`receivable.getPaymentCurrency()` — o endpoint de liquidação não aceita mais
esse parâmetro.

---

## 9. [MODERADO] Ausência de validação de entrada

Nenhuma validação de `receivableId` (existe? é um número válido?) ou
`currency` (é um valor aceito?). Se `receivable` vier `undefined` do banco
(id inexistente), a linha seguinte (`receivable.type === ...`) lança um
`TypeError` não tratado — fora do escopo do `try/catch` existente (que só
envolve as duas escritas finais).

**Impacto em produção:** dependendo do middleware de erro do Express, isso
vira um 500 genérico com stack trace potencialmente vazado ao cliente, ou,
em casos piores, uma promise rejeitada sem handler que pode derrubar o
processo Node inteiro.

**Correção:** validação de bean no DTO de entrada (`@Valid` +
`jakarta.validation`) e um `GlobalExceptionHandler` que garante status HTTP
semântico e corpo de erro consistente para todo caso não tratado.

---

## 10. [MODERADO] Nenhum tratamento de falha na integração de câmbio

```ts
const rate = await fxService.getLatestRate("USD");
```

Sem timeout, sem retry, sem fallback, sem circuit breaker. Se o provedor de
câmbio cair no meio de uma liquidação, a exceção não tratada interrompe a
requisição de forma abrupta (e, combinada com o item 3, poderia inclusive
deixar o recebível em estado inconsistente dependendo de onde exatamente a
falha ocorre em relação às escritas).

**Impacto em produção:** indisponibilidade de um serviço terceiro (câmbio)
vira indisponibilidade do fluxo de liquidação inteiro, inclusive para
liquidações em BRL que nem deveriam depender dele.

**Correção:** timeout curto + retry com backoff, e um circuit breaker que
falhe rápido e de forma explícita quando o provedor está fora do ar — item
de nível sênior do próprio desafio (seção 6).

---

## 11. [MODERADO] Auditoria incompleta

O schema implícito (`INSERT INTO settlements (receivable_id, amount,
currency)`) não registra taxa base, spread, taxa efetiva nem taxa de câmbio
usadas no cálculo — só o resultado final.

**Impacto em produção:** viola o requisito de auditabilidade do item 4.1.4
do desafio ("cada liquidação gera registro imutável com valores, taxa de
câmbio efetivamente usada e timestamps"). Na prática, isso significa que
investigar um incidente como o do Anexo B fica muito mais difícil: não dá
para reconstruir *por que* aquele valor específico foi calculado, só *qual*
foi o valor.

**Correção:** `Settlement` grava `base_rate_used`, `spread_used`,
`effective_rate_raw`, `effective_rate_applied` e `fx_rate_used`, além do
valor final — nunca é possível recalcular retroativamente o "porquê" sem
essas colunas.

---

## 12. [BAIXO] Zero observabilidade

Nenhum log, nem no caminho feliz nem no `catch`. Sem uma métrica sequer de
liquidações processadas.

**Impacto em produção:** o time só descobre um problema como o do Anexo B
quando o cliente externo (cedente) reclama — não existe nenhum sinal
interno que dispararia antes disso.

**Correção:** logs estruturados nos pontos de decisão (idempotência
resolvida via replay, conflito de concorrência detectado, liquidação
concluída) e métricas de negócio básicas (liquidações/min, taxa de conflito
de concorrência) — item de nível sênior do desafio (seção 6).

---

## Prevenção sistêmica (o que eu mudaria no processo, não só no código)

Como exercício de "o que eu levaria desta revisão para o time", os pontos
1, 3 e 4 acima não deveriam depender de um revisor humano pegar num PR de
sexta-feira à tarde:

- **Lint/análise estática bloqueando merge** para concatenação de string em
  chamadas de query (regra facilmente detectável automaticamente — não
  precisa de revisão manual para pegar injeção de SQL óbvia como esta).
- **Checklist de PR obrigatório** para qualquer endpoint que escreve em mais
  de uma tabela: "as escritas estão na mesma transação? o que acontece se a
  segunda falhar depois da primeira ter sido commitada?" — pergunta
  simples, mas é exatamente a que este PR não teria passado.
- **Golden cases como gate de CI**, não como validação manual — um teste
  automatizado teria pego o erro de escala da taxa base (item 6) no
  primeiro `git push`, sem depender de ninguém perceber "esse número parece
  pequeno demais" a olho.
