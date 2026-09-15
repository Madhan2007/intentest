/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Kernel entry point. Feature controllers never live in this package (doc 04 §3).
 * Component scan spans com.managemyopz.modules.* too, since module Java sources
 * compile into this same artifact (build-helper extra source roots in pom.xml).
 * DataSource autoconfiguration is excluded: `DataClientConfig` builds a Hikari
 * DataSource itself, only when db.type=postgres (doc 07 §8) — Spring must not
 * try to build one from empty spring.datasource.* properties in dev/memory mode.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class })
@ComponentScan(basePackages = { "com.managemyopz.kernel", "com.managemyopz.modules", "com.managemyopz.apps" })
public class KernelApplication {

    public static void main(String[] args) {
        SpringApplication.run(KernelApplication.class, args);
    }
}
