package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import java.math.BigDecimal;

/**
 * Strategy (item 4.1.2 do desafio): desacopla a regra de spread por tipo
 * de recebivel do motor de calculo generico (PricingEngine). Adicionar um
 * novo tipo de recebivel = adicionar uma nova implementacao, sem tocar no
 * motor - e' exatamente o tipo de mudanca que costuma ser pedida na
 * "mudanca ao vivo" da defesa tecnica.
 */
public interface SpreadStrategy {

    ReceivableType getType();

    BigDecimal getMonthlySpread();
}
