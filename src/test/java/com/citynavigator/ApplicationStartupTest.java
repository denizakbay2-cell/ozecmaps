package com.citynavigator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.citynavigator.service.DemoIncidents;
import com.citynavigator.service.NationalEngine;
import com.citynavigator.service.NetworkImportService;
import com.citynavigator.service.RoutingEngine;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ApplicationStartupTest {

    @Test
    void preparesSchemaNetworkAndGraphsInThatOrder() {
        DatabaseMigrator migrator = mock(DatabaseMigrator.class);
        NetworkImportService networkImport = mock(NetworkImportService.class);
        RoutingEngine engine = mock(RoutingEngine.class);
        NationalEngine national = mock(NationalEngine.class);
        DemoIncidents demo = mock(DemoIncidents.class);

        new ApplicationStartup(migrator, networkImport, engine, national, demo).prepare();

        InOrder order = inOrder(migrator, networkImport, engine, national);
        order.verify(migrator).migrate();
        order.verify(networkImport).importBundledAreaIfEmpty();
        order.verify(engine).load();
        order.verify(national).load();
        // DEMO_DATA is not set while the tests run
        verifyNoInteractions(demo);
    }

    @Test
    void aFailedStepLeavesTheApplicationUpInsteadOfCrashingTheStart() {
        DatabaseMigrator migrator = mock(DatabaseMigrator.class);
        NetworkImportService networkImport = mock(NetworkImportService.class);
        RoutingEngine engine = mock(RoutingEngine.class);
        doThrow(new IllegalStateException("database unreachable")).when(networkImport).importBundledAreaIfEmpty();

        // the background preparation swallows the failure (and logs it); it must not propagate
        assertThatCode(() -> new ApplicationStartup(migrator, networkImport, engine,
                mock(NationalEngine.class), mock(DemoIncidents.class)).prepare()).doesNotThrowAnyException();
    }
}
