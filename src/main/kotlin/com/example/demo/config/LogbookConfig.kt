package com.example.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.HttpLogFormatter
import org.zalando.logbook.Sink
import org.zalando.logbook.json.JsonHttpLogFormatter
import org.zalando.logbook.logstash.LogstashLogbackSink

@Configuration
class LogbookConfig {
    @Bean
    fun logbookLogStash(): Sink {
        val formatter: HttpLogFormatter = JsonHttpLogFormatter()
        val sink = LogstashLogbackSink(formatter)
        return sink
    }
}
