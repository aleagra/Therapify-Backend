package com.example.therapify.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    public double[] getCoordinates(String address) {

        String fullAddress =
                address + ", Mar del Plata, Buenos Aires, Argentina";

        System.out.println("Geocoding: " + fullAddress);

        String url = "https://nominatim.openstreetmap.org/search"
                + "?q=" + fullAddress.replace(" ", "+")
                + "&format=json&limit=1";

        ResponseEntity<List> response =
                restTemplate.getForEntity(url, List.class);

        if (response.getBody() == null || response.getBody().isEmpty()) {
            throw new RuntimeException("Dirección no encontrada");
        }

        Map result = (Map) response.getBody().get(0);

        double lat = Double.parseDouble(result.get("lat").toString());
        double lon = Double.parseDouble(result.get("lon").toString());

        System.out.println("Lat: " + lat + " Lon: " + lon);

        return new double[]{lat, lon};
    }

}
