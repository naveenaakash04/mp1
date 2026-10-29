package com.smartparking.controller;

import com.smartparking.dto.PermitUpdateRequest;
import com.smartparking.dto.VehicleRequest;
import com.smartparking.dto.VehicleResponse;
import com.smartparking.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> list(@RequestParam(required = false) String q) {
        return vehicleService.list(q);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable Long id) {
        return vehicleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse register(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.register(request);
    }

    @PatchMapping("/{id}/permit")
    public VehicleResponse updatePermit(@PathVariable Long id,
                                        @Valid @RequestBody PermitUpdateRequest request) {
        return vehicleService.updatePermit(id, request.accessibilityPermit());
    }
}
