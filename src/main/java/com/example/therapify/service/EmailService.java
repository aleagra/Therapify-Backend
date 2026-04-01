package com.example.therapify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Service
public class EmailService {

    @Value("${google.script.webhook.url:TU_WEBHOOK_AQUI}")
    private String webhookUrl;
    private final String SECRET_KEY = "therapify_secret_key_123";

    private void sendWebhookEmail(String to, String subject, String text) {
        if ("TU_WEBHOOK_AQUI".equals(webhookUrl)) {
            System.err.println(
                    "❌ Error: Falta configurar la URL del Webhook de Google Script en application.properties o Render (Variable: GOOGLE_SCRIPT_WEBHOOK_URL)");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(Map.of(
                    "secret", SECRET_KEY,
                    "to", to,
                    "subject", subject,
                    "text", text));

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                System.out.println("✅ Email enviado vía Google Script a " + to);
            } else {
                System.err.println("❌ Error de Google Script al enviar a " + to + ": " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Excepción enviando email con el Webhook a " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendResetPassword(String to, String link) {
        String text = "Haz click en el siguiente enlace para recuperar tu contraseña:\n" + link;
        sendWebhookEmail(to, "Recuperar contraseña", text);
    }

    @Async
    public void sendEmailVerification(String to, String link) {
        String text = "¡Bienvenido a Therapify!\n\n" +
                "Para activar tu cuenta, haz click en el siguiente enlace:\n" +
                link + "\n\n" +
                "Si no creaste esta cuenta, puedes ignorar este mensaje.";
        sendWebhookEmail(to, "Verificá tu cuenta en Therapify", text);
    }

    @Async
    public void sendAppointmentConfirmation(
            String patientEmail,
            String doctorEmail,
            String patientName,
            String doctorName,
            String date,
            String startTime,
            String endTime) {
        String subject = "Confirmación de turno - Therapify";

        String textForPatient = "Hola " + patientName + ",\n\n" +
                "Tu turno fue reservado correctamente.\n\n" +
                "Doctor: " + doctorName + "\n" +
                "Fecha: " + date + "\n" +
                "Horario: " + startTime + " - " + endTime + "\n\n" +
                "Estado: PENDIENTE\n\n" +
                "Gracias por confiar en Therapify.";

        String textForDoctor = "Hola Dr/a. " + doctorName + ",\n\n" +
                "Se ha reservado un nuevo turno.\n\n" +
                "Paciente: " + patientName + "\n" +
                "Fecha: " + date + "\n" +
                "Horario: " + startTime + " - " + endTime + "\n\n" +
                "Estado: PENDIENTE\n\n" +
                "Revisalo desde tu panel.";

        sendWebhookEmail(patientEmail, subject, textForPatient);
        sendWebhookEmail(doctorEmail, subject, textForDoctor);
    }
}
