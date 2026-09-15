package com.fitmind;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling @MapperScan("com.fitmind.mapper") @SpringBootApplication
public class FitMindApplication { public static void main(String[] args) { SpringApplication.run(FitMindApplication.class, args); } }
