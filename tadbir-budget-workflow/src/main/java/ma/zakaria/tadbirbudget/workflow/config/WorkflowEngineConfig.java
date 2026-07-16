/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.workflow.config;

import ma.zakaria.tadbirbudget.workflow.event.WorkflowEventBridge;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers {@link WorkflowEventBridge} as a global Flowable event listener so it receives
 * engine events (TASK_CREATED, PROCESS_COMPLETED, …) for every deployed process definition.
 * Reusable task listeners (e.g. {@code managerResolutionListener}) are resolved by Spring
 * via {@code delegateExpression} and need no registration here.
 */
@Configuration
public class WorkflowEngineConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> workflowEventBridgeConfigurer(
            WorkflowEventBridge bridge) {
        return config -> {
            List<FlowableEventListener> listeners = config.getEventListeners();
            if (listeners == null) {
                listeners = new ArrayList<>();
            }
            listeners.add(bridge);
            config.setEventListeners(listeners);
        };
    }
}
