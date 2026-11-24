package com.citynavigator.resource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthResourceTest {

    @Test
    void healthIsUpAsSoonAsTheWebLayerIsDeployed() {
        assertThat(new HealthResource().health().status()).isEqualTo("UP");
    }
}
