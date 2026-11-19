package com.portfolio.bankapi.support;

import org.testcontainers.DockerClientFactory;

/** Lo usa @EnabledIf (vía @IntegrationTest) para saltar las pruebas si no hay Docker. */
public final class DockerAvailability {

    private DockerAvailability() {
    }

    public static boolean isAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
