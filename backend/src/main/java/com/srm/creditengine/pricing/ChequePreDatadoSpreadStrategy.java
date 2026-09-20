package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class ChequePreDatadoSpreadStrategy implements SpreadStrategy {

    @Override
    public ReceivableType getType() {
        return ReceivableType.CHEQUE_PRE_DATADO;
    }

    @Override
    public BigDecimal getMonthlySpread() {
        return ReceivableType.CHEQUE_PRE_DATADO.getMonthlySpread();
    }
}
