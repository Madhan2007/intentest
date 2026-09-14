/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.module;

import org.springframework.context.annotation.Condition;
import org.springframework.core.io.Resource;

import java.lang.annotation.*;

/**
 * Meta-annotation for module @Configuration classes: registers only when the
 * module folder is physically present on the classpath (modules/&lt;id&gt;/module.yaml
 * copied as a resource) AND listed in platform.yaml modules.enabled — the same
 * fail-closed discovery algorithm as web/mobile/Python (doc 01 §3).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@java.lang.annotation.Inherited
@org.springframework.context.annotation.Conditional(ConditionalOnModule.OnModuleCondition.class)
public @interface ConditionalOnModule {

    String value();

    class OnModuleCondition implements Condition {
        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                                org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            var attrs = metadata.getAnnotationAttributes(ConditionalOnModule.class.getName());
            if (attrs == null) {
                return false;
            }
            String moduleId = (String) attrs.get("value");

            Resource resource = context.getResourceLoader()
                .getResource("classpath:modules/" + moduleId + "/module.yaml");
            if (!resource.exists()) {
                return false;
            }

            var binder = org.springframework.boot.context.properties.bind.Binder.get(context.getEnvironment());
            java.util.List<String> enabled = binder
                .bind("modules.enabled", org.springframework.boot.context.properties.bind.Bindable.listOf(String.class))
                .orElse(java.util.List.of());
            return enabled.contains(moduleId);
        }
    }
}
