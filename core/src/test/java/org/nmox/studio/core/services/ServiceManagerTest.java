package org.nmox.studio.core.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceManager.
 */
class ServiceManagerTest {

    private ServiceManager serviceManager;
    
    @Mock
    private ServiceManager.ServiceListener mockListener;
    
    @Mock
    private Service mockService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serviceManager = ServiceManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        // Clean up any registered services
        serviceManager.unregisterService(TestService.class);
        serviceManager.removeServiceListener(mockListener);
    }

    @Test
    void testGetInstance() {
        assertThat(serviceManager).isNotNull();
        assertThat(ServiceManager.getInstance()).isSameAs(serviceManager);
    }

    @Test
    void testRegisterAndGetService() {
        TestService testService = new TestService();
        
        serviceManager.registerService(TestService.class, testService);
        
        TestService retrieved = serviceManager.getService(TestService.class);
        assertThat(retrieved).isSameAs(testService);
    }

    @Test
    void testUnregisterService() {
        TestService testService = new TestService();
        serviceManager.registerService(TestService.class, testService);
        
        serviceManager.unregisterService(TestService.class);
        
        TestService retrieved = serviceManager.getService(TestService.class);
        assertThat(retrieved).isNull();
    }

    @Test
    void testServiceListener() throws InterruptedException {
        serviceManager.addServiceListener(mockListener);
        
        TestService testService = new TestService();
        serviceManager.registerService(TestService.class, testService);
        
        // Wait for async notification
        Thread.sleep(100);
        
        verify(mockListener).serviceRegistered(TestService.class, testService);
    }

    @Test
    void testServiceListenerUnregistration() throws InterruptedException {
        serviceManager.addServiceListener(mockListener);
        
        TestService testService = new TestService();
        serviceManager.registerService(TestService.class, testService);
        
        // Wait for registration notification
        Thread.sleep(100);
        
        serviceManager.unregisterService(TestService.class);
        
        // Wait for unregistration notification
        Thread.sleep(100);
        
        verify(mockListener).serviceRegistered(TestService.class, testService);
        verify(mockListener).serviceUnregistered(TestService.class, testService);
    }

    @Test
    void testRemoveServiceListener() {
        serviceManager.addServiceListener(mockListener);
        serviceManager.removeServiceListener(mockListener);
        
        TestService testService = new TestService();
        serviceManager.registerService(TestService.class, testService);
        
        verifyNoInteractions(mockListener);
    }

    // Test service implementation
    private static class TestService implements Service {
        @Override
        public String getServiceName() {
            return "TestService";
        }
    }
}