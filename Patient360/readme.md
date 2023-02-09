There are two use cases covered here: Care Quality and Common Well.

Care Quality steps:
1. Read .csv file with patient demographics.
2. Call FHIR Search api (Patient resource) to see if it is existing Patient.
3. If non existing Patient then create the Patient with the demographics from the .csv file.
4. Make a 50 miles CareQuality document search for the patient.
5. Retrieve individual CareQuality documents and save them to the local file system.

Common Well steps:
1. Read .csv file with patient demographics.
2. Call FHIR Search api (Patient resource) to see if it is existing Patient.
3. Check if Patient is Enrolled in CommonWell.
4. Enroll the Patient if needed.
5. Make a CommonWell document search for the patient.
6. Import the Bundle of found DocumentReferences to Health Gorilla.

This package consists go the following files:

- params.json: contains URL for HG FHIR server, and Bearer access token.
- patients.csv: contains patient demographics
- CareQuality.java: main method for Care Quality.
- CommonWell.java: main method for Common Well.
- Bundle.java and HttpWorker: utility classes.

The Java code was tested using: OpenJDK Runtime Environment AdoptOpenJDK-11.0.11+9 (build 11.0.11+9).

Dependency: com.googlecode.json/json-simple-1.1.1.jar - JSON support.
