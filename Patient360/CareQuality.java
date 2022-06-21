import java.nio.file.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

public class CareQuality {

    public static void main(String[] args) {
        //
        try {
            // get parameters from params.json
            // something like:
            //       { "token": "xxxxx", "url": "https://sandbox.healthgorilla.com/fhir/"
            Path fileName = Path.of("params.json");
            String str = Files.readString(fileName);

            JSONParser jsonParser = new JSONParser();
            JSONObject params = (JSONObject) jsonParser.parse(str);
            HttpWorker httpWorker = new HttpWorker((String)params.get("url"),
                                                   (String)params.get("token"));
            String body;
            //
            // read input .csv file with patient info
            //  - first line with headers ignored
            //  - second line has a patient
            fileName = Path.of("patients.csv");
            str = Files.readString(fileName);
            String[] lines = str.split("\n");
            String[] fields = lines[1].split(",");
            System.out.println("1st name = " + fields[0]);
            System.out.println("last name = " + fields[2]);

            // 
            // Let's start by searching for patient.
            if (httpWorker.searchPatient("family=" + fields[2] +
                                         "&given=" + fields[0] +
                                         "&birthdate=" + fields[3]) != 200) {
                // something went wrong
                throw new Exception("HTTP response = " + httpWorker.statusCode() + "\n" + httpWorker.body());
            }
            body = httpWorker.body();
            Bundle bundle = new Bundle(body);
            if (bundle.total > 1) {
                // more than 1 patient found
                throw new Exception("# of patients found = " + bundle.total);
            }
            String patientId = "";
            String zipCode = "";
            if (bundle.total == 0) {
                // patient not found, let's create 
                String patientJson = getPatientJson(fields);
                System.out.println(patientJson);
                if (httpWorker.createPatient(patientJson) != 201) {
                    // something went wrong
                    throw new Exception("Something went wrong creating a Patient. HTTP response = " + httpWorker.statusCode() + "\n" + httpWorker.body());
                }
                patientId = httpWorker.getResponseHeader("Location");
                zipCode = fields[9];
            } else if (bundle.total == 1) {
                patientId = bundle.getEntryResourceId(0);
                zipCode = bundle.getEntryAddressZip(0);
            }
            // We have a Patient.
            // Let's do 50 miles CareQuality search for documents for this patient.
            // The Search is within a radius of the specified zip code or address - we will use zip code.
            // Note: it has to be asynchronies call.
            System.out.println("patient id = " + patientId);
            if (httpWorker.getDocumentReferences("CQ", "patient=" + patientId + "&address-postalcode=" + zipCode) != 202) {
                // something went wrong
                throw new Exception("HTTP response = " + httpWorker.statusCode() + "\n" + httpWorker.body());
            }
            // Request for Document References submitted.
            // We will try to get Document Rererences up to 3 times, with increasing time interval in between.
            String location = httpWorker.getResponseHeader("Location");
            System.out.println("Location ======== " + location);      
            int tries = 0;
            while (tries < 3 && httpWorker.getAsyncResponse(location) == 202) {
                tries++;
                Thread.sleep(tries * 5000);
            }
            if (httpWorker.statusCode() != 200) {
                // something went wrong
                throw new Exception("HTTP response = " + httpWorker.statusCode() + "\n" + httpWorker.body());
            }
                // We got Document References. Let's import them to HG.
                bundle = new Bundle(httpWorker.body());
                JSONObject docResource, attachment;
                for (int i = 0; i < bundle.total; i++) {
                    docResource = bundle.getEntryResource(i);
                    System.out.println(docResource.get("description"));
                    System.out.println(docResource.get("created"));
                    attachment = (JSONObject)((JSONObject)((JSONArray)docResource.get("content")).get(0)).get("attachment");
                    if (httpWorker.getBinaryData(attachment.get("url").toString(), attachment.get("contentType").toString()) != 200) {
                        // something went wrong
                        throw new Exception("Something went wrong getting Binary Data. HTTP response = " + httpWorker.statusCode() + "\n" + httpWorker.body());
                    }
                    fileName = Path.of("file_" + i + ".txt");
                    Files.writeString(fileName, httpWorker.body());
                }
            } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    static String getPatientJson(String[] fields) throws ParseException {
        StringBuilder patient = new StringBuilder();
        patient.append("{\"resourceType\": \"Patient\", \"name\": [{\"family\": \"")
        .append(fields[2])
        .append("\",\"given\": [\"")
        .append(fields[0])
        .append("\",\"")
        .append(fields[1])
        .append("\"]}],\"gender\": \"")
        .append(fields[4])
        .append("\",\"birthDate\": \"")
        .append(fields[3])
        .append("\",\"address\": [{\"line\": [\"")
        .append(fields[5])
        .append("\", \"\"],\"city\": \"")
        .append(fields[7])
        .append("\",\"state\": \"")
        .append(fields[8])
        .append("\",\"postalCode\": \"")
        .append(fields[9])
        .append("\"}],\"telecom\": [{\"system\": \"phone\",\"value\": \"")
        .append(fields[11])
        .append("\",\"use\": \"home\"},{\"system\": \"email\",\"value\": \"")
        .append(fields[12])
        .append("\"}]}");
        return patient.toString();
    }
}
