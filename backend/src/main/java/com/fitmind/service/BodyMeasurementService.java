package com.fitmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.PageResult;
import com.fitmind.dto.BodyMeasurementRequest;
import com.fitmind.entity.BodyMeasurement;
import com.fitmind.exception.BusinessException;
import com.fitmind.mapper.BodyMeasurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BodyMeasurementService {
    private final BodyMeasurementMapper mapper;

    public BodyMeasurement latest() {
        BodyMeasurement record = mapper.selectOne(base()
            .orderByDesc(BodyMeasurement::getRecordDate)
            .orderByDesc(BodyMeasurement::getId)
            .last("LIMIT 1"));
        if (record == null) throw BusinessException.notFound("尚无身体维度记录");
        return record;
    }

    public PageResult<BodyMeasurement> page(long page, long size) {
        Page<BodyMeasurement> result = mapper.selectPage(new Page<>(page, size), base()
            .orderByDesc(BodyMeasurement::getRecordDate)
            .orderByDesc(BodyMeasurement::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    public BodyMeasurement save(BodyMeasurementRequest request) {
        BodyMeasurement record = findDate(request.getRecordDate());
        if (record == null) {
            record = new BodyMeasurement();
            record.setUserId(ProfileService.USER_ID);
            copy(record, request);
            mapper.insert(record);
        } else {
            copy(record, request);
            mapper.updateById(record);
        }
        return record;
    }

    public BodyMeasurement update(long id, BodyMeasurementRequest request) {
        BodyMeasurement record = find(id);
        BodyMeasurement sameDate = findDate(request.getRecordDate());
        if (sameDate != null && !sameDate.getId().equals(id)) {
            throw BusinessException.conflict("同一天只能有一条身体维度记录");
        }
        copy(record, request);
        mapper.updateById(record);
        return record;
    }

    public void delete(long id) {
        mapper.deleteById(find(id));
    }

    private BodyMeasurement find(long id) {
        BodyMeasurement record = mapper.selectById(id);
        if (record == null || !Long.valueOf(ProfileService.USER_ID).equals(record.getUserId())) {
            throw BusinessException.notFound("身体维度记录不存在");
        }
        return record;
    }

    private BodyMeasurement findDate(LocalDate date) {
        return mapper.selectOne(base().eq(BodyMeasurement::getRecordDate, date));
    }

    private LambdaQueryWrapper<BodyMeasurement> base() {
        return new LambdaQueryWrapper<BodyMeasurement>()
            .eq(BodyMeasurement::getUserId, ProfileService.USER_ID);
    }

    private void copy(BodyMeasurement target, BodyMeasurementRequest source) {
        target.setRecordDate(source.getRecordDate());
        target.setHeightCm(source.getHeightCm());
        target.setWeightKg(source.getWeightKg());
        target.setUpperChestCm(source.getUpperChestCm());
        target.setUnderChestCm(source.getUnderChestCm());
        target.setWaistCm(source.getWaistCm());
        target.setAbdomenCm(source.getAbdomenCm());
        target.setShoulderWidthCm(source.getShoulderWidthCm());
        target.setThighCm(source.getThighCm());
        target.setCalfCm(source.getCalfCm());
        target.setAnkleCm(source.getAnkleCm());
        target.setUpperArmCm(source.getUpperArmCm());
        target.setWristCm(source.getWristCm());
        target.setThighLengthCm(source.getThighLengthCm());
        target.setCalfLengthCm(source.getCalfLengthCm());
        target.setUpperArmLengthCm(source.getUpperArmLengthCm());
        target.setForearmLengthCm(source.getForearmLengthCm());
    }
}
