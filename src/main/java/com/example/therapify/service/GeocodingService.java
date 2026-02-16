package com.example.therapify.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    public double[] getCoordinates(String address) {
        String fullAddress = address + ", Mar del Plata, Buenos Aires, Argentina";
        System.out.println("Geocoding: " + fullAddress);

        String url = "https://nominatim.openstreetmap.org/search"
                + "?q=" + fullAddress.replace(" ", "+")
                + "&format=json&limit=1";

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "TherapifyApp/1.0 (contact@therapify.com)");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List body = response.getBody();
            if (body == null || body.isEmpty()) {
                throw new RuntimeException("Dirección no encontrada");
            }

            Map result = (Map) body.get(0);

            double lat = Double.parseDouble(result.get("lat").toString());
            double lon = Double.parseDouble(result.get("lon").toString());

            return new double[]{lat, lon};

        } catch (HttpClientErrorException.Forbidden e) {
            System.err.println("⚠ Acceso bloqueado por Nominatim: " + e.getMessage());
            return new double[]{0.0, 0.0};
        } catch (Exception e) {
            throw new RuntimeException("Error geocodificando dirección: " + e.getMessage(), e);
        }
    }
}
