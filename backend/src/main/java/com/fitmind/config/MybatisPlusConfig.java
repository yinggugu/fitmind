package com.fitmind.config;
import com.baomidou.mybatisplus.annotation.DbType; import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor; import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor; import org.springframework.context.annotation.*;
@Configuration public class MybatisPlusConfig { @Bean public MybatisPlusInterceptor interceptor(){MybatisPlusInterceptor i=new MybatisPlusInterceptor();i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));return i;} }
