package com.portfolio.bankapi.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * Un ÚNICO MySQL para todas las pruebas de integración.
 *
 * El contenedor es un bean de Spring: nace con el contexto, se reutiliza en todas las clases
 * de prueba (Spring cachea el contexto) y se apaga al final, DESPUÉS de cerrar el pool de
 * conexiones. Así desaparecen los 3 contenedores y la espera de 30 s del cierre.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainerConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.4");
    }
}
