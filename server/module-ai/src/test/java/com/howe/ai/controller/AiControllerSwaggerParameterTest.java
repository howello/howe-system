package com.howe.ai.controller;

import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiControllerSwaggerParameterTest {
    @Test
    void everyPathAndQueryParameterHasSwaggerDescription() {
        for (Class<?> controller : new Class<?>[]{AiAdminController.class, com.howe.ai.config.AiConfigController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                java.lang.reflect.Parameter[] parameters = method.getParameters();
                for (java.lang.reflect.Parameter parameter : parameters) {
                    if (parameter.isAnnotationPresent(PathVariable.class)
                            || parameter.isAnnotationPresent(RequestParam.class)) {
                        assertTrue(parameter.isAnnotationPresent(Parameter.class),
                                () -> controller.getSimpleName() + "." + method.getName()
                                        + " missing @Parameter on " + parameter.getName());
                    }
                }
            }
        }
    }
}
