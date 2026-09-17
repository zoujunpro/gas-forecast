package com.gas.forecast.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Order(3)
@Component
public class WebLogAspect {

    private static final Logger log = LoggerFactory.getLogger(WebLogAspect.class);
    private static final int MAX_ARGUMENT_LENGTH = 1000;

    @Around("@annotation(com.gas.forecast.common.web.WebLog)")
    public Object aroundLog(ProceedingJoinPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        WebLog webLog = method.getAnnotation(WebLog.class);
        String description = webLog.value().isBlank() ? method.getName() : webLog.value();
        HttpServletRequest request = currentRequest();
        String arguments = formatArguments(signature.getParameterNames(), point.getArgs());

        if (request != null) {
            log.info("Request [{}] {} {} args={}", description, request.getMethod(), request.getRequestURI(), arguments);
        } else {
            log.info("Request [{}] args={}", description, arguments);
        }

        try {
            Object result = point.proceed();
            log.info("Request [{}] completed in {} ms", description, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable throwable) {
            log.warn("Request [{}] failed in {} ms, message={}", description, System.currentTimeMillis() - start, throwable.getMessage());
            throw throwable;
        }
    }

    private String formatArguments(String[] names, Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (shouldSkipArgument(arg)) {
                continue;
            }
            String name = names != null && index < names.length ? names[index] : "arg" + index;
            if ("response".equalsIgnoreCase(name)) {
                continue;
            }
            values.put(name, formatValue(arg));
        }
        return values.toString();
    }

    private boolean shouldSkipArgument(Object arg) {
        return arg == null
                || arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof BindingResult
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[]
                || arg instanceof ResponseEntity<?>
                || arg instanceof InputStream
                || arg instanceof OutputStream
                || arg instanceof Principal;
    }

    private Object formatValue(Object value) {
        String text = String.valueOf(value);
        if (text.length() <= MAX_ARGUMENT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ARGUMENT_LENGTH) + "...";
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
