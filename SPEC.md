# SPEC.md — SRM Credit Engine

## 1. Premissas adotadas

O enunciado contém ambiguidades propositais. Abaixo, cada uma delas com a premissa
adotada e a justificativa. Os itens marcados **[confirmado pelo negócio]** foram
esclarecidos junto ao stakeholder nesta rodada (ver histórico na seção 2); os
demais são premissas de engenharia assumidas na ausência de resposta do negócio.
As premissas fixadas para os *golden cases* (seção 4.3 do desafio) seguem como
contrato de aferição à parte — não substituem as regras gerais, apenas as
restringem para fins de teste objetivo.

### 1.1 Unidade do prazo na fórmula — **[Pergunta a ser feita ao negócio]**

**Premissa:** o prazo é **sempre expresso em meses inteiros**. Não existe
conversão de dias corridos nem convenção de dia-contagem (30/360, ACT/360,
ACT/365) — recebíveis com vencimento fracionário simplesmente não fazem parte do
domínio do problema nesta versão.

**Porque:** Conforme apresentado o item 4.3 foi apresentada a taxa base e o prazo em meses inteiros. Simplifica a fórmula base (`VP = VF / (1 + i)^n`, com `n` inteiro) e elimina uma classe inteira de decisão
de dia-contagem que exigiria calendário de dias úteis, feriados etc. — escopo que
não está no enunciado.

### 1.2 Origem e valor da taxa base — **[Pergunta a ser feita ao negócio]**

**Premissa:** a taxa base **varia por combinação (tipo de recebível × moeda de
pagamento)**, não é um valor único global. É um parâmetro de configuração
versionado, com data/hora de vigência, gerido no mesmo Currency Engine que
administra o câmbio (`GET/PUT /base-rates?tipo=&moeda=`).

**Porque:** Com base no documento do desafio a taxa base pode diferir por tipo de
recebível e por moeda. Modelo de dados: tabela `base_rates(tipo_recebivel,
moeda, taxa, vigencia_inicio, vigencia_fim)`. A busca da taxa vigente no momento
do cálculo segue a mesma lógica de "última vigência ≤ timestamp" usada para
câmbio, garantindo auditabilidade simétrica entre as duas taxas.

### 1.3 Câmbio usado na liquidação — **[Pergunta a ser feita ao negócio]**

**Premissa:** o câmbio é **travado no momento da aquisição** do ativo (rate
lock), não no momento da liquidação. O valor travado é persistido no registro do
recebível/aquisição e é essa mesma taxa — não a vigente no instante da
liquidação — que é usada no cálculo final e novamente persistida no registro de
liquidação (redundância proposital para auditoria).

**Porque:** Com base no documento do desafio optei explicitamente por eliminar 
a exposição cambial entre aquisição e liquidação, prática comum em operações cross-currency de
FIDC. Isso muda a premissa original (que assumia câmbio vigente na liquidação) e tem implicação 
direta de modelagem: a entidade `receivable` precisa de um campo `locked_fx_rate` (nullable, só se aplicável
 a título cross-currency) preenchido no momento da aquisição/cadastro, não no momento da liquidação.

### 1.4 Política de arredondamento

**Premissa:** **half-even (banker's rounding)**, 2 casas decimais, aplicado
**uma única vez, no valor final** de cada operação (valor presente em BRL e,
quando cross-currency, também no valor final convertido). Nenhum arredondamento
intermediário — a exponenciação e a divisão da fórmula de VP são calculadas em
precisão total (`BigDecimal` com `MathContext` de alta precisão) até o
arredondamento final.

**Porque:** replica a regrade fixada nos golden cases e a generaliza como padrão
do sistema. Half-even evita viés sistemático de arredondamento em grande volume
de liquidações. Não confirmado explicitamente pelo negócio nesta rodada — mantido
como premissa de engenharia por ausência de objeção.

### 1.5 Granularidade da liquidação — **[Pergunta a ser feita ao negócio]**

**Premissa:** o sistema suporta **apenas liquidação total** do recebível. Não há
liquidação parcial nesta versão.

**Porque:** Simplifica a máquina de estados do
recebível para `PENDENTE → LIQUIDADO` (sem estado intermediário de saldo
remanescente) e a idempotência (uma liquidação bem-sucedida esgota o recebível).

### 1.6 Taxa efetiva negativa — **[Pergunta a ser feita ao negócio]**

**Premissa:** se a taxa efetiva (taxa base + spread) resultar **menor que zero**,
a taxa **aplicada** ao cálculo é **zero** (não gera VP maior que o valor de
face). O sistema registra **ambos os valores**: a taxa efetiva real (pode ser
negativa, mantida para fins analíticos/auditoria) e a taxa efetiva aplicada
(sempre ≥ 0, é a que efetivamente entra na fórmula).

**Porque:** Evita que uma taxa base negativa configurada
incorretamente gere deságio negativo (ativo "valorizado" na compra, o que não
faz sentido de negócio). Modelagem: `settlements.effective_rate_raw` e
`settlements.effective_rate_applied` como colunas separadas no registro
imutável de liquidação.

### 1.7 Validação de limites do cedente — **[Pergunta a ser feita ao negócio]**

**Premissa:** **fora de escopo** nesta v1. Não há validação de limite de
exposição ou concentração por cedente.

**Porque:** Simplifica a máquina de estados do recebível e evita complexidade desnecessária na primeira versão. Registrado explicitamente aqui (e em `DECISIONS.md`) para não ser lido como
omissão não intencional.

---

## 2. Histórico de decisões com o negócio

As perguntas abaixo foram levantadas na primeira rodada deste SPEC e já
respondidas pelo negócio. Mantidas aqui como rastro de decisão (útil na defesa
técnica) em vez de removidas.

| # | Pergunta original | Resposta do negócio | Impacto na modelagem |
|---|---|---|---|
| 1 | Existe convenção de dia para prazo fracionário? | Não — prazo é sempre em meses inteiros | Elimina necessidade de calendário de dias úteis; `term` é `INT`, não `DECIMAL` |
| 2 | Taxa base é global ou varia por produto/moeda? | Varia por tipo de recebível **e** moeda | Tabela `base_rates` chaveada por (tipo, moeda), não valor único |
| 3 | Existe rate lock de câmbio na aquisição? | Sim — câmbio trava na aquisição | Campo `locked_fx_rate` no recebível; liquidação não busca câmbio "vigente" |
| 4 | Liquidação pode ser parcial? | Não — apenas total | Máquina de estados simplificada; sem saldo remanescente |
| 5 | O que fazer com taxa efetiva negativa? | Piso em zero; registrar real e aplicada | Duas colunas no registro de liquidação (`raw` e `applied`) |
| 6 | Validar limites/concentração do cedente? | Não nesta v1 | Fora de escopo, documentado em `DECISIONS.md` |

**Nota:** todas as respostas acima valem para a v1 e podem ser revistas em versões futuras — não são 
compromissos definitivos de arquitetura, apenas o contrato assumido para esta entrega.

---

## 3. Decisões de precisão numérica

| Camada | Tipo de dado | Observação |
|---|---|---|
| Banco (MariaDB) | `DECIMAL(18,2)` para valores monetários finais; `DECIMAL(18,8)` para taxas, spreads e câmbio (inclusive `locked_fx_rate`) | Nunca `FLOAT`/`DOUBLE` |
| Banco — auditoria | `settlements.effective_rate_raw` e `settlements.effective_rate_applied` como colunas `DECIMAL(18,8)` distintas | Preserva histórico mesmo quando a taxa real é negativa |
| Aplicação (Java) | `java.math.BigDecimal` em todo o pipeline de cálculo | `RoundingMode.HALF_EVEN` aplicado apenas na conversão final para `DECIMAL(18,2)` |
| Cálculo intermediário | `BigDecimal` com `MathContext(34, HALF_EVEN)` | Evita perda de precisão na exponenciação; `n` (prazo) é sempre inteiro (1.1), simplificando o `pow` |
| Taxa base | Lookup por `(tipo_recebivel, moeda, vigência)`, não constante | Reflete 1.2 |
| Câmbio | Lido uma única vez, na aquisição, e persistido (`locked_fx_rate`) — liquidação apenas reutiliza | Reflete 1.3; elimina dependência de fxService no caminho crítico da liquidação |
| API (JSON) | Valores monetários serializados como **string**, não `number` | Evita perda de precisão em clientes JS/JSON |

**Momento do arredondamento:** apenas no valor final apresentado/persistido por operação (ver 1.4). Nenhum 
arredondamento em passos intermediários.

---

## 4. Critérios de aceite definidos

**Usabilidade**
- Simulação do valor líquido no painel do operador responde em < 300ms
  percebidos.
- Erros de validação de input (ex.: prazo ≤ 0, moeda não suportada) são
  exibidos de forma específica por campo, nunca como erro genérico.

**Segurança**
- Nenhuma query com concatenação de string — 100% via prepared statements /
  ORM parametrizado.
- Endpoint de liquidação exige idempotency key (header) além do ID do
  recebível.
- Nenhuma exceção é engolida silenciosamente; toda falha retorna status HTTP
  semântico (nunca `200 OK` para erro).

**Desempenho**
- Extrato de liquidação com filtro (período, cedente, moeda) responde em
  < 500ms para até 100k registros, via índice composto (cedente, moeda, data).
- Endpoint de liquidação é idempotente sob retry: N requisições idênticas
  (mesma idempotency key) produzem exatamente 1 liquidação — reforçado agora
  pela premissa 1.5 (liquidação sempre total, sem estado intermediário).

---

## 5. Escopo desta versão (v1)

**Fora de escopo, com justificativa (ver também `DECISIONS.md`):**
- Liquidação parcial (1.5).
- Validação de limites/concentração por cedente (1.7).
- Taxa base ou câmbio negociados individualmente por operação fora da tabela
  de configuração (toda a variação já é coberta por tipo × moeda / rate lock).

**Em escopo, mesmo sendo decisões "novas" em relação à primeira leitura do
enunciado:**
- Taxa base multidimensional (tipo × moeda).
- Rate lock de câmbio na aquisição.
- Registro duplo de taxa efetiva (real vs. aplicada).

Todas as decisões desta versão podem ser revisitadas — nada aqui é modelado
como definitivo, apenas como o contrato assumido para esta entrega (ver nota
final da seção 2).
