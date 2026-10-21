package com.multitenant.workspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MultiTenantWorkspaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiTenantWorkspaceApplication.class, args);
    }
}
