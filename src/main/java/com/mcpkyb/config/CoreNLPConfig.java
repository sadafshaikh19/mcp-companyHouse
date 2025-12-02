package com.mcpkyb.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class CoreNLPConfig {

    @Bean
    public StanfordCoreNLP stanfordCoreNLP() {
        Properties props = new Properties();
        // Include minimum required annotators for sentiment analysis
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,sentiment");
        props.setProperty("sutime.binders", "0");
        return new StanfordCoreNLP(props);
    }
}
