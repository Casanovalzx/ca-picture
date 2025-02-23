package com.ca.capicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.ca.capicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class CaPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaPictureBackendApplication.class, args);
    }

}
