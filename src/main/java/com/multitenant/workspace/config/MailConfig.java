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
        return createSender(this.host, this.port, this.username, this.password);
    }

    public static JavaMailSenderImpl createSender(String host, int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        String cleanHost = sanitize(host);
        sender.setHost(cleanHost != null && !cleanHost.isBlank() ? cleanHost : "smtp-relay.brevo.com");
        int targetPort = port > 0 ? port : 2525;
        sender.setPort(targetPort);

        String cleanUser = sanitize(username);
        if (cleanUser == null || cleanUser.isBlank()) {
            cleanUser = sanitize(System.getenv("MAIL_USERNAME"));
        }
        if (cleanUser != null && !cleanUser.isBlank()) {
            sender.setUsername(cleanUser);
        }

        String cleanPass = sanitize(password);
        if (cleanPass == null || cleanPass.isBlank()) {
            cleanPass = sanitize(System.getenv("MAIL_PASSWORD"));
        }
        if (cleanPass == null || cleanPass.isBlank()) {
            cleanPass = sanitize(System.getenv("SPRING_MAIL_PASSWORD"));
        }
        if (cleanPass != null && !cleanPass.isBlank()) {
            sender.setPassword(cleanPass);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (targetPort == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        logger.info("[MailConfig] Initialized JavaMailSender: host={}, port={}, user={}, passLen={}",
                sender.getHost(), sender.getPort(), sender.getUsername(),
                cleanPass != null ? cleanPass.length() : 0);

        return sender;
    }

    public static String sanitize(String input) {
        if (input == null) return null;
        String val = input.trim();
        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
            if (val.length() >= 2) {
                val = val.substring(1, val.length() - 1).trim();
            }
        }
        val = val.replaceAll("[\\r\\n\\t]", "").trim();
        return val;
    }
}
