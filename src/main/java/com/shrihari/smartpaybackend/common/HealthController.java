package com.shrihari.smartpaybackend.common;


import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "SmartPay Core Backend Running \nLast Updated on : 3rd April at 09:25";
    }
}

