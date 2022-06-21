import java.net.http.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.zip.GZIPInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.util.*;

public class HttpWorker {
    String url;
    String token;
    HttpClient client;
    HttpResponse<String> response;
    Map<String, List<String>> responseMap;
    
    HttpWorker(String url, String token) {
        this.url = url;
        this.token = token;
        client = HttpClient.newHttpClient();
    }
        
    int searchPatient(String params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient?" + params))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int createPatient(String patientJson) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient"))
                .POST(BodyPublishers.ofString(patientJson))
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int cwLookup(String patientId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient/" + patientId + "/$cw-lookup"))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int cwSearch(String patientId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient/" + patientId + "/$cw-search"))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int cwEnroll(String patientId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient/" + patientId + "/$cw-enroll"))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int cwEnroll(String patientId, String cwPersonId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Patient/" + patientId + "/$cw-enroll?person=" + cwPersonId))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int getBinaryData(String url, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .setHeader("Content-Type", contentType)
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int getDocumentReferences(String network, String params) throws Exception {
        String urlSuffix = "";
        if (network.equals("CW")) {
            urlSuffix = "DocumentReference/$cw-search?" + params;
        } else if (network.equals("CQ")) {
            urlSuffix = "DocumentReference/$cq-search?" + params;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + urlSuffix))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Prefer", "respond-async")
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int importDocuments(String documentReferenceBundle) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "Bundle"))
                .POST(BodyPublishers.ofString(documentReferenceBundle))
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Prefer", "respond-async")
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int getAsyncResponse(String location) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.substring(0, url.indexOf("fhir") - 1) + location))
                .GET()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept-Encoding", "")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    int statusCode() {
        return response.statusCode();
    }

    String body() throws Exception {
        String encoding = getResponseHeader("Content-Encoding");
        if (encoding != null) {
            System.out.println("encoding ====" + encoding);
            if (encoding.equals("gzip")) {
                return decompress(response.body().getBytes());
            } else {
                throw new Exception("We coded only to handle gzip encoding. We got encoding = " + encoding);
            }
        }
        return response.body();
    }

    String getResponseHeader(String name) {
        responseMap = response.headers().map();

        List<String> header = responseMap.get(name);
        if (header != null) {
            return (String)header.get(0);
        } else {
            return null;
        }

    }

    public static String decompress(byte[] str) throws Exception {
	    if (str == null ) {
	        return null;
	    }
	    
	    GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(str));
	    BufferedReader bf = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
	    String outStr = "";
	    String line;
	    while ((line=bf.readLine())!=null) {
	      outStr += line;
	    }
	    System.out.println("Output String lenght : " + outStr.length());
	    return outStr;
	 }
}
