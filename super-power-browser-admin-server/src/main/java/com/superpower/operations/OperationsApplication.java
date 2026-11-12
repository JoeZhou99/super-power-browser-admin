package com.superpower.operations;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName="org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
@MapperScan(basePackages="com.superpower.operations.shared",annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class OperationsApplication {
    public static void main(String[] args) { SpringApplication.run(OperationsApplication.class,args); }
}
