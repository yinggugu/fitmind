package com.fitmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.PageResult;
import com.fitmind.dto.MealRecordRequest;
import com.fitmind.entity.MealRecord;
import com.fitmind.enums.MealType;
import com.fitmind.exception.BusinessException;
import com.fitmind.mapper.MealRecordMapper;
import com.fitmind.vo.NutritionStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class MealService {
    private final MealRecordMapper mapper;
    public PageResult<MealRecord> page(LocalDate date,long page,long size){LambdaQueryWrapper<MealRecord> query=base().eq(date!=null,MealRecord::getRecordDate,date).orderByDesc(MealRecord::getRecordDate,MealRecord::getId);Page<MealRecord> result=mapper.selectPage(new Page<>(page,size),query);return new PageResult<>(result.getCurrent(),result.getSize(),result.getTotal(),result.getRecords());}
    public MealRecord create(MealRecordRequest request){MealRecord record=new MealRecord();record.setUserId(ProfileService.USER_ID);copy(record,request);mapper.insert(record);return record;}
    public MealRecord update(long id,MealRecordRequest request){MealRecord record=find(id);copy(record,request);mapper.updateById(record);return record;}
    public void delete(long id){mapper.deleteById(find(id));}
    public List<MealRecord> byDate(LocalDate date){return mapper.selectList(base().eq(MealRecord::getRecordDate,date));}
    public MealRecord latest(){return mapper.selectOne(base().orderByDesc(MealRecord::getRecordDate,MealRecord::getId).last("LIMIT 1"));}
    public NutritionStatisticsVO nutrition(int days){
        if(days<1||days>30)throw new IllegalArgumentException("days must be between 1 and 30");
        LocalDate endDate=LocalDate.now();
        return nutritionBetween(endDate.minusDays(days-1),endDate);
    }
    public List<MealRecord> listBetween(LocalDate startDate,LocalDate endDate){
        if(startDate==null||endDate==null||startDate.isAfter(endDate))throw new IllegalArgumentException("日期范围不正确");
        return mapper.selectList(base().between(MealRecord::getRecordDate,startDate,endDate)
            .orderByAsc(MealRecord::getRecordDate,MealRecord::getId));
    }
    public NutritionStatisticsVO nutritionBetween(LocalDate startDate,LocalDate endDate){
        List<MealRecord> rows=listBetween(startDate,endDate);
        NutritionStatisticsVO value=new NutritionStatisticsVO();
        for(MealRecord row:rows){value.setTotalCaloriesKcal(value.getTotalCaloriesKcal().add(row.getCaloriesKcal()));value.setProteinG(value.getProteinG().add(row.getProteinG()));value.setCarbohydrateG(value.getCarbohydrateG().add(row.getCarbohydrateG()));value.setFatG(value.getFatG().add(row.getFatG()));if(Boolean.TRUE.equals(row.getEstimated()))value.setContainsEstimatedData(true);}
        BigDecimal macroTotal=value.getProteinG().multiply(BigDecimal.valueOf(4)).add(value.getCarbohydrateG().multiply(BigDecimal.valueOf(4))).add(value.getFatG().multiply(BigDecimal.valueOf(9)));
        value.setMacroDistribution(Arrays.asList(item("PROTEIN",value.getProteinG().multiply(BigDecimal.valueOf(4)),macroTotal),item("CARBOHYDRATE",value.getCarbohydrateG().multiply(BigDecimal.valueOf(4)),macroTotal),item("FAT",value.getFatG().multiply(BigDecimal.valueOf(9)),macroTotal)));
        for(MealType type:MealType.values()){BigDecimal sum=rows.stream().filter(row->row.getMealType()==type).map(MealRecord::getCaloriesKcal).reduce(BigDecimal.ZERO,BigDecimal::add);value.getMealCalorieDistribution().add(item(type.name(),sum,value.getTotalCaloriesKcal()));}
        return value;
    }
    private NutritionStatisticsVO.DistributionItem item(String name,BigDecimal value,BigDecimal total){BigDecimal percent=total.signum()==0?BigDecimal.ZERO:value.multiply(BigDecimal.valueOf(100)).divide(total,1,RoundingMode.HALF_UP);return new NutritionStatisticsVO.DistributionItem(name,value,percent);}
    private MealRecord find(long id){MealRecord record=mapper.selectById(id);if(record==null||record.getUserId()==null||record.getUserId()!=ProfileService.USER_ID)throw BusinessException.notFound("饮食记录不存在");return record;}
    private LambdaQueryWrapper<MealRecord> base(){return new LambdaQueryWrapper<MealRecord>().eq(MealRecord::getUserId,ProfileService.USER_ID);}
    private void copy(MealRecord record,MealRecordRequest request){record.setRecordDate(request.getRecordDate());record.setMealType(request.getMealType());record.setFoodName(request.getFoodName());record.setPortionDescription(request.getPortionDescription());record.setCaloriesKcal(request.getCaloriesKcal());record.setProteinG(request.getProteinG());record.setCarbohydrateG(request.getCarbohydrateG());record.setFatG(request.getFatG());record.setEstimated(Boolean.TRUE.equals(request.getEstimated()));record.setSourceType(request.getSourceType());record.setImageUrl(request.getImageUrl());}
}
