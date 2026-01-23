package com.wfh.drawio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * @author fenghuanwang
 */
@Configuration
public class MsgPackConfig {


    @Bean("msgPackRestTemplate")
    public RestTemplate msgPackRestTemplate() {
        ObjectMapper msgPackMapper = new ObjectMapper(new MessagePackFactory());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(msgPackMapper);
        converter.setSupportedMediaTypes(Collections.singletonList(new MediaType("application", "x-msgpack")));
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setMessageConverters(Collections.singletonList(converter));
        return restTemplate;
    }
}