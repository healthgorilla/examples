import java.nio.file.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.*;

public class CommonWell {

    public static void main(String[] args) {
        //
        try {
            // get parameters from params.json
            // something like:
            //       { "token": "xxxxx", "url": "https://sandbox.healthgorilla.com/fhir/"

            Path fileName = Path.of("params.json");
            String str = Files.readString(fileName);
            System.out.println(str);
            JSONParser jsonParser = new JSONParser();
            JSONObject params = (JSONObject) jsonParser.parse(str);
            HttpWorker httpWorker = new HttpWorker((String)params.get("url"), (String)params.get("token"));

            //
            // read input .csv file with patient info
            //  - first line with headers ignored
            //  - second line has a patient - just 1 for now
            fileName = Path.of("patients.csv");
            str = Files.readString(fileName);
            String[] lines = str.split("\n");
            String[] fields = lines[1].split(",");
            System.out.println("1st name = " + fields[0]);
            System.out.println("last name = " + fields[2]);

            // 
            // Let's start by searching for patient.
            //
            if (httpWorker.searchPatient("family=" + fields[2] +
                                         "&given=" + fields[0] +
                                         "&birthdate=" + fields[3]) != 200) {
                System.out.println(httpWorker.statusCode());
                System.out.println(httpWorker.body());
                throw new Exception("Problem. Unexpected HTTP response while calling Patient search.");
            }
            Bundle bundle = new Bundle(httpWorker.body());
            String patientId = "";
            if (bundle.total == 0) {
                // TODO: patient not found, let's create 
            } else if (bundle.total == 1) {
                // Patient found.
                patientId = bundle.getEntryResourceId(0);
                System.out.println("patient id = " + patientId);
            } else {
                System.out.println(httpWorker.body());
                throw new Exception("Problem. Number of patients found = " + bundle.total);
            }  

            //
            // Let's check if the Patient is enrolled with CW, and enroll if not
            //
            switch (httpWorker.cwLookup(patientId)) {
                case 200:
                    // patient already enrolled
                    System.out.println("Patient already enrolled");
                    System.out.println(httpWorker.body());
                    break;
                case 404:
                    System.out.println("Patient is not yet enrolled.");
                    // patient not enrolled. Let's see if there are matching Persons in CW already.
                    if (httpWorker.cwSearch(patientId) != 200) {
                        System.out.println(httpWorker.statusCode());
                        System.out.println(httpWorker.body());
                        throw new Exception("Problem. Unexpected HTTP response while calling CW search.");
                    }
                    bundle = new Bundle(httpWorker.body()); 
                    if (bundle.total == 0) {
                        // no matching Person found in CW. Let's enroll our Patient.
                        System.out.println("No matching CW person found. Let's enroll the patient.");
                        if (httpWorker.cwEnroll(patientId) != 200) {
                            System.out.println(httpWorker.statusCode());
                            System.out.println(httpWorker.body());
                            throw new Exception("Problem. Failed to Enroll the Patient.");
                        }
                        // Patient enrolled.
                        System.out.println("Patient enrolled. ");
                        System.out.println(httpWorker.body());
                    } else {
                        // Multiple CW Persons match our patient. Let's decide what to do - we will Enroll to the first CW Person on the list.
                        System.out.println("Multiple CW Persons match our patient.");
                        System.out.println(httpWorker.body());
                        if (httpWorker.cwEnroll(patientId, bundle.getEntryResourceId(0)) != 200) {
                            System.out.println(httpWorker.statusCode());
                            System.out.println(httpWorker.body());
                            throw new Exception("Problem. Failed to Enroll the Patient.");
                        }
                        System.out.println("Patient enrolled as CW Person = " + bundle.getEntryResourceId(0));
                    }
                    break;
                default: 
                    // something went wrong
                    System.out.println(httpWorker.statusCode());
                    System.out.println(httpWorker.body());
                    throw new Exception("Problem. Unexpected HTTP response while calling CW lookup.");
            }

            // 
            // Patient is enrolled in CW. Let's get Document References.
            // Note: it has to be asynchronies call.
            // 
            if (httpWorker.getDocumentReferences("CW", "patient=" + patientId) != 202) {
                System.out.println(httpWorker.statusCode());
                System.out.println(httpWorker.body());
                throw new Exception("Problem. Unexpected HTTP response while calling CW Document Reference search.");
            }
            // Request for Document References submitted.
            // Let's get results.
            if (getAsyncResponse(httpWorker) != 200) {
                System.out.println(httpWorker.statusCode());
                System.out.println(httpWorker.body());
                throw new Exception("Problem. Unable to get CW Document Reference search results.");
            }

            String body = httpWorker.body();
            bundle = new Bundle(body);
            if (bundle.total > 0) {
                // 
                // we will now import all documents to HG
                // 
                // step 1: get the bundle JSON string with some fields corrected/removed.
                String bundleJSON = bundle.getBatchBundle();
                System.out.println("---------------");
                System.out.println(bundleJSON);
                //
                // step 2: make async call to import
                if (httpWorker.importDocuments(bundleJSON) != 202) {
                    System.out.println(httpWorker.statusCode());
                    System.out.println(httpWorker.body());
                    throw new Exception("Problem. Unexpected HTTP response while calling Import Document Reference.");
                }
                //
                // step 3: get response to async call
                if (getAsyncResponse(httpWorker) != 201) {
                    System.out.println(httpWorker.statusCode());
                    System.out.println(httpWorker.body());
                    throw new Exception("Problem. Unable to get Import Document Reference results.");
                }
                System.out.println("Patient's CW documents imported to HG!!!!!!!!!!!!!!");
            } else {
                System.out.println(httpWorker.body());
                throw new Exception("No CW Document Rererences found for the Patient.");
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    static int getAsyncResponse(HttpWorker httpWorker) throws Exception {
        // We will try to get results for async call up to 3 times, with increasing time interval in between.
        String location = httpWorker.getResponseHeader("Location");
        System.out.println("Location ======== " + location);      
        int tries = 0;
        while (tries < 3 && httpWorker.getAsyncResponse(location) == 202) {
            tries++;
            Thread.sleep(tries * 5000);
        }
        return httpWorker.statusCode();
    }
}
