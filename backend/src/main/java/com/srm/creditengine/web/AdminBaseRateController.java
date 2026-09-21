package com.srm.creditengine.web;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.service.BaseRateAdminService;
import com.srm.creditengine.web.dto.BaseRateResponse;
import com.srm.creditengine.web.dto.PublishBaseRateRequest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Item 4.1.1 do desafio: endpoint de atualizacao manual de taxa base. */
@RestController
@RequestMapping("/admin/base-rates")
public class AdminBaseRateController {

    private final BaseRateAdminService baseRateAdminService;

    public AdminBaseRateController(BaseRateAdminService baseRateAdminService) {
        this.baseRateAdminService = baseRateAdminService;
    }

    @PostMapping
    public BaseRateResponse publish(@Valid @RequestBody PublishBaseRateRequest request) {
        BaseRate baseRate = baseRateAdminService.publish(request.receivableType(), request.currency(), request.rate());
        return BaseRateResponse.from(baseRate);
    }
}
