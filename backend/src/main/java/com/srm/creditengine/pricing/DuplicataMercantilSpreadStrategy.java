package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DuplicataMercantilSpreadStrategy implements SpreadStrategy {

    @Override
    public ReceivableType getType() {
        return ReceivableType.DUPLICATA_MERCANTIL;
    }

    @Override
    public BigDecimal getMonthlySpread() {
        return ReceivableType.DUPLICATA_MERCANTIL.getMonthlySpread();
    }
}
