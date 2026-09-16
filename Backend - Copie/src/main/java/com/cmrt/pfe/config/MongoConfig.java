package com.cmrt.pfe.config;

import com.cmrt.pfe.converters.DepartementReadConverter;
import com.cmrt.pfe.converters.RoleReadConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;
import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        List converters = Arrays.asList(
                new DepartementReadConverter(),
                new RoleReadConverter()
        );
        return new MongoCustomConversions(converters);
    }
}
