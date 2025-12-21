package com.wfh.drawio.controller;

import com.wfh.drawio.ai.client.DrawClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Title: AIClientController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/20 20:05
 * @description:
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class AIClientController {

    @Resource
    private DrawClient drawClient;



    @PostMapping("/gen")
    public String doChat(String message){
        String s = drawClient.doChat(message);
        return s;
    }

}
