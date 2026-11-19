package com.portfolio.bankapi.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Marca una prueba de integración: arranca la aplicación completa contra el MySQL
 * compartido en Docker. Si Docker no está disponible, la prueba se omite (no falla).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
@EnabledIf(
        value = "com.portfolio.bankapi.support.DockerAvailability#isAvailable",
        disabledReason = "Docker no está disponible")
public @interface IntegrationTest {
}
