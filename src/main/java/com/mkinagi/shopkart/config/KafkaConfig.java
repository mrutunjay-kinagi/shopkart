package com.mkinagi.shopkart.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.mkinagi.shopkart.common.events.Topics;

/**
 * Declares topics (created on start-up by {@link KafkaAdmin}) and the listener retry policy: three
 * exponential retries, then the record is parked on {@code <topic>.DLT} for inspection instead of
 * blocking the partition.
 */
@Configuration
public class KafkaConfig {

	private static final int PARTITIONS = 3;

	@Bean
	KafkaAdmin.NewTopics shopkartTopics() {
		return new KafkaAdmin.NewTopics(topic(Topics.USER_EVENTS), topic(Topics.CART_EVENTS),
				topic(Topics.ORDER_EVENTS), topic(Topics.PAYMENT_EVENTS), topic(Topics.USER_EVENTS + ".DLT"),
				topic(Topics.ORDER_EVENTS + ".DLT"), topic(Topics.PAYMENT_EVENTS + ".DLT"));
	}

	private static NewTopic topic(String name) {
		return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
	}

	@Bean
	CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
		ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
		backOff.setMaxAttempts(3);
		return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
	}

}
