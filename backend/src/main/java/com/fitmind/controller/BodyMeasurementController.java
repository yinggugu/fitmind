package com.fitmind.controller;

import com.fitmind.common.PageResult;
import com.fitmind.common.Result;
import com.fitmind.dto.BodyMeasurementRequest;
import com.fitmind.entity.BodyMeasurement;
import com.fitmind.service.BodyMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@RequestMapping("/api/body-measurements")
@RequiredArgsConstructor
public class BodyMeasurementController {
    private final BodyMeasurementService service;

    @GetMapping("/latest")
    public Result<BodyMeasurement> latest() {
        return Result.ok(service.latest());
    }

    @GetMapping
    public Result<PageResult<BodyMeasurement>> page(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(service.page(page, size));
    }

    @PostMapping
    public Result<BodyMeasurement> save(@Valid @RequestBody BodyMeasurementRequest request) {
        return Result.ok(service.save(request));
    }

    @PutMapping("/{id}")
    public Result<BodyMeasurement> update(@PathVariable long id,
                                          @Valid @RequestBody BodyMeasurementRequest request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok();
    }
}
