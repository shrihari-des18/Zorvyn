package com.shrihari.smartpaybackend.common;


import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "SmartPay Core Backend Running \nLast Updated on : 5th April at 19:46";
    }
}

