package com.fitmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.PageResult;
import com.fitmind.dto.WeightRecordRequest;
import com.fitmind.entity.WeightRecord;
import com.fitmind.exception.BusinessException;
import com.fitmind.mapper.WeightRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service @RequiredArgsConstructor
public class WeightService {
    private final WeightRecordMapper mapper;
    public PageResult<WeightRecord> page(long page, long size) {
        Page<WeightRecord> result = mapper.selectPage(new Page<>(page, size), base().orderByDesc(WeightRecord::getRecordDate));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }
    public List<WeightRecord> trend(int days) {
        if (days != 7 && days != 30) throw new IllegalArgumentException("days must be 7 or 30");
        List<WeightRecord> records = mapper.selectList(base().orderByDesc(WeightRecord::getRecordDate).last("LIMIT " + days));
        Collections.reverse(records);
        return records;
    }
    public WeightRecord create(WeightRecordRequest request) {
        if (findDate(request.getRecordDate()) != null) throw BusinessException.conflict("同一天只能有一条体重记录");
        WeightRecord record = new WeightRecord(); record.setUserId(ProfileService.USER_ID); copy(record, request); mapper.insert(record); return record;
    }
    public WeightRecord update(long id, WeightRecordRequest request) {
        WeightRecord record = find(id); WeightRecord sameDate = findDate(request.getRecordDate());
        if (sameDate != null && !sameDate.getId().equals(id)) throw BusinessException.conflict("同一天只能有一条体重记录");
        copy(record, request); mapper.updateById(record); return record;
    }
    public void delete(long id) { mapper.deleteById(find(id)); }
    public WeightRecord latest() { return mapper.selectOne(base().orderByDesc(WeightRecord::getRecordDate).last("LIMIT 1")); }
    public List<WeightRecord> listBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("日期范围不正确");
        }
        return mapper.selectList(base()
            .between(WeightRecord::getRecordDate, startDate, endDate)
            .orderByAsc(WeightRecord::getRecordDate));
    }
    private WeightRecord find(long id) { WeightRecord record = mapper.selectById(id); if (record == null || record.getUserId() == null || record.getUserId() != ProfileService.USER_ID) throw BusinessException.notFound("体重记录不存在"); return record; }
    private WeightRecord findDate(java.time.LocalDate date) { return mapper.selectOne(base().eq(WeightRecord::getRecordDate, date)); }
    private LambdaQueryWrapper<WeightRecord> base() { return new LambdaQueryWrapper<WeightRecord>().eq(WeightRecord::getUserId, ProfileService.USER_ID); }
    private void copy(WeightRecord record, WeightRecordRequest request) { record.setRecordDate(request.getRecordDate()); record.setRecordTime(request.getRecordTime() == null ? LocalTime.of(7, 0) : request.getRecordTime()); record.setWeightKg(request.getWeightKg()); }
}
