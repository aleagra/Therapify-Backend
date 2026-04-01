package com.example.therapify.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async
    public void sendResetPassword(String to, String link) {

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom("therapifyy@gmail.com");
        mail.setTo(to);
        mail.setSubject("Recuperar contraseña");
        mail.setText(
                "Haz click en el siguiente enlace para recuperar tu contraseña:\n" +
                        link
        );

        mailSender.send(mail);
    }
    @Async
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

        try {
            mailSender.send(mail);
            System.out.println("✅ Email de verificación enviado a " + to);
        } catch (Exception e) {
            System.err.println("❌ Error enviando email de verificación a " + to + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Async
    public void sendAppointmentConfirmation(
            String patientEmail,
            String doctorEmail,
            String patientName,
            String doctorName,
            String date,
            String startTime,
            String endTime
    ) {

        String subject = "Confirmación de turno - Therapify";

        String textForPatient =
                "Hola " + patientName + ",\n\n" +
                        "Tu turno fue reservado correctamente.\n\n" +
                        "Doctor: " + doctorName + "\n" +
                        "Fecha: " + date + "\n" +
                        "Horario: " + startTime + " - " + endTime + "\n\n" +
                        "Estado: PENDIENTE\n\n" +
                        "Gracias por confiar en Therapify.";

        String textForDoctor =
                "Hola Dr/a. " + doctorName + ",\n\n" +
                        "Se ha reservado un nuevo turno.\n\n" +
                        "Paciente: " + patientName + "\n" +
                        "Fecha: " + date + "\n" +
                        "Horario: " + startTime + " - " + endTime + "\n\n" +
                        "Estado: PENDIENTE\n\n" +
                        "Revisalo desde tu panel.";

        SimpleMailMessage mailToPatient = new SimpleMailMessage();
        mailToPatient.setFrom("therapifyy@gmail.com");
        mailToPatient.setTo(patientEmail);
        mailToPatient.setSubject(subject);
        mailToPatient.setText(textForPatient);

        SimpleMailMessage mailToDoctor = new SimpleMailMessage();
        mailToDoctor.setFrom("therapifyy@gmail.com");
        mailToDoctor.setTo(doctorEmail);
        mailToDoctor.setSubject(subject);
        mailToDoctor.setText(textForDoctor);

        try {
            mailSender.send(mailToPatient);
            mailSender.send(mailToDoctor);
            System.out.println("✅ Emails de confirmación de turno enviados");
        } catch (Exception e) {
            System.err.println("❌ Error enviando emails de confirmación de turno: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
