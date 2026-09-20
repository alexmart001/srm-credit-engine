package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve a SpreadStrategy correta a partir do ReceivableType. Spring injeta
 * automaticamente todos os @Component que implementam SpreadStrategy - novo
 * tipo de recebivel nao exige alterar esta classe, apenas registrar uma nova
 * implementacao (Open/Closed Principle).
 */
@Component
public class SpreadStrategyFactory {

    private final Map<ReceivableType, SpreadStrategy> strategiesByType;

    public SpreadStrategyFactory(List<SpreadStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toMap(SpreadStrategy::getType, Function.identity()));
    }

    public SpreadStrategy resolve(ReceivableType type) {
        SpreadStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            throw new IllegalStateException("Nenhuma SpreadStrategy registrada para o tipo: " + type);
        }
        return strategy;
    }
}
