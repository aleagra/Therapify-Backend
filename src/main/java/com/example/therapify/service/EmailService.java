package com.example.therapify.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendResetPassword(String to, String link) {

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom("therapify.app@gmail.com");
        mail.setTo(to);
        mail.setSubject("Recuperar contraseña");
        mail.setText(
                "Haz click en el siguiente enlace para recuperar tu contraseña:\n" +
                        link
        );

        mailSender.send(mail);
    }
}
