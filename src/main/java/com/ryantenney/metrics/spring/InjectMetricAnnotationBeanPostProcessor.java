/*
 * Copyright 2012 Ryan W Tenney (http://ryan.10e.us)
 *            and Martello Technologies (http://martellotech.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ryantenney.metrics.spring;

import com.ryantenney.metrics.annotation.InjectMetric;
import com.codahale.metrics.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import java.lang.reflect.Field;

public class InjectMetricAnnotationBeanPostProcessor implements BeanPostProcessor, Ordered {

	private static final Logger log = LoggerFactory.getLogger(InjectMetricAnnotationBeanPostProcessor.class);

	private static final AnnotationFilter filter = new AnnotationFilter(InjectMetric.class);

	private final MetricRegistry metrics;

	public InjectMetricAnnotationBeanPostProcessor(final MetricRegistry metrics) {
		this.metrics = metrics;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(final Object bean, String beanName) throws BeansException {
		final Class<?> targetClass = AopUtils.getTargetClass(bean);

		ReflectionUtils.doWithFields(targetClass, new FieldCallback() {
			@Override
			public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
				final InjectMetric annotation = field.getAnnotation(InjectMetric.class);
				final String metricName = Util.forInjectMetricField(targetClass, field, annotation);

				final Class<?> type = field.getType();
				Metric metric = null;
				if (Meter.class == type) {
					metric = metrics.meter(metricName);
				} else if (Timer.class == type) {
					metric = metrics.timer(metricName);
				} else if (Counter.class == type) {
					metric = metrics.counter(metricName);
				} else if (Histogram.class == type) {
					metric = metrics.histogram(metricName);
				} else {
					throw new IllegalStateException("Cannot inject a metric of type " + type.getCanonicalName());
				}

				ReflectionUtils.makeAccessible(field);
                ReflectionUtils.setField(field, bean, metric);

				log.debug("Injected metric {} for field {}.{}", new Object[] { metricName, targetClass.getCanonicalName(), field.getName() });
			}
		}, filter);

		return bean;
	}

	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE - 2;
	}

}
