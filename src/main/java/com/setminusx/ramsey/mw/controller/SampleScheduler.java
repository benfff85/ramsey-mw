package com.setminusx.ramsey.mw.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j

@Component
public class SampleScheduler {

    @Scheduled(fixedRate = 300000)
    public void doSomething(){
        log.info("Doing Something");
    }
}
