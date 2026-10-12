package com.ridepool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sends the OTP by email (SMTP) or SMS (Twilio).
 * If nothing is configured, the code is printed in the console so you can test locally.
 */
@Service
public class OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${ridepool.mail.from:no-reply@ridepool.local}")
    private String from;

    @Value("${ridepool.sms.twilio.account-sid:}")
    private String twilioSid;
    @Value("${ridepool.sms.twilio.auth-token:}")
    private String twilioToken;
    @Value("${ridepool.sms.twilio.from-number:}")
    private String twilioFrom;
    @Value("${ridepool.sms.default-country-code:+91}")
    private String defaultCountryCode;

    public OtpDeliveryService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String toEmail, String name, String otp, long minutes) {
        String body = "Hi " + name + ",\n\nYour RidePool password reset code is: " + otp
                + "\n\nIt is valid for " + minutes + " minutes. Don't share it with anyone."
                + "\nIf you didn't ask for this, you can ignore this email.\n\n- RidePool";
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || mailHost.isBlank()) {
            log.warn("[DEV] SMTP not configured. Reset OTP for {} is: {}", toEmail, otp);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Your RidePool password reset code");
            msg.setText(body);
            sender.send(msg);
        } catch (Exception e) {
            log.error("Could not send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSms(String phone, String otp, long minutes) {
        String text = "Your RidePool password reset code is " + otp + ". Valid for " + minutes + " minutes.";
        if (twilioSid.isBlank() || twilioToken.isBlank() || twilioFrom.isBlank()) {
            log.warn("[DEV] SMS not configured. Reset OTP for {} is: {}", phone, otp);
            return;
        }
        try {
            String form = "To=" + enc(toE164(phone)) + "&From=" + enc(twilioFrom) + "&Body=" + enc(text);
            String auth = Base64.getEncoder().encodeToString(
                    (twilioSid + ":" + twilioToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                log.error("Twilio SMS failed ({}): {}", res.statusCode(), res.body());
            }
        } catch (Exception e) {
            log.error("Could not send OTP SMS: {}", e.getMessage());
        }
    }

    private String toE164(String phone) {
        String p = phone.trim();
        if (p.startsWith("+")) {
            return "+" + p.replaceAll("\\D", "");
        }
        String digits = p.replaceAll("\\D", "").replaceFirst("^0+", "");
        return defaultCountryCode + digits;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
