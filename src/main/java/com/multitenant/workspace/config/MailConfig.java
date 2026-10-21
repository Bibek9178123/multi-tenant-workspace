package com.multitenant.workspace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    private static final Logger logger = LoggerFactory.getLogger(MailConfig.class);

    @Value("${spring.mail.host:smtp-relay.brevo.com}")
    private String host;

    @Value("${spring.mail.port:2525}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        String cleanHost = sanitize(host);
        sender.setHost(cleanHost != null && !cleanHost.isBlank() ? cleanHost : "smtp-relay.brevo.com");
        sender.setPort(port > 0 ? port : 2525);

        String cleanUsername = sanitize(username);
        if (cleanUsername != null && !cleanUsername.isBlank()) {
            sender.setUsername(cleanUsername);
        }

        String cleanPassword = sanitize(password);
        if (cleanPassword != null && !cleanPassword.isBlank()) {
            sender.setPassword(cleanPassword);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        logger.info("[MailConfig] Configured custom JavaMailSender: host={}, port={}, user={}, passLength={}",
                sender.getHost(), sender.getPort(), sender.getUsername(),
                cleanPassword != null ? cleanPassword.length() : 0);

        return sender;
    }

    public static String sanitize(String input) {
        if (input == null) return null;
        String val = input.trim();
        // Remove enclosing double or single quotes
        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
            if (val.length() >= 2) {
                val = val.substring(1, val.length() - 1).trim();
            }
        }
        // Remove trailing or leading whitespace, carriage returns, newlines, tabs
        val = val.replaceAll("[\\r\\n\\t]", "").trim();
        return val;
    }
}
