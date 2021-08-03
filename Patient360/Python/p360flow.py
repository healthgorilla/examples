
import requests ##requests must be installed to use this script
import json
import csv
from urllib.parse import urlparse, urlsplit
import time

#enter your assertion
assertion = 'YOUR ASSERTION'
##enter your client id
client_id = 'CLIENT ID'
baseUrl = "https://sandbox.healthgorilla.com"
bearerToken = 0 ## leave this 0. This is a global variable that will populate when the script is run. 

#######
# getBearerToken
#########
def getBearerToken():
    global bearerToken
    url = baseUrl+"/oauth/token"

    payload='grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&client_id='+client_id+'&assertion='+assertion+'&scope=user%2F*.*%20patient360%20'
    headers = {
        'Content-Type': 'application/x-www-form-urlencoded',
    }

    response = requests.request("POST", url, headers=headers, data=payload)
    
    print(response.text)
    #convert string to a dict so user can pull access token into a variable
    resDict = json.loads(response.text)
    bearerToken = resDict['access_token']

#######
# searchForPatient
#
#  search for a patient in HG using demographics from patient list
#  return false if no matches found
#  return HG ID if match is found
#  else write a message to the 'multipleMatches log file for further consideration
def searchForPatient(patient):
    firstName = patient['First Name']
    lastName = patient['Last Name']
    birthdate = patient['DOB']
    global bearerToken
    url = "https://sandbox.healthgorilla.com/fhir/Patient?given="+firstName+"&family="+lastName+"&birthdate="+birthdate

    payload = ""
    headers = {
        'Authorization': 'Bearer '+ bearerToken,
        'Content-Type': 'application/json',
    }

    response = requests.request("GET", url, headers=headers, data=payload)
    resDict = json.loads(response.text)
    
    if resDict['total'] == 0:
        with open('log.txt','a') as myFile:
            myFile.write("No matches found in Health Gorilla for patient "+firstName+" "+lastName+'\n')
        return False
    elif resDict['total'] == 1:
        with open('log.txt','a') as myFile:
            myFile.write("1 match found in Health Gorilla for patient "+firstName+" "+lastName+" with HG ID: "+resDict['entry'][0]['resource']['id']+'\n')
        ##next line returns the HG ID from the JSON response
        return resDict['entry'][0]['resource']['id']
    else:
        with open('multipleMatchLog.txt','a') as myFile:
            myFile.write("Multiple matches found in Health Gorilla for patient "+firstName+" "+lastName+'\n')
        return "multiple"
    
########
# createPatient
# this function creates a patient in HG
#
def createPatient(patient):
    with open('log.txt','a') as myFile:
            myFile.write("Creating a new HG patient record for: "+patient['First Name']+" "+patient['Last Name']+'\n')
    global bearerToken
    url = baseUrl+"/fhir/Patient"
    address = patient['Address 1'] + ' ' + patient['Address 2'] + ' ' + patient['City'] + ', ' + patient['State'] + ' ' + patient['ZipCode']
    payload = json.dumps({
        "name": [
            {
                "use": "usual",
                "family": patient['Last Name'],
                 "given": [
                    patient['First Name']
                ]
            }
        ],
        "address": [
            {
                "use": "home",
                "type": "both",
                "text": address,
                "line": [
                     patient['Address 1']
                ],
                "city": patient['City'],
                "state": patient['State'],
                "postalCode": patient['ZipCode'],
            }
        ],
        "gender": patient['Gender'],
        "birthDate": patient['DOB'],
        "telecom": [
            {
                "use": "home"
            },
            {
                 "system": "phone",
                "value": patient['Phone'],
                "use": "mobile",
                "rank": 1
            },
            {
            "system": "email",
            "value": patient['Email'],
            "use": "home"
            }
        ],
            "resourceType": "Patient"
    })
    headers = {
        'Authorization': 'Bearer '+bearerToken,
        'Content-Type': 'application/json',
    }

    response = requests.request("POST", url, headers=headers, data=payload)
    if response.status_code == 201:
        #get the response header location and split it into elements 
        fhirPatientUrl = urlsplit(response.headers['location'])
        #grab the path from the location url
        fhirPath = fhirPatientUrl[2]
        #strip the path from the patient ID
        patientId = fhirPath[14:]
        with open('log.txt','a') as myFile:
            myFile.write("Patient "+patient['First Name']+" "+patient['Last Name']+" created in HG with patient ID " + patientId +'\n')
        return patientId
    else:
        with open('log.txt','a') as myFile:
            myFile.write("Failure to create a new HG patient for patient "+patient['First Name']+" "+patient['Last Name']+'\n')
        return False
     

#######
# CSV to dictionary
#   convert Patient List from CSV to dictionary
def csvToDictionary():
    patientDict = {}
    inc = 0
    with open('PatientList.csv') as infile:
        for line in csv.DictReader(infile):
            patientDict[inc] = line
            inc +=1

    return patientDict

######
# cwSearch
# @param takes patientId
#
def cwSearch(patientId):
    with open('log.txt','a') as myFile:
            myFile.write("Searching CW for patientID:" + patientId + '\n')
        
    url = baseUrl+"/fhir/Patient/"+patientId+"/$cw-search"

    payload={}
    headers = {
        'Authorization': 'Bearer '+bearerToken,
    }

    response = requests.request("GET", url, headers=headers, data=payload)

    resDict = json.loads(response.text)
    if resDict['total'] == 0:
        with open('log.txt','a') as myFile:
            myFile.write("No Matching Patients Found For :" + patientId + "in CW"+'\n')
        return False

    else:
        with open('log.txt','a') as myFile:
            myFile.write("CW Search found " + str(resDict['total']) + " match(es) for: " + patientId + '\n')
        #grab first match for this example workflow
        return resDict['entry'][0]['resource']['id']

##########
# cwEnroll
# enroll in CW
# takes two parameters if cwId is NULL then it is a new enrollment
def cwEnroll(patientId, cwId = 0):

    if cwId == 0:
        with open('log.txt','a') as myFile:
            myFile.write("Enrolling: " + patientId + " in CW as a new enrollment" + '\n')
        url = baseUrl+"/fhir/Patient/"+patientId+"/$cw-enroll"

        payload={}
        headers = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer '+ bearerToken,
        }

        response = requests.request("GET", url, headers=headers, data=payload)
        
        resDict = json.loads(response.text)
        if resDict['parameter'][0]['name'] == 'enrolled' and resDict['parameter'][0]['valueBoolean'] == 'True':
            with open('log.txt','a') as myFile:
                myFile.write("Successful enrolling: " + patientId + " in CW as a new enrollment."  + '\n')
        else:
            with open('log.txt','a') as myFile:
                myFile.write("Unsuccessful enrolling: " + patientId + "in CW as a new enrollment." + '\n')
    else:
        with open('log.txt','a') as myFile:
            myFile.write("Enrolling:" + patientId + "in CW with CW ID: " + cwId + '\n')
        url = baseUrl+"/fhir/Patient/"+patientId+"/$cw-enroll?person="+cwId

        payload={}
        headers = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer '+ bearerToken,
        }

        response = requests.request("GET", url, headers=headers, data=payload)

        resDict = json.loads(response.text)
        if resDict['parameter'][0]['name'] == 'enrolled' and resDict['parameter'][0]['valueBoolean'] == 'True':
            with open('log.txt','a') as myFile:
                myFile.write("Successful enrolling:" + patientId + "in CW with CW ID: " + cwId + '\n')
        else:
            with open('log.txt','a') as myFile:
                myFile.write("Unsuccessful enrolling:" + patientId + "in CW with CW ID: " + cwId + '\n')

##############
# cwLookUp
# check to see if the patient id enrolled in CW already
#
def cwLookUp(patientId):
    with open('log.txt','a') as myFile:
        myFile.write("Executing CW Lookup For " + patientId + '\n')
    url = baseUrl+"/fhir/Patient/"+patientId+"/$cw-lookup"

    payload={}
    headers = {
        'Authorization': 'Bearer '+bearerToken,

    }

    response = requests.request("GET", url, headers=headers, data=payload)
    resDict = json.loads(response.text)
    if resDict['issue'][0]['details']['coding'][0]['code'] == 'NOT_ENROLLED':
        with open('log.txt','a') as myFile:
            myFile.write("Not Enrolled in CW Status for: " + patientId + '\n')
        return False
    else:
        with open('log.txt','a') as myFile:
            myFile.write("Patient already enrolled in CW:  " + patientId + '\n')
        return True
            
#############
#hgPatientPull
# does the p360 query for BOTH networks
#
def hgPatientPull(patientId):
    with open('log.txt','a') as myFile:
        myFile.write("Querying CW & CQ for:  " + patientId + '\n')
    url = baseUrl+"/fhir/DocumentReference/$p360-search?patient="+patientId

    payload={}
    headers = {
        'Authorization': 'Bearer ' + bearerToken,
        'Prefer': 'respond-async'
    }

    response = requests.request("GET", url, headers=headers, data=payload)

    if response.status_code == 202 or response.status_code == 201:
        with open('log.txt','a') as myFile:
            myFile.write("P360 Query accepted for  " + patientId + '\n')
        #grab the location from the response headers to use in the retrieval
        resultLocation = response.headers['location']
        #go get those results
        results = retrieveResults(resultLocation, patientId)  
        if results == False:
            return
        else:
            importResults(results,patientId)  
    else:
        with open('log.txt','a') as myFile:
            myFile.write("P360 Query accepted for  " + patientId + '\n')
        return False

########
# retrieveResults
# grap async results from P360 search
def retrieveResults(resultLocation, patientId):
    with open('log.txt','a') as myFile:
        myFile.write("Attempting to retrieve P360 results from patient: " + patientId + "from location " + resultLocation + '\n')
    
    success = False
    
    while success == False:
        url = baseUrl+resultLocation

        payload={}
        headers = {
            'Authorization': 'Bearer '+ bearerToken,
        }

        response = requests.request("GET", url, headers=headers, data=payload)
        if response.status_code == 202:
            with open('log.txt','a') as myFile:
                myFile.write("Results Still Not Ready For patient: " + patientId + " from location " + resultLocation + '\n')
            time.sleep(5)
        elif response.status_code == 454:
            with open('log.txt','a') as myFile:
                myFile.write("Error Getting Results For patient: " + patientId + " from location " + resultLocation + '\n')
            return False
        elif response.status_code == 200:
            resDict = json.loads(response.text)
            total = resDict['total']

            with open('log.txt','a') as myFile:
                myFile.write("Successful Retrieval of " + str(total) + " Results For patient: " + patientId + " from location " + resultLocation + '\n')
            result = response.text
            return result


#########
# importResults
# try to import all of the returned results into Health Gorilla Tenant
#
def importResults(results,patientId):
    resDict = json.loads(results)
    if resDict['total'] != 0:
        with open('log.txt','a') as myFile:
            myFile.write("Importing results for patient: " + patientId + '\n')
        del resDict['meta']
        del resDict['total']
        del resDict['link']
        resDict['type'] = 'batch'
        url = baseUrl+"/fhir/Bundle"

        payload = json.dumps(resDict)
        
        headers = {
            'Authorization': 'Bearer ' + bearerToken,
            'Content-Type': 'application/json',
            'Prefer': 'respond-async',
        }

        response = requests.request("POST", url, headers=headers, data=payload)

        
        if response.status_code == 202:
            with open('log.txt','a') as myFile:
                myFile.write("Imported results accepted into HG for patient: " + patientId + '\n')
        else:
            with open('log.txt','a') as myFile:
                myFile.write("Imported results not accepted into HG for patient: " + patientId + '\n')
    else:
        return
####Program start######

getBearerToken() #get the access token
patientList = csvToDictionary() #read the CSV into a dictionary

#loop through each patient in the patient list and take them through the P360 process
for value in patientList:
    #search for patient
    patientId = searchForPatient(patientList[value])
    #if patient doesn't exist
    if patientId == False:
        patientId = createPatient(patientList[value])
    #if patient has multiple matches
    elif patientId == "multiple":
        continue
    
    #now we have patientId, let's work some HG magic!
    
    #search CW for possible matches. If 0 are returned, do a lookup to see if they are enrolled. 
    # If not enrolled, enroll the patient. 
    # If a match is found, enroll the patient in CW by linking the HG and CW ID's
    cwId = cwSearch(patientId)
    if cwId == False:
        #check to see if the patient is already enrolled in CW
        cwLookUpCheck = cwLookUp(patientId)
        if cwLookUpCheck == False:
            ##if not enroll, enroll
            cwEnroll(patientId)
    else:
        ##link the two Id's together
        cwEnroll(patientId, cwId)

    ##Do P360 pull
    results = hgPatientPull(patientId)
    ##if results come back import into HG. 

