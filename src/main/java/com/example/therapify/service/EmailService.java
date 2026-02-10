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
    public void sendEmailVerification(String to, String link) {

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom("therapifyy@gmail.com");
        mail.setTo(to);
        mail.setSubject("Verificá tu cuenta en Therapify");
        mail.setText(
                "¡Bienvenido a Therapify!\n\n" +
                        "Para activar tu cuenta, haz click en el siguiente enlace:\n" +
                        link + "\n\n" +
                        "Si no creaste esta cuenta, puedes ignorar este mensaje."
        );

        mailSender.send(mail);
    }
}
